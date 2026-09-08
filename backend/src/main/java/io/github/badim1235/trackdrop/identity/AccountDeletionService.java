package io.github.badim1235.trackdrop.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDeletionService {

	private final JdbcClient jdbcClient;
	private final SupabaseAuthGateway supabaseAuth;

	public AccountDeletionService(JdbcClient jdbcClient, SupabaseAuthGateway supabaseAuth) {
		this.jdbcClient = jdbcClient;
		this.supabaseAuth = supabaseAuth;
	}

	@Transactional
	public String delete(UUID userId, String password) {
		String email = jdbcClient.sql("SELECT email FROM users WHERE id = :userId FOR UPDATE")
			.param("userId", userId)
			.query(String.class)
			.optional()
			.orElseThrow(IdentityException::invalidCredentials);

		SupabaseAuthGateway.AuthenticatedUser authenticatedUser = supabaseAuth.signIn(email, password);
		if (!userId.equals(authenticatedUser.id())) {
			throw IdentityException.invalidCredentials();
		}

		List<UUID> recommendedTrackIds = jdbcClient.sql("""
				SELECT DISTINCT track_id
				FROM recommendations
				WHERE recommender_user_id = :userId
				""")
			.param("userId", userId)
			.query(UUID.class)
			.list();

		deleteRecommendationDependents(userId);
		jdbcClient.sql("DELETE FROM daily_recommendation_quotas WHERE user_id = :userId")
			.param("userId", userId)
			.update();
		jdbcClient.sql("DELETE FROM recommendations WHERE recommender_user_id = :userId")
			.param("userId", userId)
			.update();
		deleteOrphanedTracks(recommendedTrackIds);

		int deleted = jdbcClient.sql("DELETE FROM users WHERE id = :userId")
			.param("userId", userId)
			.update();
		if (deleted != 1) {
			throw IdentityException.invalidCredentials();
		}

		supabaseAuth.deleteUser(userId);
		return email;
	}

	private void deleteRecommendationDependents(UUID userId) {
		jdbcClient.sql("""
				DELETE FROM daily_rankings ranking
				USING recommendations recommendation
				WHERE recommendation.recommender_user_id = :userId
				  AND ranking.track_id = recommendation.track_id
				  AND ranking.ranking_date = recommendation.recommended_on
				""")
			.param("userId", userId)
			.update();
		jdbcClient.sql("""
				DELETE FROM content_reports report
				WHERE report.status = 'PENDING'
				  AND (
				   report.reporter_user_id = :userId
				   OR report.reported_user_id = :userId
				   OR EXISTS (
				       SELECT 1
				       FROM recommendations recommendation
				       WHERE recommendation.id = report.recommendation_id
				         AND recommendation.recommender_user_id = :userId
				   )
				  )
				""")
			.param("userId", userId)
			.update();
		jdbcClient.sql("""
				DELETE FROM votes vote
				WHERE vote.user_id = :userId
				   OR EXISTS (
				       SELECT 1
				       FROM recommendations recommendation
				       WHERE recommendation.recommender_user_id = :userId
				         AND recommendation.track_id = vote.track_id
				         AND recommendation.recommended_on = vote.voted_on
				   )
				""")
			.param("userId", userId)
			.update();
	}

	private void deleteOrphanedTracks(List<UUID> trackIds) {
		if (trackIds.isEmpty()) {
			return;
		}
		List<UUID> orphanedTrackIds = jdbcClient.sql("""
				SELECT track.id
				FROM tracks track
				WHERE track.id IN (:trackIds)
				  AND NOT EXISTS (
				      SELECT 1 FROM recommendations recommendation
				      WHERE recommendation.track_id = track.id
				  )
				""")
			.param("trackIds", trackIds)
			.query(UUID.class)
			.list();
		if (orphanedTrackIds.isEmpty()) {
			return;
		}
		jdbcClient.sql("DELETE FROM daily_rankings WHERE track_id IN (:trackIds)")
			.param("trackIds", orphanedTrackIds)
			.update();
		jdbcClient.sql("DELETE FROM votes WHERE track_id IN (:trackIds)")
			.param("trackIds", orphanedTrackIds)
			.update();
		jdbcClient.sql("DELETE FROM track_provider_refs WHERE track_id IN (:trackIds)")
			.param("trackIds", orphanedTrackIds)
			.update();
		jdbcClient.sql("DELETE FROM track_genres WHERE track_id IN (:trackIds)")
			.param("trackIds", orphanedTrackIds)
			.update();
		jdbcClient.sql("DELETE FROM tracks WHERE id IN (:trackIds)")
			.param("trackIds", orphanedTrackIds)
			.update();
	}
}
