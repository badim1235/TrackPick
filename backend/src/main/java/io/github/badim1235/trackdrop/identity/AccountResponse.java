package io.github.badim1235.trackdrop.identity;

import java.time.Instant;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaSnapshot;

public record AccountResponse(Account account, DailyQuotaSnapshot quota) {

	public static AccountResponse from(UserAccount user, DailyQuotaSnapshot quota, boolean admin) {
		return new AccountResponse(
			new Account(
				user.getEmail(),
				user.getPublicNickname(),
				user.getEmailVerifiedAt() != null,
				user.getCreatedAt(),
				admin),
			quota);
	}

	public record Account(
		String email,
		String publicNickname,
		boolean emailVerified,
		Instant createdAt,
		boolean admin
	) {
	}

}
