package io.github.badim1235.trackdrop.home;

import io.github.badim1235.trackdrop.catalog.GenreResponse.Genre;
import io.github.badim1235.trackdrop.catalog.MusicProvider;
import io.github.badim1235.trackdrop.home.HomeResponse.ExternalLink;
import io.github.badim1235.trackdrop.home.HomeResponse.Preview;
import io.github.badim1235.trackdrop.home.HomeResponse.Recommendation;
import io.github.badim1235.trackdrop.home.HomeResponse.Section;
import io.github.badim1235.trackdrop.home.HomeResponse.TrackCard;
import io.github.badim1235.trackdrop.home.HomeResponse.Viewer;
import io.github.badim1235.trackdrop.home.RecentTracksResponse.Page;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaService;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaSnapshot;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class HomeService {
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
	private static final int HOME_SECTION_SIZE = 4;
	private static final int RECENT_PAGE_SIZE = 20;
	private static final UUID EMPTY_UUID = new UUID(0, 0);
	private static final String TRACK_COLUMNS = """
		track.id,
		track.title,
		track.artist_name,
		track.album_name,
		track.album_cover_url,
		track.release_year,
		track.explicit,
		genre.id AS genre_id,
		genre.code AS genre_code,
		genre.display_name AS genre_display_name,
		genre.sort_order AS genre_sort_order,
		recommendation.id AS recommendation_id,
		CASE WHEN recommendation.comment_visibility = 'VISIBLE' THEN recommendation.comment END AS comment,
		CASE WHEN recommendation.comment_visibility = 'VISIBLE' THEN recommender.public_nickname END AS recommender_nickname,
		recommendation.created_at AS recommendation_created_at,
		provider_ref.preview_url,
		provider_ref.external_url
		""";
	private static final String TRENDING_SQL = """
		WITH vote_counts AS (
			SELECT vote.track_id, COUNT(vote.id)::INTEGER AS today_vote_count
			FROM votes vote
			WHERE vote.voted_on = :today
			  AND vote.created_at <= :asOf
			GROUP BY vote.track_id
		)
		SELECT
			%s,
			vote_counts.today_vote_count,
			EXISTS (
				SELECT 1
				FROM votes viewer_vote
				WHERE viewer_vote.user_id = :viewerId
				  AND viewer_vote.track_id = track.id
				  AND viewer_vote.voted_on = :today
			) AS has_voted_today
		FROM vote_counts
		JOIN tracks track ON track.id = vote_counts.track_id
		JOIN LATERAL (
			SELECT latest.id, latest.recommender_user_id, latest.primary_genre_id,
				latest.comment, latest.comment_visibility, latest.created_at
			FROM recommendations latest
			WHERE latest.track_id = track.id
			  AND latest.recommended_on <= :today
			ORDER BY latest.recommended_on DESC, latest.created_at DESC, latest.id DESC
			LIMIT 1
		) recommendation ON TRUE
		JOIN users recommender ON recommender.id = recommendation.recommender_user_id
		JOIN genres genre ON genre.id = recommendation.primary_genre_id
		JOIN track_provider_refs provider_ref
		  ON provider_ref.track_id = track.id AND provider_ref.provider = 'APPLE_MUSIC'
		ORDER BY
			vote_counts.today_vote_count DESC,
			track.title COLLATE trackdrop_nocase ASC,
			track.artist_name COLLATE trackdrop_nocase ASC,
			track.id ASC
		LIMIT :limit
		""".formatted(TRACK_COLUMNS);
	private static final String RECENT_SQL = """
		SELECT
			%s,
			(
				SELECT COUNT(today_vote.id)::INTEGER
				FROM votes today_vote
				WHERE today_vote.track_id = track.id
				  AND today_vote.voted_on = :today
				  AND today_vote.created_at <= :asOf
			) AS today_vote_count,
			EXISTS (
				SELECT 1
				FROM votes viewer_vote
				WHERE viewer_vote.user_id = :viewerId
				  AND viewer_vote.track_id = track.id
				  AND viewer_vote.voted_on = :today
			) AS has_voted_today
		FROM recommendations recommendation
		JOIN tracks track ON track.id = recommendation.track_id
		JOIN users recommender ON recommender.id = recommendation.recommender_user_id
		JOIN genres genre ON genre.id = recommendation.primary_genre_id
		JOIN track_provider_refs provider_ref
		  ON provider_ref.track_id = track.id AND provider_ref.provider = 'APPLE_MUSIC'
		WHERE recommendation.created_at >= :dayStart
		  AND recommendation.created_at <= :asOf
		  AND (
			:firstPage = TRUE
			OR recommendation.created_at < :afterCreatedAt
			OR (
				recommendation.created_at = :afterCreatedAt
				AND recommendation.id < :afterRecommendationId
			)
		  )
		ORDER BY recommendation.created_at DESC, recommendation.id DESC
		LIMIT :limit
		""".formatted(TRACK_COLUMNS);

	private final JdbcClient jdbcClient;
	private final DailyQuotaService quotaService;
	private final Clock clock;

	HomeService(JdbcClient jdbcClient, DailyQuotaService quotaService, Clock clock) {
		this.jdbcClient = jdbcClient;
		this.quotaService = quotaService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	HomeResponse home(UUID viewerId) {
		Instant asOf = clock.instant();
		LocalDate today = LocalDate.ofInstant(asOf, SERVICE_ZONE);
		UUID selectedViewerId = viewerId == null ? EMPTY_UUID : viewerId;
		List<TrackCard> trending = trending(today, asOf, selectedViewerId, viewerId != null);
		List<TrackCard> recent = recentItems(
			today,
			new CursorState(asOf, asOf, EMPTY_UUID, true),
			selectedViewerId,
			viewerId != null,
			HOME_SECTION_SIZE);
		DailyQuotaSnapshot quota = viewerId == null ? null : quotaService.current(viewerId);

		return new HomeResponse(
			asOf,
			quota,
			new Section("오늘의 추천", trending, "/chart"),
			new Section("최근 등록된 곡", recent, "/recent"));
	}

	@Transactional(readOnly = true)
	RecentTracksResponse recent(String cursor, UUID viewerId) {
		Instant now = clock.instant();
		CursorState state = cursor == null || cursor.isBlank()
			? new CursorState(now, now, EMPTY_UUID, true)
			: decodeCursor(cursor, now);
		LocalDate today = LocalDate.ofInstant(state.asOf(), SERVICE_ZONE);
		UUID selectedViewerId = viewerId == null ? EMPTY_UUID : viewerId;
		List<TrackCard> queriedItems = recentItems(
			today,
			state,
			selectedViewerId,
			viewerId != null,
			RECENT_PAGE_SIZE + 1);
		boolean hasMore = queriedItems.size() > RECENT_PAGE_SIZE;
		List<TrackCard> items = hasMore
			? List.copyOf(queriedItems.subList(0, RECENT_PAGE_SIZE))
			: queriedItems;
		String nextCursor = hasMore ? encodeCursor(state.asOf(), items.getLast()) : null;
		DailyQuotaSnapshot quota = viewerId == null ? null : quotaService.current(viewerId);

		return new RecentTracksResponse(
			state.asOf(),
			items,
			new Page(RECENT_PAGE_SIZE, hasMore, nextCursor),
			quota);
	}

	private List<TrackCard> trending(
		LocalDate today,
		Instant asOf,
		UUID viewerId,
		boolean authenticated
	) {
		return jdbcClient.sql(TRENDING_SQL)
			.param("today", today)
			.param("asOf", OffsetDateTime.ofInstant(asOf, ZoneOffset.UTC))
			.param("viewerId", viewerId)
			.param("limit", HOME_SECTION_SIZE)
			.query((row, rowNumber) -> mapTrack(row, authenticated))
			.list();
	}

	private List<TrackCard> recentItems(
		LocalDate today,
		CursorState state,
		UUID viewerId,
		boolean authenticated,
		int limit
	) {
		return jdbcClient.sql(RECENT_SQL)
			.param("today", today)
			.param("dayStart", today.atStartOfDay(SERVICE_ZONE).toOffsetDateTime())
			.param("asOf", OffsetDateTime.ofInstant(state.asOf(), ZoneOffset.UTC))
			.param("viewerId", viewerId)
			.param("firstPage", state.firstPage())
			.param("afterCreatedAt", OffsetDateTime.ofInstant(state.afterCreatedAt(), ZoneOffset.UTC))
			.param("afterRecommendationId", state.afterRecommendationId())
			.param("limit", limit)
			.query((row, rowNumber) -> mapTrack(row, authenticated))
			.list();
	}

	private static TrackCard mapTrack(ResultSet row, boolean authenticated) throws SQLException {
		String comment = row.getString("comment");
		String previewUrl = row.getString("preview_url");
		String externalUrl = row.getString("external_url");
		return new TrackCard(
			row.getObject("id", UUID.class),
			row.getString("title"),
			row.getString("artist_name"),
			row.getString("album_name"),
			row.getString("album_cover_url"),
			row.getObject("release_year") == null ? null : row.getInt("release_year"),
			row.getBoolean("explicit"),
			new Genre(
				row.getObject("genre_id", UUID.class),
				row.getString("genre_code"),
				row.getString("genre_display_name"),
				row.getInt("genre_sort_order")),
			new Recommendation(
				row.getObject("recommendation_id", UUID.class),
				comment,
				comment != null,
				row.getString("recommender_nickname"),
				row.getObject("recommendation_created_at", OffsetDateTime.class).toInstant()),
			row.getInt("today_vote_count"),
			authenticated ? new Viewer(row.getBoolean("has_voted_today")) : null,
			new Preview(
				previewUrl != null,
				MusicProvider.APPLE_MUSIC,
				"OFFICIAL_30_SECOND_CLIP",
				"PROVIDER_SELECTED",
				previewUrl),
			externalUrl == null
				? List.of()
				: List.of(new ExternalLink(MusicProvider.APPLE_MUSIC, externalUrl)));
	}

	private static String encodeCursor(Instant asOf, TrackCard lastItem) {
		String value = String.join("|",
			asOf.toString(),
			lastItem.recommendation().createdAt().toString(),
			lastItem.recommendation().id().toString());
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static CursorState decodeCursor(String cursor, Instant now) {
		try {
			String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			String[] parts = value.split("\\|", -1);
			if (parts.length != 3) {
				throw new IllegalArgumentException("Cursor part count");
			}
			Instant asOf = Instant.parse(parts[0]);
			Instant afterCreatedAt = Instant.parse(parts[1]);
			UUID afterRecommendationId = UUID.fromString(parts[2]);
			if (asOf.isAfter(now.plusSeconds(1)) || afterCreatedAt.isAfter(asOf)) {
				throw new IllegalArgumentException("Cursor context mismatch");
			}
			return new CursorState(asOf, afterCreatedAt, afterRecommendationId, false);
		} catch (RuntimeException exception) {
			throw HomeFeedException.invalidCursor(exception);
		}
	}

	private record CursorState(
		Instant asOf,
		Instant afterCreatedAt,
		UUID afterRecommendationId,
		boolean firstPage
	) {
	}
}
