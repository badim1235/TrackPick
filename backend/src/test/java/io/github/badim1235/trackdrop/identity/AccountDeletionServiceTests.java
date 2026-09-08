package io.github.badim1235.trackdrop.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.badim1235.trackdrop.TestcontainersConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountDeletionServiceTests {

	private static final UUID GENRE_ID = UUID.fromString("10000000-0000-0000-0000-000000000020");

	@Autowired
	private AccountDeletionService accountDeletionService;

	@Autowired
	private JdbcClient jdbcClient;

	@MockitoBean
	private SupabaseAuthGateway supabaseAuth;

	@MockitoBean
	private SupabaseUserDirectory supabaseUsers;

	@Test
	void deletesOnlyTheWithdrawingUsersActivityAndOrphanedCatalogTracks() {
		UUID userId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		UUID withdrawnTrackId = UUID.randomUUID();
		UUID retainedTrackId = UUID.randomUUID();
		UUID withdrawnRecommendationId = UUID.randomUUID();
		UUID retainedRecommendationId = UUID.randomUUID();
		LocalDate withdrawnDate = LocalDate.of(2042, 1, 2);
		LocalDate retainedDate = withdrawnDate.plusDays(1);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		String email = userId + "@example.com";

		insertUser(userId, email, "withdrawn" + userId.toString().substring(0, 8), now);
		insertUser(otherUserId, otherUserId + "@example.com", "retained" + otherUserId.toString().substring(0, 8), now);
		insertTrack(withdrawnTrackId, "withdrawn-track", now);
		insertTrack(retainedTrackId, "retained-track", now);
		insertRecommendation(withdrawnRecommendationId, userId, withdrawnTrackId, withdrawnDate, now);
		insertRecommendation(retainedRecommendationId, otherUserId, retainedTrackId, retainedDate, now);
		insertVote(userId, withdrawnTrackId, withdrawnDate, now);
		insertVote(otherUserId, withdrawnTrackId, withdrawnDate, now);
		insertVote(userId, retainedTrackId, retainedDate, now);
		insertReport(userId, retainedRecommendationId, now);
		UUID resolvedReportId = insertResolvedReport(otherUserId, withdrawnRecommendationId, now);
		insertRanking(withdrawnTrackId, withdrawnDate, now);
		jdbcClient.sql("""
				INSERT INTO daily_recommendation_quotas (user_id, quota_date, daily_limit, used_count, updated_at)
				VALUES (:userId, :date, 4, 2, :now)
				""")
			.param("userId", userId)
			.param("date", withdrawnDate)
			.param("now", now)
			.update();
		when(supabaseAuth.signIn(email, "chatgpt5555"))
			.thenReturn(new SupabaseAuthGateway.AuthenticatedUser(userId, email, Instant.now()));

		accountDeletionService.delete(userId, "chatgpt5555");

		verify(supabaseAuth).deleteUser(userId);
		assertThat(count("users", "id", userId)).isZero();
		assertThat(count("users", "id", otherUserId)).isOne();
		assertThat(count("recommendations", "id", withdrawnRecommendationId)).isZero();
		assertThat(count("recommendations", "id", retainedRecommendationId)).isOne();
		assertThat(count("votes", "track_id", withdrawnTrackId)).isZero();
		assertThat(count("votes", "user_id", userId)).isZero();
		assertThat(count("content_reports", "reporter_user_id", userId)).isZero();
		assertThat(count("content_reports", "recommendation_id", withdrawnRecommendationId)).isZero();
		assertThat(count("content_reports", "id", resolvedReportId)).isOne();
		assertThat(jdbcClient.sql("""
				SELECT recommendation_id IS NULL AND reported_user_id IS NULL
				FROM content_reports
				WHERE id = :id
				""")
			.param("id", resolvedReportId)
			.query(Boolean.class)
			.single()).isTrue();
		assertThat(count("daily_rankings", "track_id", withdrawnTrackId)).isZero();
		assertThat(count("daily_recommendation_quotas", "user_id", userId)).isZero();
		assertThat(count("tracks", "id", withdrawnTrackId)).isZero();
		assertThat(count("tracks", "id", retainedTrackId)).isOne();
	}

	@Test
	void restoresLocalDataWhenSupabaseCannotDeleteTheAuthUser() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		String email = userId + "@example.com";
		insertUser(userId, email, "rollback" + userId.toString().substring(0, 8), now);
		when(supabaseAuth.signIn(email, "chatgpt5555"))
			.thenReturn(new SupabaseAuthGateway.AuthenticatedUser(userId, email, Instant.now()));
		doThrow(IdentityException.accountDeletionUnavailable())
			.when(supabaseAuth).deleteUser(userId);

		assertThatThrownBy(() -> accountDeletionService.delete(userId, "chatgpt5555"))
			.isInstanceOf(IdentityException.class)
			.hasMessage("회원 탈퇴를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");

		assertThat(count("users", "id", userId)).isOne();
	}

	private void insertUser(UUID id, String email, String nickname, OffsetDateTime now) {
		jdbcClient.sql("""
				INSERT INTO users (
					id, email, email_normalized, email_verified_at,
					public_nickname, status, created_at, updated_at
				)
				VALUES (:id, :email, :email, :now, :nickname, 'ACTIVE', :now, :now)
				""")
			.param("id", id)
			.param("email", email)
			.param("nickname", nickname)
			.param("now", now)
			.update();
	}

	private void insertTrack(UUID id, String title, OffsetDateTime now) {
		jdbcClient.sql("""
				INSERT INTO tracks (id, title, artist_name, explicit, created_at, updated_at)
				VALUES (:id, :title, 'artist', FALSE, :now, :now)
				""")
			.param("id", id)
			.param("title", title)
			.param("now", now)
			.update();
		jdbcClient.sql("""
				INSERT INTO track_genres (track_id, genre_id, source, created_at)
				VALUES (:trackId, :genreId, 'PROVIDER', :now)
				""")
			.param("trackId", id)
			.param("genreId", GENRE_ID)
			.param("now", now)
			.update();
	}

	private void insertRecommendation(
		UUID id,
		UUID userId,
		UUID trackId,
		LocalDate date,
		OffsetDateTime now
	) {
		jdbcClient.sql("""
				INSERT INTO recommendations (
					id, recommender_user_id, track_id, primary_genre_id,
					comment, comment_visibility, recommended_on, created_at
				)
				VALUES (:id, :userId, :trackId, :genreId, 'comment', 'VISIBLE', :date, :now)
				""")
			.param("id", id)
			.param("userId", userId)
			.param("trackId", trackId)
			.param("genreId", GENRE_ID)
			.param("date", date)
			.param("now", now)
			.update();
	}

	private void insertVote(UUID userId, UUID trackId, LocalDate date, OffsetDateTime now) {
		jdbcClient.sql("""
				INSERT INTO votes (id, user_id, track_id, voted_on, created_at)
				VALUES (:id, :userId, :trackId, :date, :now)
				""")
			.param("id", UUID.randomUUID())
			.param("userId", userId)
			.param("trackId", trackId)
			.param("date", date)
			.param("now", now)
			.update();
	}

	private void insertReport(UUID reporterId, UUID recommendationId, OffsetDateTime now) {
		jdbcClient.sql("""
				INSERT INTO content_reports (
					id, reporter_user_id, reported_user_id, recommendation_id,
					reason_code, status, created_at
				)
				SELECT :id, :reporterId, recommendation.recommender_user_id, :recommendationId,
					'OTHER', 'PENDING', :now
				FROM recommendations recommendation
				WHERE recommendation.id = :recommendationId
				""")
			.param("id", UUID.randomUUID())
			.param("reporterId", reporterId)
			.param("recommendationId", recommendationId)
			.param("now", now)
			.update();
	}

	private UUID insertResolvedReport(UUID reporterId, UUID recommendationId, OffsetDateTime now) {
		UUID reportId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO content_reports (
					id, reporter_user_id, reported_user_id, recommendation_id,
					reason_code, status, created_at, resolved_at,
					resolved_by_user_id, resolution_action
				)
				SELECT :id, :reporterId, recommendation.recommender_user_id, :recommendationId,
					'OTHER', 'DISMISSED', :now, :now, :reporterId, 'DISMISS'
				FROM recommendations recommendation
				WHERE recommendation.id = :recommendationId
				""")
			.param("id", reportId)
			.param("reporterId", reporterId)
			.param("recommendationId", recommendationId)
			.param("now", now)
			.update();
		return reportId;
	}

	private void insertRanking(UUID trackId, LocalDate date, OffsetDateTime now) {
		UUID runId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO ranking_runs (
					id, ranking_date, status, attempt_count, completed_at, created_at, updated_at
				)
				VALUES (:id, :date, 'COMPLETED', 1, :now, :now, :now)
				""")
			.param("id", runId)
			.param("date", date)
			.param("now", now)
			.update();
		jdbcClient.sql("""
				INSERT INTO daily_rankings (
					id, ranking_run_id, ranking_date, scope_type,
					track_id, rank, vote_count, created_at
				)
				VALUES (:id, :runId, :date, 'ALL', :trackId, 1, 2, :now)
				""")
			.param("id", UUID.randomUUID())
			.param("runId", runId)
			.param("date", date)
			.param("trackId", trackId)
			.param("now", now)
			.update();
	}

	private long count(String table, String column, UUID value) {
		return jdbcClient.sql("SELECT count(*) FROM " + table + " WHERE " + column + " = :value")
			.param("value", value)
			.query(Long.class)
			.single();
	}
}
