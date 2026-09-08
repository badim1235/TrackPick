package io.github.badim1235.trackdrop.feedback;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {
	private final FeedbackService feedbackService;

	FeedbackController(FeedbackService feedbackService) {
		this.feedbackService = feedbackService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	FeedbackResponse create(@Valid @RequestBody FeedbackRequest request) {
		return feedbackService.create(request);
	}
}
