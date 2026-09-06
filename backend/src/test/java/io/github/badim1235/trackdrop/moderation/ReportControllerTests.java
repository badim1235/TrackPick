package io.github.badim1235.trackdrop.moderation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.badim1235.trackdrop.TestcontainersConfiguration;
import io.github.badim1235.trackdrop.identity.AccountStatus;
import io.github.badim1235.trackdrop.identity.TrackDropPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
	"trackdrop.features.reports-enabled=true",
	"trackdrop.moderation.report-limit=2"
})
@AutoConfigureMockMvc
class ReportControllerTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@BeforeEach
	void clearReportData() {
		jdbcClient.sql("DELETE FROM content_reports").update();
		jdbcClient.sql("DELETE FROM daily_recommendation_quotas").update();
		jdbcClient.sql("DELETE FROM votes").update();
		jdbcClient.sql("DELETE FROM recommendations").update();
		jdbcClient.sql("DELETE FROM track_genres").update();
		jdbcClient.sql("DELETE FROM track_provider_refs").update();
		jdbcClient.sql("DELETE FROM tracks").update();
		jdbcClient.sql("DELETE FROM users").update();
	}

	@Test
	void createsAReportWithoutHidingTheComment() throws Exception {
		UUID ownerId = insertUser("owner");
		UUID reporterId = insertUser("reporter");
		UUID recommendationId = insertRecommendation(ownerId);

		mockMvc.perform(reportRequest(reporterId, recommendationId))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.report.status").value("PENDING"))
			.andExpect(jsonPath("$.report.id").isNotEmpty());

		String visibility = jdbcClient.sql("""
				SELECT comment_visibility FROM recommendations WHERE id = :id
				""")
			.param("id", recommendationId)
			.query(String.class)
			.single();
		org.assertj.core.api.Assertions.assertThat(visibility).isEqualTo("VISIBLE");
	}

	@Test
	void rejectsSelfReports() throws Exception {
		UUID ownerId = insertUser("owner");
		UUID recommendationId = insertRecommendation(ownerId);

		mockMvc.perform(reportRequest(ownerId, recommendationId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("SELF_REPORT_NOT_ALLOWED"));
	}

	@Test
	void rejectsDuplicateReports() throws Exception {
		UUID ownerId = insertUser("owner");
		UUID reporterId = insertUser("reporter");
		UUID recommendationId = insertRecommendation(ownerId);

		mockMvc.perform(reportRequest(reporterId, recommendationId))
			.andExpect(status().isCreated());
		mockMvc.perform(reportRequest(reporterId, recommendationId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("ALREADY_REPORTED"));
	}

	@Test
	void flagsTheReportedUserWhenPendingReportsReachTheLimit() throws Exception {
		UUID ownerId = insertUser("owner");
		UUID firstReporterId = insertUser("reporter-one");
		UUID secondReporterId = insertUser("reporter-two");
		UUID firstRecommendationId = insertRecommendation(ownerId);
		UUID secondRecommendationId = insertRecommendation(ownerId);

		mockMvc.perform(reportRequest(firstReporterId, firstRecommendationId))
			.andExpect(status().isCreated());
		mockMvc.perform(reportRequest(secondReporterId, secondRecommendationId))
			.andExpect(status().isCreated());

		String activity = jdbcClient.sql("SELECT activity FROM users WHERE id = :id")
			.param("id", ownerId)
			.query(String.class)
			.single();
		org.assertj.core.api.Assertions.assertThat(activity).isEqualTo("FLAGGED");
	}

	@Test
	void rejectsReportsAgainstBannedUsers() throws Exception {
		UUID ownerId = insertUser("owner");
		UUID reporterId = insertUser("reporter");
		UUID recommendationId = insertRecommendation(ownerId);
		jdbcClient.sql("UPDATE users SET activity = 'BAN', status = 'SUSPENDED' WHERE id = :id")
			.param("id", ownerId)
			.update();

		mockMvc.perform(reportRequest(reporterId, recommendationId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("REPORTED_USER_BANNED"));
	}

	@Test
	void rejectsReportsAgainstMissingRecommendations() throws Exception {
		UUID reporterId = insertUser("reporter");

		mockMvc.perform(reportRequest(reporterId, UUID.randomUUID()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RECOMMENDATION_NOT_FOUND"));
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder reportRequest(
		UUID reporterId,
		UUID recommendationId
	) {
		TrackDropPrincipal principal = new TrackDropPrincipal(
			reporterId,
			"reporter@example.com",
			AccountStatus.ACTIVE);
		return post("/api/v1/recommendations/{recommendationId}/reports", recommendationId)
			.with(user(principal))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"reasonCode":"ABUSIVE_LANGUAGE","details":"  신고 설명  "}
				""");
	}

	private UUID insertUser(String prefix) {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2);
		jdbcClient.sql("""
				INSERT INTO users (
					id, email, email_normalized, email_verified_at,
					public_nickname, status, created_at, updated_at
				)
				VALUES (:id, :email, :email, :now, :nickname, 'ACTIVE', :now, :now)
				""")
			.param("id", userId)
			.param("email", prefix + "-" + userId + "@example.com")
			.param("nickname", "신고테스트" + userId.toString().substring(0, 8))
			.param("now", now)
			.update();
		return userId;
	}

	private UUID insertRecommendation(UUID ownerId) {
		UUID trackId = UUID.randomUUID();
		UUID recommendationId = UUID.randomUUID();
		UUID genreId = jdbcClient.sql("SELECT id FROM genres WHERE code = 'rock'")
			.query(UUID.class)
			.single();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
		jdbcClient.sql("""
				INSERT INTO tracks (
					id, title, artist_name, explicit, created_at, updated_at
				)
				VALUES (:id, 'Report Track', 'Report Artist', FALSE, :now, :now)
				""")
			.param("id", trackId)
			.param("now", now)
			.update();
		jdbcClient.sql("""
				INSERT INTO track_genres (track_id, genre_id, source, created_at)
				VALUES (:trackId, :genreId, 'USER_SELECTED', :now)
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
				VALUES (
					:id, :ownerId, :trackId, :genreId,
					'신고 테스트 한줄평', 'VISIBLE', :now
				)
				""")
			.param("id", recommendationId)
			.param("ownerId", ownerId)
			.param("trackId", trackId)
			.param("genreId", genreId)
			.param("now", now)
			.update();
		return recommendationId;
	}
}
