package io.github.badim1235.trackdrop.home;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.badim1235.trackdrop.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HomeControllerTests {
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void returnsTrendingAndRecentTracksUsingIndependentSorts() throws Exception {
		List<UUID> users = createUsers(3);
		UUID rock = genreId("rock");
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		insertTrack("인기곡", now.minusMinutes(3), rock, users.getFirst(), users);
		insertTrack("중간곡", now.minusMinutes(2), rock, users.getFirst(), users.subList(0, 2));
		insertTrack("최신곡", now.minusMinutes(1), rock, users.getFirst(), users.subList(0, 1));

		mockMvc.perform(get("/api/v1/home"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.trending.title").value("오늘의 추천"))
			.andExpect(jsonPath("$.trending.viewAllPath").value("/chart"))
			.andExpect(jsonPath("$.trending.items[0].title").value("인기곡"))
			.andExpect(jsonPath("$.trending.items[0].todayVoteCount").value(3))
			.andExpect(jsonPath("$.trending.items[0].preview.available").value(true))
			.andExpect(jsonPath("$.trending.items[0].viewer").doesNotExist())
			.andExpect(jsonPath("$.recent.viewAllPath").value("/recent"))
			.andExpect(jsonPath("$.recent.items[0].title").value("최신곡"))
			.andExpect(jsonPath("$.quota").doesNotExist());
	}

	@Test
	void limitsHomeSectionsToSixItems() throws Exception {
		UUID user = createUsers(1).getFirst();
		UUID rock = genreId("rock");
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		for (int index = 1; index <= 7; index++) {
			insertTrack("Home %02d".formatted(index), now.minusSeconds(index), rock, user, List.of(user));
		}

		mockMvc.perform(get("/api/v1/home"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.trending.items.length()").value(4))
			.andExpect(jsonPath("$.recent.items.length()").value(4));
	}

	@Test
	void paginatesRecentTracksFromAStableCursor() throws Exception {
		UUID user = createUsers(1).getFirst();
		UUID rock = genreId("rock");
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		for (int index = 1; index <= 21; index++) {
			insertTrack("Recent %02d".formatted(index), now.minusSeconds(index), rock, user, List.of(user));
		}

		MvcResult firstPage = mockMvc.perform(get("/api/v1/tracks/recent"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(20))
			.andExpect(jsonPath("$.items[0].title").value("Recent 01"))
			.andExpect(jsonPath("$.page.hasMore").value(true))
			.andReturn();
		String body = firstPage.getResponse().getContentAsString(StandardCharsets.UTF_8);
		Matcher cursorMatch = Pattern.compile("\\\"nextCursor\\\":\\\"([^\\\"]+)\\\"").matcher(body);
		if (!cursorMatch.find()) {
			throw new AssertionError("Expected the first recent page to include a cursor");
		}

		mockMvc.perform(get("/api/v1/tracks/recent").param("cursor", cursorMatch.group(1)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].title").value("Recent 21"))
			.andExpect(jsonPath("$.page.hasMore").value(false));
	}

	@Test
	void onlyReturnsTracksRegisteredOnTheCurrentKoreanDate() throws Exception {
		UUID user = createUsers(1).getFirst();
		UUID rock = genreId("rock");
		OffsetDateTime todayStart = LocalDate.now(SERVICE_ZONE)
			.atStartOfDay(SERVICE_ZONE)
			.toOffsetDateTime();
		insertTrack("어제 등록곡", todayStart.minusSeconds(1), rock, user, List.of(user));
		insertTrack("오늘 등록곡", todayStart, rock, user, List.of(user));

		mockMvc.perform(get("/api/v1/home"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recent.items.length()").value(1))
			.andExpect(jsonPath("$.recent.items[0].title").value("오늘 등록곡"));

		mockMvc.perform(get("/api/v1/tracks/recent"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].title").value("오늘 등록곡"));
	}

	@Test
	void rejectsAnInvalidRecentCursor() throws Exception {
		mockMvc.perform(get("/api/v1/tracks/recent").param("cursor", "not-a-cursor"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_CURSOR"));
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
				.param("email", "home-" + suffix + "@example.com")
				.param("nickname", "홈테스트" + suffix)
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

	private void insertTrack(
		String title,
		OffsetDateTime createdAt,
		UUID genreId,
		UUID recommenderId,
		List<UUID> voterIds
	) {
		UUID trackId = UUID.randomUUID();
		UUID recommendationId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO tracks (
					id, title, artist_name, album_name, album_cover_url,
					release_year, explicit, provider_genre_name, created_at, updated_at
				)
				VALUES (
					:id, :title, 'TrackDrop Artist', 'Test Album', :cover,
					2026, FALSE, 'Rock', :createdAt, :createdAt
				)
				""")
			.param("id", trackId)
			.param("title", title)
			.param("cover", "https://example.com/cover/" + trackId + ".jpg")
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
					'홈에서 발견한 곡이에요.', 'VISIBLE', :createdAt
				)
				""")
			.param("id", recommendationId)
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
	}
}
