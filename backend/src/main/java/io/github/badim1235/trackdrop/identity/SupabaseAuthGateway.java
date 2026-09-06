package io.github.badim1235.trackdrop.identity;

import java.time.Instant;
import java.util.UUID;

public interface SupabaseAuthGateway {

	void signUp(String email, String password);

	AuthenticatedUser signIn(String email, String password);

	void requestPasswordRecovery(String email);

	void updatePassword(String accessToken, String password);

	void deleteUser(UUID userId);

	record AuthenticatedUser(UUID id, String email, Instant emailVerifiedAt) {
	}
}
