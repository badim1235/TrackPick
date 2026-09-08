package io.github.badim1235.trackdrop.activity;

import org.springframework.http.HttpStatus;

public class ActivityException extends RuntimeException {
	private final String code;
	private final HttpStatus status;

	private ActivityException(String code, String message, HttpStatus status, Throwable cause) {
		super(message, cause);
		this.code = code;
		this.status = status;
	}

	static ActivityException invalidCursor(Throwable cause) {
		return new ActivityException(
			"INVALID_CURSOR",
			"더 보기 정보를 확인할 수 없습니다. 처음부터 다시 불러와 주세요.",
			HttpStatus.BAD_REQUEST,
			cause);
	}

	public String getCode() {
		return code;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
