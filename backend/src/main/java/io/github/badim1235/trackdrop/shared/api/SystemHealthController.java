package io.github.badim1235.trackdrop.shared.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemHealthController {

	@GetMapping("/health")
	ResponseEntity<SystemHealthResponse> health() {
		return ResponseEntity.ok(new SystemHealthResponse("UP", "TrackPick"));
	}

	record SystemHealthResponse(String status, String service) {
	}
}
