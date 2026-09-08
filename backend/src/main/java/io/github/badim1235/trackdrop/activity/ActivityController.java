package io.github.badim1235.trackdrop.activity;

import io.github.badim1235.trackdrop.identity.TrackDropPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/activity")
public class ActivityController {
	private final ActivityService activityService;

	ActivityController(ActivityService activityService) {
		this.activityService = activityService;
	}

	@GetMapping
	ActivityResponse activity(
		@RequestParam(required = false) String cursor,
		@AuthenticationPrincipal TrackDropPrincipal principal
	) {
		return activityService.get(principal.userId(), cursor);
	}
}
