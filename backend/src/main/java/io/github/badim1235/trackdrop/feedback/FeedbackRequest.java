package io.github.badim1235.trackdrop.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(
	@NotNull(message = "의견 분류를 선택해 주세요.") Category category,
	@NotBlank(message = "의견을 입력해 주세요.")
	@Size(max = 2000, message = "의견은 2,000자 이내로 입력해 주세요.") String content
) {
	public enum Category {
		ERROR,
		UI_USABILITY,
		OTHER
	}
}
