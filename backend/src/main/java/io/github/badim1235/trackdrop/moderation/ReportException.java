package io.github.badim1235.trackdrop.moderation;

import org.springframework.http.HttpStatus;

public class ReportException extends RuntimeException {
	private final String code;
	private final HttpStatus status;

	private ReportException(
		String code,
		String message,
		HttpStatus status,
		Throwable cause
	) {
		super(message, cause);
		this.code = code;
		this.status = status;
	}

	static ReportException featureDisabled() {
		return new ReportException("NOT_FOUND", "요청한 기능을 찾을 수 없습니다.", HttpStatus.NOT_FOUND, null);
	}

	static ReportException recommendationNotFound() {
		return new ReportException(
			"RECOMMENDATION_NOT_FOUND",
			"신고할 한줄평을 찾을 수 없습니다.",
			HttpStatus.NOT_FOUND,
			null);
	}

	static ReportException selfReportNotAllowed() {
		return new ReportException(
			"SELF_REPORT_NOT_ALLOWED",
			"자신이 작성한 한줄평은 신고할 수 없습니다.",
			HttpStatus.FORBIDDEN,
			null);
	}

	static ReportException alreadyReported(Throwable cause) {
		return new ReportException(
			"ALREADY_REPORTED",
			"이미 신고한 한줄평입니다.",
			HttpStatus.CONFLICT,
			cause);
	}

	static ReportException reportedUserBanned() {
		return new ReportException(
			"REPORTED_USER_BANNED",
			"이미 이용이 제한된 사용자의 한줄평입니다.",
			HttpStatus.CONFLICT,
			null);
	}

	static ReportException adminOnly() {
		return new ReportException(
			"NOT_FOUND",
			"유효하지 않은 요청입니다.",
			HttpStatus.NOT_FOUND,
			null);
	}

	static ReportException userNotFound() {
		return new ReportException(
			"USER_NOT_FOUND",
			"사용자를 찾을 수 없습니다.",
			HttpStatus.NOT_FOUND,
			null);
	}

	static ReportException userNotFlagged() {
		return new ReportException(
			"USER_NOT_FLAGGED",
			"현재 신고 조치 대상이 아닌 사용자입니다.",
			HttpStatus.CONFLICT,
			null);
	}

	static ReportException selfModerationNotAllowed() {
		return new ReportException(
			"SELF_MODERATION_NOT_ALLOWED",
			"관리자 본인의 계정은 이 화면에서 조치할 수 없습니다.",
			HttpStatus.FORBIDDEN,
			null);
	}

	public String getCode() {
		return code;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
