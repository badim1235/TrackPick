package io.github.badim1235.trackdrop.moderation;

import io.github.badim1235.trackdrop.moderation.ReportResponse.Report;
import io.github.badim1235.trackdrop.identity.UserActivity;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReportService {
	private final JdbcClient jdbcClient;
	private final Clock clock;
	private final boolean enabled;
	private final int reportLimit;

	ReportService(
		JdbcClient jdbcClient,
		Clock clock,
		@Value("${trackdrop.features.reports-enabled:true}") boolean enabled,
		@Value("${trackdrop.moderation.report-limit:3}") int reportLimit
	) {
		this.jdbcClient = jdbcClient;
		this.clock = clock;
		this.enabled = enabled;
		if (reportLimit < 1) {
			throw new IllegalArgumentException("Report limit must be at least 1");
		}
		this.reportLimit = reportLimit;
	}

	@Transactional
	ReportResponse create(UUID reporterId, UUID recommendationId, ReportRequest request) {
		if (!enabled) {
			throw ReportException.featureDisabled();
		}
		ReportTarget target = jdbcClient.sql("""
				SELECT recommendation.recommender_user_id, target_user.activity
				FROM recommendations recommendation
				JOIN users target_user ON target_user.id = recommendation.recommender_user_id
				WHERE recommendation.id = :recommendationId
				FOR UPDATE OF target_user
				""")
			.param("recommendationId", recommendationId)
			.query((row, rowNumber) -> new ReportTarget(
				row.getObject("recommender_user_id", UUID.class),
				UserActivity.valueOf(row.getString("activity"))))
			.optional()
			.orElseThrow(ReportException::recommendationNotFound);
		if (target.userId().equals(reporterId)) {
			throw ReportException.selfReportNotAllowed();
		}
		if (target.activity() == UserActivity.BAN) {
			throw ReportException.reportedUserBanned();
		}

		UUID reportId = UUID.randomUUID();
		Instant createdAt = clock.instant();
		String details = normalizeDetails(request.details());
		try {
			jdbcClient.sql("""
					INSERT INTO content_reports (
						id, reporter_user_id, reported_user_id, recommendation_id,
						reason_code, details, status, created_at
					)
					VALUES (
						:id, :reporterId, :reportedUserId, :recommendationId,
						:reasonCode, :details, 'PENDING', :createdAt
					)
					""")
				.param("id", reportId)
				.param("reporterId", reporterId)
				.param("reportedUserId", target.userId())
				.param("recommendationId", recommendationId)
				.param("reasonCode", request.reasonCode().name())
				.param("details", details)
				.param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
				.update();
		} catch (DuplicateKeyException exception) {
			throw ReportException.alreadyReported(exception);
		}

		long pendingReports = jdbcClient.sql("""
				SELECT count(*)
				FROM content_reports
				WHERE reported_user_id = :reportedUserId
				  AND status = 'PENDING'
				""")
			.param("reportedUserId", target.userId())
			.query(Long.class)
			.single();
		if (pendingReports >= reportLimit && target.activity() == UserActivity.NORMAL) {
			jdbcClient.sql("""
					UPDATE users
					SET activity = 'FLAGGED', updated_at = :updatedAt
					WHERE id = :userId AND activity = 'NORMAL'
					""")
				.param("updatedAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
				.param("userId", target.userId())
				.update();
		}
		return new ReportResponse(new Report(reportId, "PENDING", createdAt));
	}

	private static String normalizeDetails(String details) {
		if (details == null || details.isBlank()) {
			return null;
		}
		return details.trim();
	}

	private record ReportTarget(UUID userId, UserActivity activity) {
	}
}
