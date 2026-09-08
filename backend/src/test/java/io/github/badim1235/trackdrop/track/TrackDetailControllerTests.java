package io.github.badim1235.trackdrop.track;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.badim1235.trackdrop.TestcontainersConfiguration;
import io.github.badim1235.trackdrop.identity.AccountStatus;
import io.github.badim1235.trackdrop.identity.TrackDropPrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrackDetailControllerTests {
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void returnsPublicTrackMetadataRecommendationAndTodayRanks() throws Exception {
		List<UUID> users = createUsers(3);
		UUID rock = genreId("rock");
		UUID pop = genreId("pop");
		UUID target = insertTrack("Beta", rock, users.getFirst(), users.subList(0, 2));
		insertTrack("alpha", rock, users.getFirst(), users.subList(0, 1));
		insertTrack("Zulu", pop, users.getFirst(), users.subList(0, 1));

		mockMvc.perform(get("/api/v1/tracks/{trackId}", target))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.track.id").value(target.toString()))
			.andExpect(jsonPath("$.track.title").value("Beta"))
			.andExpect(jsonPath("$.track.primaryGenre.code").value("rock"))
			.andExpect(jsonPath("$.track.genres[0].code").value("rock"))
			.andExpect(jsonPath("$.track.recommendation.comment").value("상세 화면에서 듣고 추천해 보세요."))
			.andExpect(jsonPath("$.track.preview.available").value(true))
			.andExpect(jsonPath("$.track.providerReferences[0].provider").value("APPLE_MUSIC"))
			.andExpect(jsonPath("$.today.voteCount").value(2))
			.andExpect(jsonPath("$.today.overallRank").value(1))
			.andExpect(jsonPath("$.today.genreRank").value(1))
			.andExpect(jsonPath("$.actions.canVote").value(false))
			.andExpect(jsonPath("$.actions.canReport").value(false))
			.andExpect(jsonPath("$.actions.hasReported").value(false))
			.andExpect(jsonPath("$.actions.reason").value("UNAUTHENTICATED"))
			.andExpect(jsonPath("$.quota").doesNotExist());
	}

	@Test
	void includesViewerVoteStateAndQuotaForAnAuthenticatedUser() throws Exception {
		List<UUID> users = createUsers(2);
		UUID trackId = insertTrack("Viewer Track", genreId("rock"), users.getFirst(), List.of(users.getFirst()));
		UUID viewerId = users.get(1);
		TrackDropPrincipal principal = new TrackDropPrincipal(
			viewerId,
			"viewer@example.com",
			AccountStatus.ACTIVE);

		mockMvc.perform(get("/api/v1/tracks/{trackId}", trackId).with(user(principal)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.track.viewer.hasVotedToday").value(false))
			.andExpect(jsonPath("$.quota.limit").value(4))
			.andExpect(jsonPath("$.quota.remaining").value(4))
			.andExpect(jsonPath("$.actions.canVote").value(true))
			.andExpect(jsonPath("$.actions.canReport").value(true))
			.andExpect(jsonPath("$.actions.hasReported").value(false))
			.andExpect(jsonPath("$.actions.reason").doesNotExist());
	}

	@Test
	void showsTheCooldownDateWhenTheTrackIsNotInTodaysChart() throws Exception {
		List<UUID> users = createUsers(2);
		UUID trackId = insertTrack("Cooling Detail", genreId("rock"), users.getFirst(), List.of());
		LocalDate today = LocalDate.now(SERVICE_ZONE);
		jdbcClient.sql("UPDATE recommendations SET recommended_on = :date WHERE track_id = :trackId")
			.param("date", today.minusDays(1))
			.param("trackId", trackId)
			.update();
		TrackDropPrincipal principal = new TrackDropPrincipal(
			users.get(1), "viewer@example.com", AccountStatus.ACTIVE);

		mockMvc.perform(get("/api/v1/tracks/{trackId}", trackId).with(user(principal)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.actions.canVote").value(false))
			.andExpect(jsonPath("$.actions.canRecommend").value(false))
			.andExpect(jsonPath("$.actions.reason").value("RECOMMENDATION_COOLDOWN"))
			.andExpect(jsonPath("$.actions.recommendationAvailableOn")
				.value(today.plusDays(2).toString()));
	}

	@Test
	void directsAnEligibleOldTrackToANewRecommendationCycle() throws Exception {
		List<UUID> users = createUsers(2);
		UUID trackId = insertTrack("Eligible Detail", genreId("rock"), users.getFirst(), List.of());
		LocalDate today = LocalDate.now(SERVICE_ZONE);
		jdbcClient.sql("UPDATE recommendations SET recommended_on = :date WHERE track_id = :trackId")
			.param("date", today.minusDays(3))
			.param("trackId", trackId)
			.update();
		TrackDropPrincipal principal = new TrackDropPrincipal(
			users.get(1), "viewer@example.com", AccountStatus.ACTIVE);

		mockMvc.perform(get("/api/v1/tracks/{trackId}", trackId).with(user(principal)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.actions.canVote").value(false))
			.andExpect(jsonPath("$.actions.canRecommend").value(true))
			.andExpect(jsonPath("$.actions.reason").doesNotExist());
	}

	@Test
	void keepsTheFirstPickAndAddsOnlyTheLatestRecommendationComment() throws Exception {
		List<UUID> users = createUsers(3);
		UUID genreId = genreId("rock");
		UUID trackId = insertTrack("Repeated Detail", genreId, users.getFirst(), List.of());
		LocalDate today = LocalDate.now(SERVICE_ZONE);
		jdbcClient.sql("UPDATE recommendations SET recommended_on = :date WHERE track_id = :trackId")
			.param("date", today.minusDays(4))
			.param("trackId", trackId)
			.update();
		UUID latestRecommendationId = UUID.randomUUID();
		OffsetDateTime latestCreatedAt = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcClient.sql("""
				INSERT INTO recommendations (
					id, recommender_user_id, track_id, primary_genre_id,
					comment, comment_visibility, recommended_on, created_at
				)
				VALUES (
					:id, :userId, :trackId, :genreId,
					'가장 최근에 작성한 한줄평', 'VISIBLE', :today, :createdAt
				)
				""")
			.param("id", latestRecommendationId)
			.param("userId", users.get(1))
			.param("trackId", trackId)
			.param("genreId", genreId)
			.param("today", today)
			.param("createdAt", latestCreatedAt)
			.update();
		TrackDropPrincipal principal = new TrackDropPrincipal(
			users.get(2), "viewer@example.com", AccountStatus.ACTIVE);

		mockMvc.perform(get("/api/v1/tracks/{trackId}", trackId).with(user(principal)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.track.recommendation.comment")
				.value("상세 화면에서 듣고 추천해 보세요."))
			.andExpect(jsonPath("$.track.latestRecommendation.id")
				.value(latestRecommendationId.toString()))
			.andExpect(jsonPath("$.track.latestRecommendation.comment")
				.value("가장 최근에 작성한 한줄평"))
			.andExpect(jsonPath("$.track.latestRecommendation.canReport").value(true))
			.andExpect(jsonPath("$.track.latestRecommendation.hasReported").value(false));
	}

	@Test
	void returnsTrackNotFoundForAnUnknownId() throws Exception {
		mockMvc.perform(get("/api/v1/tracks/{trackId}", UUID.randomUUID()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("TRACK_NOT_FOUND"));
	}

	private List<UUID> createUsers(int count) {
		List<UUID> users = new ArrayList<>();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10);
		for (int index = 0; index < count; index++) {
			UUID id = UUID.randomUUID();
			String suffix = id.toString().substring(0, 8);
			jdbcClient.sql("""
					INSERT INTO users (
						id, email, email_normalized, email_verified_at,
						public_nickname, status, created_at, updated_at
					)
					VALUES (
						:id, :email, :email, :now,
						:nickname, 'ACTIVE', :now, :now
					)
					""")
				.param("id", id)
				.param("email", "detail-" + suffix + "@example.com")
				.param("nickname", "상세테스트" + suffix)
				.param("now", now)
				.update();
			users.add(id);
		}
		return users;
	}

	private UUID genreId(String code) {
		return jdbcClient.sql("SELECT id FROM genres WHERE code = :code")
			.param("code", code)
			.query(UUID.class)
			.single();
	}

	private UUID insertTrack(
		String title,
		UUID genreId,
		UUID recommenderId,
		List<UUID> voterIds
	) {
		UUID trackId = UUID.randomUUID();
		OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
		jdbcClient.sql("""
				INSERT INTO tracks (
					id, title, artist_name, album_name, album_cover_url,
					release_year, isrc, explicit, provider_genre_name, created_at, updated_at
				)
				VALUES (
					:id, :title, 'TrackPick Artist', 'Detail Album', :cover,
					2026, :isrc, FALSE, 'Rock', :createdAt, :createdAt
				)
				""")
			.param("id", trackId)
			.param("title", title)
			.param("cover", "https://example.com/cover/" + trackId + ".jpg")
			.param("isrc", "KRABC2600001")
			.param("createdAt", createdAt)
			.update();
		jdbcClient.sql("""
				INSERT INTO track_provider_refs (
					id, track_id, provider, external_track_id,
					external_url, preview_url, metadata_refreshed_at
				)
				VALUES (
					:id, :trackId, 'APPLE_MUSIC', :externalTrackId,
					:externalUrl, :previewUrl, :createdAt
				)
				""")
			.param("id", UUID.randomUUID())
			.param("trackId", trackId)
			.param("externalTrackId", trackId.toString())
			.param("externalUrl", "https://music.apple.com/kr/song/" + trackId)
			.param("previewUrl", "https://example.com/preview/" + trackId + ".m4a")
			.param("createdAt", createdAt)
			.update();
		jdbcClient.sql("""
				INSERT INTO track_genres (track_id, genre_id, source, created_at)
				VALUES (:trackId, :genreId, 'USER_SELECTED', :createdAt)
				""")
			.param("trackId", trackId)
			.param("genreId", genreId)
			.param("createdAt", createdAt)
			.update();
		jdbcClient.sql("""
				INSERT INTO recommendations (
					id, recommender_user_id, track_id, primary_genre_id,
					comment, comment_visibility, created_at
				)
				VALUES (
					:id, :userId, :trackId, :genreId,
					'상세 화면에서 듣고 추천해 보세요.', 'VISIBLE', :createdAt
				)
				""")
			.param("id", UUID.randomUUID())
			.param("userId", recommenderId)
			.param("trackId", trackId)
			.param("genreId", genreId)
			.param("createdAt", createdAt)
			.update();

		LocalDate today = LocalDate.now(SERVICE_ZONE);
		for (UUID voterId : voterIds) {
			jdbcClient.sql("""
					INSERT INTO votes (id, user_id, track_id, voted_on, created_at)
					VALUES (:id, :userId, :trackId, :today, :createdAt)
					""")
				.param("id", UUID.randomUUID())
				.param("userId", voterId)
				.param("trackId", trackId)
				.param("today", today)
				.param("createdAt", createdAt)
				.update();
		}
		return trackId;
	}
}
