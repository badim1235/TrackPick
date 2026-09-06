package io.github.badim1235.trackdrop.moderation;

import io.github.badim1235.trackdrop.identity.AuthSessionService;
import io.github.badim1235.trackdrop.identity.UserActivity;
import io.github.badim1235.trackdrop.moderation.AdminReportController.ModerationAction;
import io.github.badim1235.trackdrop.moderation.AdminReportResponse.FlaggedUser;
import io.github.badim1235.trackdrop.moderation.AdminReportResponse.FlaggedUsers;
import io.github.badim1235.trackdrop.moderation.AdminReportResponse.ModerationResult;
import io.github.badim1235.trackdrop.moderation.AdminReportResponse.Recommendation;
import io.github.badim1235.trackdrop.moderation.AdminReportResponse.ReportRecord;
import io.github.badim1235.trackdrop.moderation.AdminReportResponse.ReportedUser;
import io.github.badim1235.trackdrop.moderation.AdminReportResponse.Reporter;
import io.github.badim1235.trackdrop.moderation.AdminReportResponse.UserReports;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AdminReportService {

	private final JdbcClient jdbcClient;
	private final AuthSessionService authSessionService;
	private final Clock clock;
	private final boolean enabled;

	AdminReportService(
		JdbcClient jdbcClient,
		AuthSessionService authSessionService,
		Clock clock,
		@Value("${trackdrop.features.reports-enabled:true}") boolean enabled
	) {
		this.jdbcClient = jdbcClient;
		this.authSessionService = authSessionService;
		this.clock = clock;
		this.enabled = enabled;
	}

	@Transactional(readOnly = true)
	FlaggedUsers findFlaggedUsers(String searchQuery) {
		requireEnabled();
		String query = searchQuery == null ? "" : searchQuery.trim();
		List<FlaggedUser> users = jdbcClient.sql("""
				SELECT
					user_account.id,
					user_account.email,
					user_account.public_nickname,
					user_account.activity,
					COUNT(report.id)::INTEGER AS pending_report_count,
					MIN(report.created_at) AS first_reported_at,
					MAX(report.created_at) AS latest_reported_at
				FROM users user_account
				JOIN content_reports report
				  ON report.reported_user_id = user_account.id
				 AND report.status = 'PENDING'
				WHERE user_account.activity = 'FLAGGED'
				  AND (
					:query = ''
					OR user_account.public_nickname ILIKE '%' || :query || '%'
					OR user_account.email ILIKE '%' || :query || '%'
				  )
				GROUP BY user_account.id
				ORDER BY pending_report_count DESC, first_reported_at ASC, user_account.id ASC
				LIMIT 50
				""")
			.param("query", query)
			.query(AdminReportService::mapFlaggedUser)
			.list();
		return new FlaggedUsers(users);
	}

	@Transactional(readOnly = true)
	UserReports findUserReports(UUID userId) {
		requireEnabled();
		ReportedUser user = jdbcClient.sql("""
				SELECT
					user_account.id,
					user_account.email,
					user_account.public_nickname,
					user_account.activity,
					COUNT(report.id) FILTER (WHERE report.status = 'PENDING')::INTEGER
						AS pending_report_count
				FROM users user_account
				LEFT JOIN content_reports report ON report.reported_user_id = user_account.id
				WHERE user_account.id = :userId
				GROUP BY user_account.id
				""")
			.param("userId", userId)
			.query((row, rowNumber) -> new ReportedUser(
				row.getObject("id", UUID.class),
				row.getString("email"),
				row.getString("public_nickname"),
				row.getString("activity"),
				row.getInt("pending_report_count")))
			.optional()
			.orElseThrow(ReportException::userNotFound);

		List<ReportRecord> reports = jdbcClient.sql("""
				SELECT
					report.id,
					report.reason_code,
					report.details,
					report.status,
					report.created_at,
					reporter.id AS reporter_id,
					reporter.public_nickname AS reporter_nickname,
					recommendation.id AS recommendation_id,
					recommendation.comment,
					track.id AS track_id,
					track.title AS track_title,
					track.artist_name
				FROM content_reports report
				JOIN users reporter ON reporter.id = report.reporter_user_id
				JOIN recommendations recommendation ON recommendation.id = report.recommendation_id
				JOIN tracks track ON track.id = recommendation.track_id
				WHERE report.reported_user_id = :userId
				ORDER BY
					CASE WHEN report.status = 'PENDING' THEN 0 ELSE 1 END,
					report.created_at DESC,
					report.id DESC
				""")
			.param("userId", userId)
			.query(AdminReportService::mapReport)
			.list();
		return new UserReports(user, reports);
	}

	@Transactional
	ModerationResult moderate(UUID adminId, UUID userId, ModerationAction action) {
		requireEnabled();
		if (adminId.equals(userId)) {
			throw ReportException.selfModerationNotAllowed();
		}

		ModerationTarget target = jdbcClient.sql("""
				SELECT email, activity, status
				FROM users
				WHERE id = :userId
				FOR UPDATE
				""")
			.param("userId", userId)
			.query((row, rowNumber) -> new ModerationTarget(
				row.getString("email"),
				UserActivity.valueOf(row.getString("activity")),
				row.getString("status")))
			.optional()
			.orElseThrow(ReportException::userNotFound);
		if (target.activity() != UserActivity.FLAGGED) {
			throw ReportException.userNotFlagged();
		}

		Instant resolvedAt = clock.instant();
		OffsetDateTime resolvedAtUtc = OffsetDateTime.ofInstant(resolvedAt, ZoneOffset.UTC);
		String nextActivity = action == ModerationAction.BAN ? "BAN" : "NORMAL";
		String nextStatus = action == ModerationAction.BAN ? "SUSPENDED" : target.status();
		String reportStatus = action == ModerationAction.BAN ? "ACTIONED" : "DISMISSED";

		jdbcClient.sql("""
				UPDATE users
				SET activity = :activity, status = :status, updated_at = :updatedAt
				WHERE id = :userId
				""")
			.param("activity", nextActivity)
			.param("status", nextStatus)
			.param("updatedAt", resolvedAtUtc)
			.param("userId", userId)
			.update();

		int resolvedCount = jdbcClient.sql("""
				UPDATE content_reports
				SET status = :status,
					resolved_at = :resolvedAt,
					resolved_by_user_id = :adminId,
					resolution_action = :action
				WHERE reported_user_id = :userId
				  AND status = 'PENDING'
				""")
			.param("status", reportStatus)
			.param("resolvedAt", resolvedAtUtc)
			.param("adminId", adminId)
			.param("action", action.name())
			.param("userId", userId)
			.update();

		if (action == ModerationAction.BAN) {
			jdbcClient.sql("""
					UPDATE recommendations
					SET comment_visibility = 'HIDDEN'
					WHERE recommender_user_id = :userId
					""")
				.param("userId", userId)
				.update();
			authSessionService.revokeAll(target.email());
		}

		return new ModerationResult(
			userId, nextActivity, nextStatus, resolvedCount, resolvedAt);
	}

	private void requireEnabled() {
		if (!enabled) {
			throw ReportException.featureDisabled();
		}
	}

	private static FlaggedUser mapFlaggedUser(ResultSet row, int rowNumber) throws SQLException {
		return new FlaggedUser(
			row.getObject("id", UUID.class),
			row.getString("email"),
			row.getString("public_nickname"),
			row.getString("activity"),
			row.getInt("pending_report_count"),
			row.getObject("first_reported_at", OffsetDateTime.class).toInstant(),
			row.getObject("latest_reported_at", OffsetDateTime.class).toInstant());
	}

	private static ReportRecord mapReport(ResultSet row, int rowNumber) throws SQLException {
		return new ReportRecord(
			row.getObject("id", UUID.class),
			row.getString("reason_code"),
			row.getString("details"),
			row.getString("status"),
			row.getObject("created_at", OffsetDateTime.class).toInstant(),
			new Reporter(
				row.getObject("reporter_id", UUID.class),
				row.getString("reporter_nickname")),
			new Recommendation(
				row.getObject("recommendation_id", UUID.class),
				row.getString("comment"),
				row.getObject("track_id", UUID.class),
				row.getString("track_title"),
				row.getString("artist_name")));
	}

	private record ModerationTarget(String email, UserActivity activity, String status) {
	}
}
