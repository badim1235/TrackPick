package io.github.badim1235.trackdrop.moderation;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.badim1235.trackdrop.TestcontainersConfiguration;
import io.github.badim1235.trackdrop.identity.AccountStatus;
import io.github.badim1235.trackdrop.identity.AuthSessionService;
import io.github.badim1235.trackdrop.identity.TrackDropPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
	"trackdrop.features.reports-enabled=true",
	"trackdrop.security.admin-emails=admin@example.com"
})
@AutoConfigureMockMvc
@Transactional
class AdminReportControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@MockitoBean
	private AuthSessionService authSessionService;

	@Test
	void hidesAdminEndpointsFromRegularUsers() throws Exception {
		UUID userId = insertUser("regular@example.com", "일반사용자", "NORMAL", "ACTIVE");

		mockMvc.perform(get("/api/v1/admin/reports/users").with(user(principal(userId, "regular@example.com"))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	@Test
	void searchesFlaggedUsersAndDismissesPendingReports() throws Exception {
		UUID adminId = insertUser("admin@example.com", "관리자", "NORMAL", "ACTIVE");
		UUID targetId = insertUser("target@example.com", "검토대상", "FLAGGED", "ACTIVE");
		UUID reporterId = insertUser("reporter@example.com", "신고자", "NORMAL", "ACTIVE");
		UUID recommendationId = insertRecommendation(targetId, "확인이 필요한 한줄평");
		insertReport(reporterId, targetId, recommendationId);

		mockMvc.perform(get("/api/v1/admin/reports/users")
				.param("query", "검토")
				.with(user(principal(adminId, "admin@example.com"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(targetId.toString()))
			.andExpect(jsonPath("$.items[0].pendingReportCount").value(1));

		mockMvc.perform(get("/api/v1/admin/reports/users/{userId}", targetId)
				.with(user(principal(adminId, "admin@example.com"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.publicNickname").value("검토대상"))
			.andExpect(jsonPath("$.reports[0].recommendation.comment").value("확인이 필요한 한줄평"));

		mockMvc.perform(patch("/api/v1/admin/reports/users/{userId}", targetId)
				.with(user(principal(adminId, "admin@example.com")))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"action\":\"DISMISS\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.activity").value("NORMAL"))
			.andExpect(jsonPath("$.resolvedReportCount").value(1));

		org.assertj.core.api.Assertions.assertThat(userActivity(targetId)).isEqualTo("NORMAL");
		org.assertj.core.api.Assertions.assertThat(reportStatus(recommendationId)).isEqualTo("DISMISSED");
	}

	@Test
	void bansAFlaggedUserHidesCommentsAndRevokesSessions() throws Exception {
		UUID adminId = insertUser("admin@example.com", "관리자", "NORMAL", "ACTIVE");
		UUID targetId = insertUser("blocked@example.com", "차단대상", "FLAGGED", "ACTIVE");
		UUID reporterId = insertUser("reporter@example.com", "신고자", "NORMAL", "ACTIVE");
		UUID recommendationId = insertRecommendation(targetId, "차단할 한줄평");
		insertReport(reporterId, targetId, recommendationId);

		mockMvc.perform(patch("/api/v1/admin/reports/users/{userId}", targetId)
				.with(user(principal(adminId, "admin@example.com")))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"action\":\"BAN\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.activity").value("BAN"))
			.andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));

		String state = jdbcClient.sql("SELECT activity || ':' || status FROM users WHERE id = :id")
			.param("id", targetId)
			.query(String.class)
			.single();
		String visibility = jdbcClient.sql(
				"SELECT comment_visibility FROM recommendations WHERE id = :id")
			.param("id", recommendationId)
			.query(String.class)
			.single();
		org.assertj.core.api.Assertions.assertThat(state).isEqualTo("BAN:SUSPENDED");
		org.assertj.core.api.Assertions.assertThat(visibility).isEqualTo("HIDDEN");
		verify(authSessionService).revokeAll("blocked@example.com");
	}

	private TrackDropPrincipal principal(UUID userId, String email) {
		return new TrackDropPrincipal(userId, email, AccountStatus.ACTIVE);
	}

	private UUID insertUser(String email, String nickname, String activity, String status) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
		jdbcClient.sql("""
				INSERT INTO users (
					id, email, email_normalized, email_verified_at, public_nickname,
					status, activity, created_at, updated_at
				)
				VALUES (:id, :email, :email, :now, :nickname, :status, :activity, :now, :now)
				""")
			.param("id", id)
			.param("email", email)
			.param("nickname", nickname)
			.param("status", status)
			.param("activity", activity)
			.param("now", now)
			.update();
		return id;
	}

	private UUID insertRecommendation(UUID ownerId, String comment) {
		UUID trackId = UUID.randomUUID();
		UUID recommendationId = UUID.randomUUID();
		UUID genreId = jdbcClient.sql("SELECT id FROM genres WHERE code = 'rock'")
			.query(UUID.class)
			.single();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2);
		jdbcClient.sql("""
				INSERT INTO tracks (id, title, artist_name, explicit, created_at, updated_at)
				VALUES (:id, 'Admin Report Track', 'Admin Artist', FALSE, :now, :now)
				""")
			.param("id", trackId)
			.param("now", now)
			.update();
		jdbcClient.sql("""
				INSERT INTO track_genres (track_id, genre_id, source, created_at)
				VALUES (:trackId, :genreId, 'PROVIDER', :now)
				""")
			.param("trackId", trackId)
			.param("genreId", genreId)
			.param("now", now)
			.update();
		jdbcClient.sql("""
				INSERT INTO recommendations (
					id, recommender_user_id, track_id, primary_genre_id,
					comment, comment_visibility, created_at
				)
				VALUES (:id, :ownerId, :trackId, :genreId, :comment, 'VISIBLE', :now)
				""")
			.param("id", recommendationId)
			.param("ownerId", ownerId)
			.param("trackId", trackId)
			.param("genreId", genreId)
			.param("comment", comment)
			.param("now", now)
			.update();
		return recommendationId;
	}

	private void insertReport(UUID reporterId, UUID targetId, UUID recommendationId) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
		jdbcClient.sql("""
				INSERT INTO content_reports (
					id, reporter_user_id, reported_user_id, recommendation_id,
					reason_code, details, status, created_at
				)
				VALUES (:id, :reporterId, :targetId, :recommendationId,
					'ABUSIVE_LANGUAGE', '관리자 확인 필요', 'PENDING', :now)
				""")
			.param("id", UUID.randomUUID())
			.param("reporterId", reporterId)
			.param("targetId", targetId)
			.param("recommendationId", recommendationId)
			.param("now", now)
			.update();
	}

	private String userActivity(UUID userId) {
		return jdbcClient.sql("SELECT activity FROM users WHERE id = :id")
			.param("id", userId)
			.query(String.class)
			.single();
	}

	private String reportStatus(UUID recommendationId) {
		return jdbcClient.sql("SELECT status FROM content_reports WHERE recommendation_id = :id")
			.param("id", recommendationId)
			.query(String.class)
			.single();
	}
}
