package io.github.badim1235.trackdrop.track;

import io.github.badim1235.trackdrop.catalog.GenreResponse.Genre;
import io.github.badim1235.trackdrop.catalog.MusicProvider;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaService;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaSnapshot;
import io.github.badim1235.trackdrop.track.TrackDetailResponse.Actions;
import io.github.badim1235.trackdrop.track.TrackDetailResponse.Preview;
import io.github.badim1235.trackdrop.track.TrackDetailResponse.ProviderReference;
import io.github.badim1235.trackdrop.track.TrackDetailResponse.Recommendation;
import io.github.badim1235.trackdrop.track.TrackDetailResponse.Today;
import io.github.badim1235.trackdrop.track.TrackDetailResponse.Track;
import io.github.badim1235.trackdrop.track.TrackDetailResponse.Viewer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TrackDetailService {
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
	private static final UUID EMPTY_UUID = new UUID(0, 0);
	private static final String DETAIL_SQL = """
		WITH vote_counts AS (
			SELECT vote.track_id, COUNT(vote.id)::INTEGER AS vote_count
			FROM votes vote
			WHERE vote.voted_on = :today
			  AND vote.created_at <= :asOf
			GROUP BY vote.track_id
		), ranked AS (
			SELECT
				vote_counts.track_id,
				vote_counts.vote_count,
				ROW_NUMBER() OVER (
					ORDER BY
						vote_counts.vote_count DESC,
						track.title COLLATE trackdrop_nocase ASC,
						track.artist_name COLLATE trackdrop_nocase ASC,
						track.id ASC
				) AS overall_rank,
				ROW_NUMBER() OVER (
					PARTITION BY recommendation.primary_genre_id
					ORDER BY
						vote_counts.vote_count DESC,
						track.title COLLATE trackdrop_nocase ASC,
						track.artist_name COLLATE trackdrop_nocase ASC,
						track.id ASC
				) AS genre_rank
			FROM vote_counts
			JOIN tracks track ON track.id = vote_counts.track_id
			JOIN LATERAL (
				SELECT latest.primary_genre_id
				FROM recommendations latest
				WHERE latest.track_id = track.id
				  AND latest.recommended_on <= :today
				ORDER BY latest.recommended_on DESC, latest.created_at DESC, latest.id DESC
				LIMIT 1
			) recommendation ON TRUE
		)
		SELECT
			track.id,
			track.title,
			track.artist_name,
			track.album_name,
			track.album_cover_url,
			track.release_year,
			track.isrc,
			track.explicit,
			track.provider_genre_name,
			genre.id AS genre_id,
			genre.code AS genre_code,
			genre.display_name AS genre_display_name,
			genre.sort_order AS genre_sort_order,
			first_recommendation.id AS first_recommendation_id,
			first_recommendation.recommender_user_id AS first_recommendation_owner_id,
			CASE WHEN first_recommendation.comment_visibility = 'VISIBLE' THEN first_recommendation.comment END AS first_comment,
			CASE WHEN first_recommendation.comment_visibility = 'VISIBLE' THEN first_recommender.public_nickname END AS first_recommender_nickname,
			first_recommender.activity AS first_recommender_activity,
			first_recommendation.created_at AS first_recommendation_created_at,
			latest_recommendation.id AS latest_recommendation_id,
			latest_recommendation.recommender_user_id AS latest_recommendation_owner_id,
			CASE WHEN latest_recommendation.comment_visibility = 'VISIBLE' THEN latest_recommendation.comment END AS latest_comment,
			CASE WHEN latest_recommendation.comment_visibility = 'VISIBLE' THEN latest_recommender.public_nickname END AS latest_recommender_nickname,
			latest_recommender.activity AS latest_recommender_activity,
			latest_recommendation.created_at AS latest_recommendation_created_at,
			latest_recommendation.recommendation_count,
			latest_recommendation.recommended_on AS latest_recommended_on,
			provider_ref.external_track_id,
			provider_ref.external_url,
			provider_ref.preview_url,
			provider_ref.metadata_refreshed_at,
			COALESCE(ranked.vote_count, 0) AS today_vote_count,
			ranked.overall_rank,
			ranked.genre_rank,
			EXISTS (
				SELECT 1
				FROM votes viewer_vote
				WHERE viewer_vote.user_id = :viewerId
				  AND viewer_vote.track_id = track.id
				  AND viewer_vote.voted_on = :today
			) AS has_voted_today,
			EXISTS (
				SELECT 1
				FROM votes current_vote
				WHERE current_vote.track_id = track.id
				  AND current_vote.voted_on = :today
			) AS in_current_chart,
			EXISTS (
				SELECT 1
				FROM content_reports report
				WHERE report.reporter_user_id = :viewerId
				  AND report.recommendation_id = first_recommendation.id
			) AS has_reported_first,
			EXISTS (
				SELECT 1
				FROM content_reports report
				WHERE report.reporter_user_id = :viewerId
				  AND report.recommendation_id = latest_recommendation.id
			) AS has_reported_latest
		FROM tracks track
		JOIN LATERAL (
			SELECT latest.id, latest.recommender_user_id, latest.primary_genre_id,
				latest.comment, latest.comment_visibility, latest.recommended_on, latest.created_at,
				COUNT(*) OVER () AS recommendation_count
			FROM recommendations latest
			WHERE latest.track_id = track.id
			ORDER BY latest.recommended_on DESC, latest.created_at DESC, latest.id DESC
			LIMIT 1
		) latest_recommendation ON TRUE
		JOIN LATERAL (
			SELECT first_pick.id, first_pick.recommender_user_id,
				first_pick.comment, first_pick.comment_visibility, first_pick.created_at
			FROM recommendations first_pick
			WHERE first_pick.track_id = track.id
			ORDER BY first_pick.recommended_on ASC, first_pick.created_at ASC, first_pick.id ASC
			LIMIT 1
		) first_recommendation ON TRUE
		JOIN users first_recommender ON first_recommender.id = first_recommendation.recommender_user_id
		JOIN users latest_recommender ON latest_recommender.id = latest_recommendation.recommender_user_id
		JOIN genres genre ON genre.id = latest_recommendation.primary_genre_id
		JOIN track_provider_refs provider_ref
		  ON provider_ref.track_id = track.id AND provider_ref.provider = 'APPLE_MUSIC'
		LEFT JOIN ranked ON ranked.track_id = track.id
		WHERE track.id = :trackId
		""";

	private final JdbcClient jdbcClient;
	private final DailyQuotaService quotaService;
	private final Clock clock;
	private final boolean reportsEnabled;

	TrackDetailService(
		JdbcClient jdbcClient,
		DailyQuotaService quotaService,
		Clock clock,
		@Value("${trackdrop.features.reports-enabled:true}") boolean reportsEnabled
	) {
		this.jdbcClient = jdbcClient;
		this.quotaService = quotaService;
		this.clock = clock;
		this.reportsEnabled = reportsEnabled;
	}

	@Transactional(readOnly = true)
	TrackDetailResponse get(UUID trackId, UUID viewerId) {
		Instant asOf = clock.instant();
		LocalDate today = LocalDate.ofInstant(asOf, SERVICE_ZONE);
		boolean authenticated = viewerId != null;
		TrackRow row = jdbcClient.sql(DETAIL_SQL)
			.param("today", today)
			.param("asOf", OffsetDateTime.ofInstant(asOf, ZoneOffset.UTC))
			.param("viewerId", authenticated ? viewerId : EMPTY_UUID)
			.param("trackId", trackId)
			.query(TrackDetailService::mapRow)
			.optional()
			.orElseThrow(TrackDetailException::notFound);
		List<Genre> genres = findGenres(trackId);
		DailyQuotaSnapshot quota = authenticated ? quotaService.current(viewerId) : null;
		ReportState firstReport = reportState(
			authenticated,
			viewerId,
			row.firstRecommendationOwnerId(),
			row.firstComment() != null,
			row.firstRecommenderActivity(),
			row.hasReportedFirst());
		ReportState latestReport = reportState(
			authenticated,
			viewerId,
			row.latestRecommendationOwnerId(),
			row.latestComment() != null,
			row.latestRecommenderActivity(),
			row.hasReportedLatest());
		Actions actions = actions(
			authenticated,
			row.hasVotedToday(),
			row.inCurrentChart(),
			row.latestRecommendedOn(),
			today,
			quota,
			firstReport);
		String previewUrl = row.previewUrl();

		Track track = new Track(
			row.id(),
			row.title(),
			row.artistName(),
			row.albumName(),
			row.albumCoverUrl(),
			row.releaseYear(),
			row.isrc(),
			row.explicit(),
			row.providerGenreName(),
			row.primaryGenre(),
			genres,
			new Recommendation(
				row.firstRecommendationId(),
				row.firstComment(),
				row.firstComment() != null,
				row.firstRecommenderNickname(),
				row.firstRecommendationCreatedAt(),
				firstReport.canReport(),
				firstReport.hasReported()),
			row.recommendationCount() > 1
				? new Recommendation(
					row.latestRecommendationId(),
					row.latestComment(),
					row.latestComment() != null,
					row.latestRecommenderNickname(),
					row.latestRecommendationCreatedAt(),
					latestReport.canReport(),
					latestReport.hasReported())
				: null,
			authenticated ? new Viewer(row.hasVotedToday()) : null,
			new Preview(
				previewUrl != null,
				MusicProvider.APPLE_MUSIC,
				"OFFICIAL_30_SECOND_CLIP",
				"PROVIDER_SELECTED",
				previewUrl),
			List.of(new ProviderReference(
				MusicProvider.APPLE_MUSIC,
				row.externalTrackId(),
				row.externalUrl(),
				row.metadataRefreshedAt())));

		return new TrackDetailResponse(
			track,
			new Today(row.todayVoteCount(), row.overallRank(), row.genreRank(), asOf),
			quota,
			actions);
	}

	private List<Genre> findGenres(UUID trackId) {
		return jdbcClient.sql("""
				SELECT genre.id, genre.code, genre.display_name, genre.sort_order
				FROM track_genres track_genre
				JOIN genres genre ON genre.id = track_genre.genre_id
				WHERE track_genre.track_id = :trackId
				ORDER BY genre.sort_order, genre.id
				""")
			.param("trackId", trackId)
			.query((row, rowNumber) -> new Genre(
				row.getObject("id", UUID.class),
				row.getString("code"),
				row.getString("display_name"),
				row.getInt("sort_order")))
			.list();
	}

	private Actions actions(
		boolean authenticated,
		boolean hasVotedToday,
		boolean inCurrentChart,
		LocalDate latestRecommendedOn,
		LocalDate today,
		DailyQuotaSnapshot quota,
		ReportState firstReport
	) {
		LocalDate availableOn = latestRecommendedOn.plusDays(3);
		if (hasVotedToday) {
			return new Actions(false, false, "ALREADY_VOTED", availableOn,
				firstReport.canReport(), firstReport.hasReported());
		}
		if (!inCurrentChart && today.isBefore(availableOn)) {
			return new Actions(false, false, "RECOMMENDATION_COOLDOWN", availableOn,
				firstReport.canReport(), firstReport.hasReported());
		}
		if (!authenticated) {
			return new Actions(false, false, "UNAUTHENTICATED", availableOn, false, false);
		}
		if (quota.remaining() == 0) {
			return new Actions(false, false, "DAILY_LIMIT_EXCEEDED", availableOn,
				firstReport.canReport(), firstReport.hasReported());
		}
		if (inCurrentChart) {
			return new Actions(true, false, null, availableOn,
				firstReport.canReport(), firstReport.hasReported());
		}
		return new Actions(false, true, null, availableOn,
			firstReport.canReport(), firstReport.hasReported());
	}

	private ReportState reportState(
		boolean authenticated,
		UUID viewerId,
		UUID recommendationOwnerId,
		boolean commentAvailable,
		String recommenderActivity,
		boolean hasReported
	) {
		boolean canReport = reportsEnabled
			&& authenticated
			&& commentAvailable
			&& !recommendationOwnerId.equals(viewerId)
			&& !"BAN".equals(recommenderActivity)
			&& !hasReported;
		return new ReportState(canReport, authenticated && hasReported);
	}

	private static TrackRow mapRow(ResultSet row, int rowNumber) throws SQLException {
		return new TrackRow(
			row.getObject("id", UUID.class),
			row.getString("title"),
			row.getString("artist_name"),
			row.getString("album_name"),
			row.getString("album_cover_url"),
			row.getObject("release_year") == null ? null : row.getInt("release_year"),
			row.getString("isrc"),
			row.getBoolean("explicit"),
			row.getString("provider_genre_name"),
			new Genre(
				row.getObject("genre_id", UUID.class),
				row.getString("genre_code"),
				row.getString("genre_display_name"),
				row.getInt("genre_sort_order")),
			row.getObject("first_recommendation_id", UUID.class),
			row.getObject("first_recommendation_owner_id", UUID.class),
			row.getString("first_comment"),
			row.getString("first_recommender_nickname"),
			row.getString("first_recommender_activity"),
			row.getObject("first_recommendation_created_at", OffsetDateTime.class).toInstant(),
			row.getObject("latest_recommendation_id", UUID.class),
			row.getObject("latest_recommendation_owner_id", UUID.class),
			row.getString("latest_comment"),
			row.getString("latest_recommender_nickname"),
			row.getString("latest_recommender_activity"),
			row.getObject("latest_recommendation_created_at", OffsetDateTime.class).toInstant(),
			row.getInt("recommendation_count"),
			row.getObject("latest_recommended_on", LocalDate.class),
			row.getString("external_track_id"),
			row.getString("external_url"),
			row.getString("preview_url"),
			row.getObject("metadata_refreshed_at", OffsetDateTime.class).toInstant(),
			row.getInt("today_vote_count"),
			nullableLong(row, "overall_rank"),
			nullableLong(row, "genre_rank"),
			row.getBoolean("has_voted_today"),
			row.getBoolean("in_current_chart"),
			row.getBoolean("has_reported_first"),
			row.getBoolean("has_reported_latest"));
	}

	private static Long nullableLong(ResultSet row, String column) throws SQLException {
		Number value = (Number) row.getObject(column);
		return value == null ? null : value.longValue();
	}

	private record TrackRow(
		UUID id,
		String title,
		String artistName,
		String albumName,
		String albumCoverUrl,
		Integer releaseYear,
		String isrc,
		boolean explicit,
		String providerGenreName,
		Genre primaryGenre,
		UUID firstRecommendationId,
		UUID firstRecommendationOwnerId,
		String firstComment,
		String firstRecommenderNickname,
		String firstRecommenderActivity,
		Instant firstRecommendationCreatedAt,
		UUID latestRecommendationId,
		UUID latestRecommendationOwnerId,
		String latestComment,
		String latestRecommenderNickname,
		String latestRecommenderActivity,
		Instant latestRecommendationCreatedAt,
		int recommendationCount,
		LocalDate latestRecommendedOn,
		String externalTrackId,
		String externalUrl,
		String previewUrl,
		Instant metadataRefreshedAt,
		int todayVoteCount,
		Long overallRank,
		Long genreRank,
		boolean hasVotedToday,
		boolean inCurrentChart,
		boolean hasReportedFirst,
		boolean hasReportedLatest
	) {
	}

	private record ReportState(boolean canReport, boolean hasReported) {
	}
}
