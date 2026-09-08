package io.github.badim1235.trackdrop.activity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.badim1235.trackdrop.TestcontainersConfiguration;
import io.github.badim1235.trackdrop.identity.AccountStatus;
import io.github.badim1235.trackdrop.identity.TrackDropPrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
class ActivityControllerTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void returnsPrivateRecommendationHistoryAndReactionSummary() throws Exception {
		UUID owner = createUser("activity-owner");
		UUID other = createUser("activity-other");
		UUID voterOne = createUser("activity-voter-one");
		UUID voterTwo = createUser("activity-voter-two");
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		UUID rock = jdbcClient.sql("SELECT id FROM genres WHERE code = 'rock'")
			.query(UUID.class)
			.single();

		UUID highestTrack = createTrack("가장 사랑받은 곡", rock);
		UUID highestRecommendation = createRecommendation(
			owner, highestTrack, rock, today.minusDays(5), "첫 번째 한줄평");
		createVotes(highestTrack, today.minusDays(5), List.of(owner, voterOne, voterTwo));

		UUID repeatedTrack = createTrack("다시 추천한 곡", rock);
		createRecommendation(other, repeatedTrack, rock, today.minusDays(7), "최초 추천자의 한줄평");
		createRecommendation(owner, repeatedTrack, rock, today.minusDays(3), "다시 발견한 한줄평");
		createVotes(repeatedTrack, today.minusDays(3), List.of(owner, voterOne));

		UUID newestTrack = createTrack("가장 최근 곡", rock);
		createRecommendation(owner, newestTrack, rock, today.minusDays(1), "최근 한줄평");
		createVotes(newestTrack, today.minusDays(1), List.of(owner));

		TrackDropPrincipal principal = new TrackDropPrincipal(
			owner,
			"activity-owner@example.com",
			AccountStatus.ACTIVE);

		mockMvc.perform(get("/api/v1/me/activity").with(user(principal)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.summary.recommendationCount").value(3))
			.andExpect(jsonPath("$.summary.firstPickCount").value(2))
			.andExpect(jsonPath("$.summary.receivedVoteCount").value(3))
			.andExpect(jsonPath("$.summary.highestVoted.trackId").value(highestTrack.toString()))
			.andExpect(jsonPath("$.summary.highestVoted.voteCount").value(2))
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.items[0].trackId").value(newestTrack.toString()))
			.andExpect(jsonPath("$.items[0].voteCount").value(0))
			.andExpect(jsonPath("$.items[0].firstPick").value(true))
			.andExpect(jsonPath("$.items[1].trackId").value(repeatedTrack.toString()))
			.andExpect(jsonPath("$.items[1].voteCount").value(1))
			.andExpect(jsonPath("$.items[1].firstPick").value(false))
			.andExpect(jsonPath("$.items[2].recommendationId").value(highestRecommendation.toString()))
			.andExpect(jsonPath("$.items[2].voteCount").value(2))
			.andExpect(jsonPath("$.page.size").value(20))
			.andExpect(jsonPath("$.page.hasMore").value(false));
	}

	@Test
	void rejectsAnInvalidActivityCursor() throws Exception {
		UUID owner = createUser("activity-cursor");
		TrackDropPrincipal principal = new TrackDropPrincipal(
			owner,
			"activity-cursor@example.com",
			AccountStatus.ACTIVE);

		mockMvc.perform(get("/api/v1/me/activity")
				.param("cursor", "not-a-cursor")
				.with(user(principal)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_CURSOR"));
	}

	@Test
	void omitsHighestVotedWhenOnlyTheAutomaticRecommendationVoteExists() throws Exception {
		UUID owner = createUser("activity-no-received-votes");
		UUID rock = jdbcClient.sql("SELECT id FROM genres WHERE code = 'rock'")
			.query(UUID.class)
			.single();
		LocalDate recommendedOn = LocalDate.now(ZoneOffset.UTC).minusDays(1);
		UUID track = createTrack("아직 반응 없는 곡", rock);
		createRecommendation(owner, track, rock, recommendedOn, "첫 추천입니다.");
		createVotes(track, recommendedOn, List.of(owner));
		TrackDropPrincipal principal = new TrackDropPrincipal(
			owner,
			"activity-no-received-votes@example.com",
			AccountStatus.ACTIVE);

		mockMvc.perform(get("/api/v1/me/activity").with(user(principal)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.summary.receivedVoteCount").value(0))
			.andExpect(jsonPath("$.summary.highestVoted").isEmpty())
			.andExpect(jsonPath("$.items[0].voteCount").value(0));
	}

	@Test
	void pagesThroughAllRecommendationActivityWithAnOpaqueCursor() throws Exception {
		UUID owner = createUser("activity-pages");
		UUID rock = jdbcClient.sql("SELECT id FROM genres WHERE code = 'rock'")
			.query(UUID.class)
			.single();
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		for (int index = 0; index < 21; index++) {
			UUID track = createTrack("페이지 곡 " + index, rock);
			createRecommendation(owner, track, rock, today.minusDays(index + 1L), "페이지 한줄평 " + index);
		}
		TrackDropPrincipal principal = new TrackDropPrincipal(
			owner,
			"activity-pages@example.com",
			AccountStatus.ACTIVE);

		String firstPage = mockMvc.perform(get("/api/v1/me/activity").with(user(principal)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.summary.recommendationCount").value(21))
			.andExpect(jsonPath("$.items.length()").value(20))
			.andExpect(jsonPath("$.page.hasMore").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String cursor = JsonPath.read(firstPage, "$.page.nextCursor");

		mockMvc.perform(get("/api/v1/me/activity")
				.param("cursor", cursor)
				.with(user(principal)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.summary.recommendationCount").value(21))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].title").value("페이지 곡 20"))
			.andExpect(jsonPath("$.page.hasMore").value(false))
			.andExpect(jsonPath("$.page.nextCursor").isEmpty());
	}

	private UUID createUser(String name) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10);
		jdbcClient.sql("""
				INSERT INTO users (
					id, email, email_normalized, email_verified_at,
					public_nickname, status, created_at, updated_at
				)
				VALUES (:id, :email, :email, :now, :nickname, 'ACTIVE', :now, :now)
				""")
			.param("id", id)
			.param("email", name + "-" + id.toString().substring(0, 8) + "@example.com")
			.param("nickname", name + id.toString().substring(0, 8))
			.param("now", now)
			.update();
		return id;
	}

	private UUID createTrack(String title, UUID genreId) {
		UUID trackId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).minusDays(8);
		jdbcClient.sql("""
				INSERT INTO tracks (
					id, title, artist_name, album_cover_url, explicit,
					provider_genre_name, created_at, updated_at
				)
				VALUES (:id, :title, 'TrackPick Artist', :cover, FALSE, 'Rock', :now, :now)
				""")
			.param("id", trackId)
			.param("title", title)
			.param("cover", "https://example.com/" + trackId + ".jpg")
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
		return trackId;
	}

	private UUID createRecommendation(
		UUID userId,
		UUID trackId,
		UUID genreId,
		LocalDate recommendedOn,
		String comment
	) {
		UUID recommendationId = UUID.randomUUID();
		OffsetDateTime createdAt = recommendedOn.atTime(12, 0).atOffset(ZoneOffset.UTC);
		jdbcClient.sql("""
				INSERT INTO recommendations (
					id, recommender_user_id, track_id, primary_genre_id,
					comment, comment_visibility, recommended_on, created_at
				)
				VALUES (
					:id, :userId, :trackId, :genreId,
					:comment, 'VISIBLE', :recommendedOn, :createdAt
				)
				""")
			.param("id", recommendationId)
			.param("userId", userId)
			.param("trackId", trackId)
			.param("genreId", genreId)
			.param("comment", comment)
			.param("recommendedOn", recommendedOn)
			.param("createdAt", createdAt)
			.update();
		return recommendationId;
	}

	private void createVotes(UUID trackId, LocalDate votedOn, List<UUID> userIds) {
		OffsetDateTime createdAt = votedOn.atTime(12, 1).atOffset(ZoneOffset.UTC);
		for (UUID userId : userIds) {
			jdbcClient.sql("""
					INSERT INTO votes (id, user_id, track_id, voted_on, created_at)
					VALUES (:id, :userId, :trackId, :votedOn, :createdAt)
					""")
				.param("id", UUID.randomUUID())
				.param("userId", userId)
				.param("trackId", trackId)
				.param("votedOn", votedOn)
				.param("createdAt", createdAt)
				.update();
		}
	}
}
