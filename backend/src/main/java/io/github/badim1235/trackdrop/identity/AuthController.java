package io.github.badim1235.trackdrop.identity;

import io.github.badim1235.trackdrop.moderation.AdminAccessPolicy;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final IdentityService identityService;
	private final AuthSessionService authSessionService;
	private final SupabaseAuthGateway supabaseAuth;
	private final SupabaseUserDirectory supabaseUsers;
	private final AuthRateLimiter rateLimiter;
	private final ClientIpHasher ipHasher;
	private final DailyQuotaService quotaService;
	private final AdminAccessPolicy adminAccessPolicy;

	public AuthController(
		IdentityService identityService,
		AuthSessionService authSessionService,
		SupabaseAuthGateway supabaseAuth,
		SupabaseUserDirectory supabaseUsers,
		AuthRateLimiter rateLimiter,
		ClientIpHasher ipHasher,
		DailyQuotaService quotaService,
		AdminAccessPolicy adminAccessPolicy
	) {
		this.identityService = identityService;
		this.authSessionService = authSessionService;
		this.supabaseAuth = supabaseAuth;
		this.supabaseUsers = supabaseUsers;
		this.rateLimiter = rateLimiter;
		this.ipHasher = ipHasher;
		this.quotaService = quotaService;
		this.adminAccessPolicy = adminAccessPolicy;
	}

	@GetMapping("/csrf")
	CsrfResponse csrf(CsrfToken token) {
		return new CsrfResponse(token.getToken());
	}

	@PostMapping("/sign-up")
	ResponseEntity<SignUpResponse> signUp(
		@Valid @RequestBody SignUpRequest body,
		HttpServletRequest request
	) {
		String ipHash = ipHasher.hash(request.getRemoteAddr());
		rateLimiter.lockAndCheckSignup(ipHash);
		String email = IdentityNormalizer.email(body.email());
		if (supabaseUsers.existsByEmail(email)) {
			throw IdentityException.emailTaken();
		}
		supabaseAuth.signUp(email, body.password());
		rateLimiter.recordSuccessfulSignup(ipHash);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(new SignUpResponse(true));
	}

	@PostMapping("/login")
	AccountResponse login(
		@Valid @RequestBody LoginRequest body,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		SupabaseAuthGateway.AuthenticatedUser authenticatedUser = supabaseAuth.signIn(
			IdentityNormalizer.email(body.email()), body.password());
		UserAccount account = identityService.provision(authenticatedUser);
		TrackDropPrincipal principal = authSessionService.establish(
			account, body.rememberMe(), request, response);
		return AccountResponse.from(
			identityService.account(principal.userId()),
			quotaService.current(principal.userId()),
			adminAccessPolicy.isAdmin(principal));
	}

	@PostMapping("/password-recovery")
	ResponseEntity<Void> passwordRecovery(@Valid @RequestBody PasswordRecoveryRequest body) {
		supabaseAuth.requestPasswordRecovery(IdentityNormalizer.email(body.email()));
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/password-reset")
	ResponseEntity<Void> passwordReset(@Valid @RequestBody PasswordResetRequest body) {
		supabaseAuth.updatePassword(body.accessToken(), body.password());
		return ResponseEntity.noContent().build();
	}

	public record CsrfResponse(String token) {
	}

	public record SignUpResponse(boolean emailVerificationRequired) {
	}

	public record SignUpRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Size(max = 320, message = "올바른 이메일을 입력해 주세요.")
		@Email(message = "올바른 이메일을 입력해 주세요.")
		String email,
		@ValidPassword String password
	) {
	}

	public record LoginRequest(
		@NotBlank @Email String email,
		@NotBlank String password,
		boolean rememberMe
	) {
	}

	public record PasswordRecoveryRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "올바른 이메일을 입력해 주세요.")
		String email
	) {
	}

	public record PasswordResetRequest(
		@NotBlank(message = "비밀번호 재설정 링크가 올바르지 않습니다.") String accessToken,
		@ValidPassword String password
	) {
	}
}
