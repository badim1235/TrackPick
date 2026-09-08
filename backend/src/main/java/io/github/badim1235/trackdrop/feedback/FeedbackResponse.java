package io.github.badim1235.trackdrop.feedback;

import io.github.badim1235.trackdrop.feedback.FeedbackRequest.Category;
import java.time.Instant;
import java.util.UUID;

public record FeedbackResponse(
	UUID id,
	Category category,
	Instant createdAt
) {
}
