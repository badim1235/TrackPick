package io.github.badim1235.trackdrop.shared.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {
	@GetMapping("/favicon.ico")
	String favicon() {
		return "redirect:/favicon-64.png";
	}

	@GetMapping({
		"/chart", "/recent", "/recommend", "/login", "/join", "/me",
		"/recover/id", "/recover/password", "/tracks/{trackId}"
	})
	String forwardToIndex() {
		return "forward:/index.html";
	}
}
