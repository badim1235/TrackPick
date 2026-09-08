package io.github.badim1235.trackdrop.shared.retention;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class DataRetentionScheduler {
	private static final String PURGE_SQL = """
		WITH removed_signup_events AS (
			DELETE FROM signup_ip_events
			WHERE created_at < :ipCutoff
			RETURNING 1
		), removed_request_limits AS (
			DELETE FROM auth_ip_request_limits
			WHERE updated_at < :ipCutoff
			RETURNING 1
		), removed_blocks AS (
			DELETE FROM signup_ip_blocks
			WHERE blocked_until <= :now
			RETURNING 1
		), removed_reports AS (
			DELETE FROM content_reports
			WHERE status <> 'PENDING'
			  AND resolved_at < :reportCutoff
			RETURNING 1
		)
		SELECT
			(SELECT COUNT(*) FROM removed_signup_events),
			(SELECT COUNT(*) FROM removed_request_limits),
			(SELECT COUNT(*) FROM removed_blocks),
			(SELECT COUNT(*) FROM removed_reports)
		""";

	private final JdbcClient jdbcClient;
	private final Clock clock;

	DataRetentionScheduler(JdbcClient jdbcClient, Clock clock) {
		this.jdbcClient = jdbcClient;
		this.clock = clock;
	}

	@Transactional
	@Scheduled(cron = "${trackdrop.retention.cron:0 */5 * * * *}", zone = "UTC")
	void purgeExpiredData() {
		OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
		jdbcClient.sql(PURGE_SQL)
			.param("now", now)
			.param("ipCutoff", now.minusHours(23).minusMinutes(55))
			.param("reportCutoff", now.minusMonths(3))
			.query((row, rowNumber) -> row.getLong(1))
			.single();
	}
}
