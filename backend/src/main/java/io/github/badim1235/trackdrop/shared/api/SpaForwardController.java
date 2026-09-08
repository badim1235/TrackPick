package io.github.badim1235.trackdrop.shared.api;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {
	@GetMapping("/favicon.ico")
	String favicon() {
		return "redirect:/favicon-64.png";
	}

	@GetMapping({
		"/chart", "/recent", "/recommend", "/login", "/join", "/me", "/activity", "/feedback", "/admin",
		"/recover/password", "/tracks/{trackId}"
	})
	String forwardToIndex() {
		return "forward:/index.html";
	}

	@GetMapping({
		"/{path:(?!api|actuator|assets)[^\\.]+}",
		"/{path:(?!api|actuator|assets)[^\\.]+}/{*remaining}"
	})
	String forwardUnknownRoute(HttpServletResponse response) {
		response.setStatus(HttpStatus.NOT_FOUND.value());
		return "forward:/index.html";
	}
}
