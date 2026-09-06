package io.github.badim1235.trackdrop.identity;

import io.github.badim1235.trackdrop.shared.quota.DailyQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class AccountController {

	private final IdentityService identityService;
	private final DailyQuotaService quotaService;
	private final AccountDeletionService accountDeletionService;
	private final AuthSessionService authSessionService;

	public AccountController(
		IdentityService identityService,
		DailyQuotaService quotaService,
		AccountDeletionService accountDeletionService,
		AuthSessionService authSessionService
	) {
		this.identityService = identityService;
		this.quotaService = quotaService;
		this.accountDeletionService = accountDeletionService;
		this.authSessionService = authSessionService;
	}

	@GetMapping
	AccountResponse me(@AuthenticationPrincipal TrackDropPrincipal principal) {
		return AccountResponse.from(
			identityService.account(principal.userId()),
			quotaService.current(principal.userId()));
	}

	@DeleteMapping
	ResponseEntity<Void> deleteAccount(
		@AuthenticationPrincipal TrackDropPrincipal principal,
		@Valid @RequestBody DeleteAccountRequest body,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		String principalName = accountDeletionService.delete(principal.userId(), body.password());
		authSessionService.terminateAll(principalName, request, response);
		return ResponseEntity.noContent().build();
	}

	public record DeleteAccountRequest(
		@NotBlank(message = "현재 비밀번호를 입력해 주세요.") String password
	) {
	}
}
