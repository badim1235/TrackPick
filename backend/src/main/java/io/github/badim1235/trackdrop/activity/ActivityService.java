package io.github.badim1235.trackdrop.activity;

import io.github.badim1235.trackdrop.activity.ActivityResponse.ActivityItem;
import io.github.badim1235.trackdrop.activity.ActivityResponse.HighestVoted;
import io.github.badim1235.trackdrop.activity.ActivityResponse.Page;
import io.github.badim1235.trackdrop.activity.ActivityResponse.Summary;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ActivityService {
	private static final int PAGE_SIZE = 20;
	private static final UUID EMPTY_UUID = new UUID(0, 0);
	private static final String ACTIVITY_COLUMNS = """
		recommendation.id AS recommendation_id,
		recommendation.track_id,
		track.title,
		track.artist_name,
		track.album_cover_url,
		recommendation.recommended_on,
		recommendation.created_at,
		CASE WHEN recommendation.comment_visibility = 'VISIBLE' THEN recommendation.comment END AS comment,
		(
			SELECT COUNT(vote.id)::INTEGER
			FROM votes vote
			WHERE vote.track_id = recommendation.track_id
			  AND vote.voted_on = recommendation.recommended_on
			  AND vote.user_id <> recommendation.recommender_user_id
			  AND vote.created_at <= :asOf
		) AS vote_count,
		NOT EXISTS (
			SELECT 1
			FROM recommendations earlier
			WHERE earlier.track_id = recommendation.track_id
			  AND (earlier.recommended_on, earlier.created_at, earlier.id)
			      < (recommendation.recommended_on, recommendation.created_at, recommendation.id)
		) AS first_pick
		""";
	private static final String ITEMS_SQL = """
		SELECT %s
		FROM recommendations recommendation
		JOIN tracks track ON track.id = recommendation.track_id
		WHERE recommendation.recommender_user_id = :userId
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
		""".formatted(ACTIVITY_COLUMNS);
	private static final String SUMMARY_SQL = """
		WITH activity AS (
			SELECT
				recommendation.track_id,
				recommendation.recommended_on,
				recommendation.created_at,
				recommendation.id,
				(
					SELECT COUNT(vote.id)::INTEGER
					FROM votes vote
					WHERE vote.track_id = recommendation.track_id
					  AND vote.voted_on = recommendation.recommended_on
					  AND vote.user_id <> recommendation.recommender_user_id
					  AND vote.created_at <= :asOf
				) AS vote_count,
				NOT EXISTS (
					SELECT 1
					FROM recommendations earlier
					WHERE earlier.track_id = recommendation.track_id
					  AND (earlier.recommended_on, earlier.created_at, earlier.id)
					      < (recommendation.recommended_on, recommendation.created_at, recommendation.id)
				) AS first_pick
			FROM recommendations recommendation
			WHERE recommendation.recommender_user_id = :userId
			  AND recommendation.created_at <= :asOf
		)
		SELECT
			COUNT(*)::INTEGER AS recommendation_count,
			COUNT(*) FILTER (WHERE first_pick)::INTEGER AS first_pick_count,
			COALESCE(SUM(vote_count), 0)::INTEGER AS received_vote_count
		FROM activity
		""";
	private static final String HIGHEST_VOTED_SQL = """
		SELECT %s
		FROM recommendations recommendation
		JOIN tracks track ON track.id = recommendation.track_id
		WHERE recommendation.recommender_user_id = :userId
		  AND recommendation.created_at <= :asOf
		  AND EXISTS (
			SELECT 1
			FROM votes received_vote
			WHERE received_vote.track_id = recommendation.track_id
			  AND received_vote.voted_on = recommendation.recommended_on
			  AND received_vote.user_id <> recommendation.recommender_user_id
			  AND received_vote.created_at <= :asOf
		  )
		ORDER BY
			vote_count DESC,
			recommendation.recommended_on DESC,
			track.title COLLATE trackdrop_nocase ASC,
			track.artist_name COLLATE trackdrop_nocase ASC,
			track.id ASC
		LIMIT 1
		""".formatted(ACTIVITY_COLUMNS);

	private final JdbcClient jdbcClient;
	private final Clock clock;

	ActivityService(JdbcClient jdbcClient, Clock clock) {
		this.jdbcClient = jdbcClient;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	ActivityResponse get(UUID userId, String cursor) {
		Instant now = clock.instant();
		CursorState state = cursor == null || cursor.isBlank()
			? new CursorState(now, now, EMPTY_UUID, true)
			: decodeCursor(cursor, now);
		OffsetDateTime asOf = OffsetDateTime.ofInstant(state.asOf(), ZoneOffset.UTC);
		List<ActivityItem> queriedItems = jdbcClient.sql(ITEMS_SQL)
			.param("userId", userId)
			.param("asOf", asOf)
			.param("firstPage", state.firstPage())
			.param("afterCreatedAt", OffsetDateTime.ofInstant(state.afterCreatedAt(), ZoneOffset.UTC))
			.param("afterRecommendationId", state.afterRecommendationId())
			.param("limit", PAGE_SIZE + 1)
			.query(ActivityService::mapActivityItem)
			.list();
		boolean hasMore = queriedItems.size() > PAGE_SIZE;
		List<ActivityItem> items = hasMore
			? List.copyOf(queriedItems.subList(0, PAGE_SIZE))
			: queriedItems;
		String nextCursor = hasMore ? encodeCursor(state.asOf(), items.getLast()) : null;
		SummaryCounts counts = jdbcClient.sql(SUMMARY_SQL)
			.param("userId", userId)
			.param("asOf", asOf)
			.query(ActivityService::mapSummaryCounts)
			.single();
		HighestVoted highestVoted = jdbcClient.sql(HIGHEST_VOTED_SQL)
			.param("userId", userId)
			.param("asOf", asOf)
			.query(ActivityService::mapHighestVoted)
			.optional()
			.orElse(null);

		return new ActivityResponse(
			state.asOf(),
			new Summary(
				counts.recommendationCount(),
				counts.firstPickCount(),
				counts.receivedVoteCount(),
				highestVoted),
			items,
			new Page(PAGE_SIZE, hasMore, nextCursor));
	}

	private static ActivityItem mapActivityItem(ResultSet row, int rowNumber) throws SQLException {
		return new ActivityItem(
			row.getObject("recommendation_id", UUID.class),
			row.getObject("track_id", UUID.class),
			row.getString("title"),
			row.getString("artist_name"),
			row.getString("album_cover_url"),
			row.getObject("recommended_on", java.time.LocalDate.class),
			row.getObject("created_at", OffsetDateTime.class).toInstant(),
			row.getString("comment"),
			row.getInt("vote_count"),
			row.getBoolean("first_pick"));
	}

	private static HighestVoted mapHighestVoted(ResultSet row, int rowNumber) throws SQLException {
		return new HighestVoted(
			row.getObject("track_id", UUID.class),
			row.getString("title"),
			row.getString("artist_name"),
			row.getString("album_cover_url"),
			row.getObject("recommended_on", java.time.LocalDate.class),
			row.getInt("vote_count"));
	}

	private static SummaryCounts mapSummaryCounts(ResultSet row, int rowNumber) throws SQLException {
		return new SummaryCounts(
			row.getInt("recommendation_count"),
			row.getInt("first_pick_count"),
			row.getInt("received_vote_count"));
	}

	private static String encodeCursor(Instant asOf, ActivityItem lastItem) {
		String value = String.join("|",
			asOf.toString(),
			lastItem.createdAt().toString(),
			lastItem.recommendationId().toString());
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
			throw ActivityException.invalidCursor(exception);
		}
	}

	private record SummaryCounts(
		int recommendationCount,
		int firstPickCount,
		int receivedVoteCount
	) {
	}

	private record CursorState(
		Instant asOf,
		Instant afterCreatedAt,
		UUID afterRecommendationId,
		boolean firstPage
	) {
	}
}
