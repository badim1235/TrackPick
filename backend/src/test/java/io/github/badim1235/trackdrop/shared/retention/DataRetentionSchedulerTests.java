package io.github.badim1235.trackdrop.shared.retention;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.badim1235.trackdrop.TestcontainersConfiguration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DataRetentionSchedulerTests {
	@Autowired
	private DataRetentionScheduler scheduler;

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void removesExpiredIpHashesAndResolvedReportsOnly() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		String oldHash = "a".repeat(64);
		String recentHash = "b".repeat(64);
		insertIpData(oldHash, now.minusHours(25), now.minusMinutes(1));
		insertIpData(recentHash, now.minusHours(1), now.plusHours(1));

		UUID reporter = createUser("retention-reporter", now);
		UUID reported = createUser("retention-reported", now);
		UUID resolver = createUser("retention-resolver", now);
		UUID oldResolved = createReport(reporter, reported, resolver, "DISMISSED", now.minusMonths(4), now);
		UUID recentResolved = createReport(reporter, reported, resolver, "DISMISSED", now.minusMonths(1), now.plusSeconds(1));
		UUID oldPending = createReport(reporter, reported, null, "PENDING", null, now.plusSeconds(2));

		scheduler.purgeExpiredData();

		assertThat(count("signup_ip_events", "ip_hash", oldHash)).isZero();
		assertThat(count("auth_ip_request_limits", "ip_hash", oldHash)).isZero();
		assertThat(count("signup_ip_blocks", "ip_hash", oldHash)).isZero();
		assertThat(count("signup_ip_events", "ip_hash", recentHash)).isOne();
		assertThat(count("auth_ip_request_limits", "ip_hash", recentHash)).isOne();
		assertThat(count("signup_ip_blocks", "ip_hash", recentHash)).isOne();
		assertThat(reportExists(oldResolved)).isFalse();
		assertThat(reportExists(recentResolved)).isTrue();
		assertThat(reportExists(oldPending)).isTrue();
	}

	private void insertIpData(String ipHash, OffsetDateTime recordedAt, OffsetDateTime blockedUntil) {
		jdbcClient.sql("""
				INSERT INTO auth_ip_request_limits (
					ip_hash, window_started_at, request_count, updated_at
				) VALUES (:ipHash, :recordedAt, 1, :recordedAt)
				""")
			.param("ipHash", ipHash)
			.param("recordedAt", recordedAt)
			.update();
		jdbcClient.sql("INSERT INTO signup_ip_events (ip_hash, created_at) VALUES (:ipHash, :recordedAt)")
			.param("ipHash", ipHash)
			.param("recordedAt", recordedAt)
			.update();
		jdbcClient.sql("""
				INSERT INTO signup_ip_blocks (ip_hash, blocked_until, updated_at)
				VALUES (:ipHash, :blockedUntil, :recordedAt)
				""")
			.param("ipHash", ipHash)
			.param("blockedUntil", blockedUntil)
			.param("recordedAt", recordedAt)
			.update();
	}

	private UUID createUser(String name, OffsetDateTime now) {
		UUID id = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO users (
					id, email, email_normalized, email_verified_at,
					public_nickname, status, created_at, updated_at
				) VALUES (:id, :email, :email, :now, :nickname, 'ACTIVE', :now, :now)
				""")
			.param("id", id)
			.param("email", name + "-" + id.toString().substring(0, 8) + "@example.com")
			.param("nickname", name + id.toString().substring(0, 8))
			.param("now", now)
			.update();
		return id;
	}

	private UUID createReport(
		UUID reporter,
		UUID reported,
		UUID resolver,
		String status,
		OffsetDateTime resolvedAt,
		OffsetDateTime createdAt
	) {
		UUID genreId = jdbcClient.sql("SELECT id FROM genres WHERE code = 'rock'")
			.query(UUID.class)
			.single();
		UUID trackId = UUID.randomUUID();
		UUID recommendationId = UUID.randomUUID();
		UUID reportId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO tracks (id, title, artist_name, explicit, created_at, updated_at)
				VALUES (:id, :title, 'Retention Artist', FALSE, :createdAt, :createdAt)
				""")
			.param("id", trackId)
			.param("title", "Retention " + trackId)
			.param("createdAt", createdAt)
			.update();
		jdbcClient.sql("""
				INSERT INTO track_genres (track_id, genre_id, source, created_at)
				VALUES (:trackId, :genreId, 'PROVIDER', :createdAt)
				""")
			.param("trackId", trackId)
			.param("genreId", genreId)
			.param("createdAt", createdAt)
			.update();
		jdbcClient.sql("""
				INSERT INTO recommendations (
					id, recommender_user_id, track_id, primary_genre_id,
					comment, comment_visibility, recommended_on, created_at
				) VALUES (
					:id, :reported, :trackId, :genreId,
					'보관 정책 테스트', 'VISIBLE', :recommendedOn, :createdAt
				)
				""")
			.param("id", recommendationId)
			.param("reported", reported)
			.param("trackId", trackId)
			.param("genreId", genreId)
			.param("recommendedOn", LocalDate.now(ZoneOffset.UTC))
			.param("createdAt", createdAt)
			.update();
		jdbcClient.sql("""
				INSERT INTO content_reports (
					id, reporter_user_id, recommendation_id, reported_user_id,
					reason_code, status, created_at, resolved_at,
					resolved_by_user_id, resolution_action
				) VALUES (
					:id, :reporter, :recommendationId, :reported,
					'SPAM', :status, :createdAt, :resolvedAt,
					:resolver, :resolutionAction
				)
				""")
			.param("id", reportId)
			.param("reporter", reporter)
			.param("recommendationId", recommendationId)
			.param("reported", reported)
			.param("status", status)
			.param("createdAt", createdAt)
			.param("resolvedAt", resolvedAt)
			.param("resolver", resolver)
			.param("resolutionAction", resolver == null ? null : "DISMISS")
			.update();
		return reportId;
	}

	private long count(String table, String column, String value) {
		return jdbcClient.sql("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = :value")
			.param("value", value)
			.query(Long.class)
			.single();
	}

	private boolean reportExists(UUID reportId) {
		return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM content_reports WHERE id = :id)")
			.param("id", reportId)
			.query(Boolean.class)
			.single();
	}
}
