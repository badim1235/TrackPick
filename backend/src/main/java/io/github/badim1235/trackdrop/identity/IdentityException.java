package io.github.badim1235.trackdrop.identity;

public class IdentityException extends RuntimeException {

	private final String code;
	private final int status;

	public IdentityException(String code, String message, int status) {
		super(message);
		this.code = code;
		this.status = status;
	}

	public String getCode() {
		return code;
	}

	public int getStatus() {
		return status;
	}

	public static IdentityException emailTaken() {
		return new IdentityException("EMAIL_TAKEN", "이미 존재하는 이메일입니다.", 409);
	}

	public static IdentityException invalidCredentials() {
		return new IdentityException("INVALID_CREDENTIALS", "로그인 정보를 확인해 주세요.", 401);
	}

	public static IdentityException accountSuspended() {
		return new IdentityException("ACCOUNT_SUSPENDED", "사용이 제한된 계정입니다.", 403);
	}

	public static IdentityException authProviderUnavailable() {
		return new IdentityException(
			"AUTH_PROVIDER_UNAVAILABLE",
			"인증 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.",
			503);
	}

	public static IdentityException passwordUnchanged() {
		return new IdentityException(
			"PASSWORD_UNCHANGED",
			"이전 비밀번호와 다른 비밀번호를 입력해 주세요.",
			422);
	}

	public static IdentityException passwordRecoveryInvalid() {
		return new IdentityException(
			"PASSWORD_RECOVERY_INVALID",
			"비밀번호 재설정 링크가 만료되었거나 올바르지 않습니다.",
			401);
	}

	public static IdentityException accountDeletionUnavailable() {
		return new IdentityException(
			"ACCOUNT_DELETION_UNAVAILABLE",
			"회원 탈퇴를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
			503);
	}

	public static IdentityException rateLimited(long retryAfterSeconds) {
		return new RateLimitedIdentityException(
			"RATE_LIMITED",
			"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
			retryAfterSeconds);
	}

	public static IdentityException signupBlocked(long retryAfterSeconds) {
		return new SignupBlockedIdentityException(retryAfterSeconds);
	}

	public static final class RateLimitedIdentityException extends IdentityException {

		private final long retryAfterSeconds;

		private RateLimitedIdentityException(String code, String message, long retryAfterSeconds) {
			super(code, message, 429);
			this.retryAfterSeconds = retryAfterSeconds;
		}

		public long getRetryAfterSeconds() {
			return retryAfterSeconds;
		}
	}

	public static final class SignupBlockedIdentityException extends IdentityException {

		private final long retryAfterSeconds;

		private SignupBlockedIdentityException(long retryAfterSeconds) {
			super("RATE_LIMITED", "이 네트워크에서는 24시간 동안 새 계정을 만들 수 없습니다.", 429);
			this.retryAfterSeconds = retryAfterSeconds;
		}

		public long getRetryAfterSeconds() {
			return retryAfterSeconds;
		}
	}
}
