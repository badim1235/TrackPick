package io.github.badim1235.trackdrop.moderation;

import io.github.badim1235.trackdrop.identity.TrackDropPrincipal;
import io.github.badim1235.trackdrop.moderation.AdminReportResponse.FlaggedUsers;
import io.github.badim1235.trackdrop.moderation.AdminReportResponse.ModerationResult;
import io.github.badim1235.trackdrop.moderation.AdminReportResponse.UserReports;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/reports/users")
public class AdminReportController {

	private final AdminAccessPolicy adminAccessPolicy;
	private final AdminReportService adminReportService;

	AdminReportController(AdminAccessPolicy adminAccessPolicy, AdminReportService adminReportService) {
		this.adminAccessPolicy = adminAccessPolicy;
		this.adminReportService = adminReportService;
	}

	@GetMapping
	FlaggedUsers findFlaggedUsers(
		@AuthenticationPrincipal TrackDropPrincipal principal,
		@RequestParam(defaultValue = "")
		@Size(max = 100, message = "검색어는 100자 이내로 입력해 주세요.") String query
	) {
		adminAccessPolicy.requireAdmin(principal);
		return adminReportService.findFlaggedUsers(query);
	}

	@GetMapping("/{userId}")
	UserReports findUserReports(
		@AuthenticationPrincipal TrackDropPrincipal principal,
		@PathVariable UUID userId
	) {
		adminAccessPolicy.requireAdmin(principal);
		return adminReportService.findUserReports(userId);
	}

	@PatchMapping("/{userId}")
	ModerationResult moderate(
		@AuthenticationPrincipal TrackDropPrincipal principal,
		@PathVariable UUID userId,
		@Valid @RequestBody ModerationRequest request
	) {
		adminAccessPolicy.requireAdmin(principal);
		return adminReportService.moderate(principal.userId(), userId, request.action());
	}

	public record ModerationRequest(
		@NotNull(message = "신고 조치를 선택해 주세요.") ModerationAction action
	) {
	}

	public enum ModerationAction {
		DISMISS,
		BAN
	}
}
