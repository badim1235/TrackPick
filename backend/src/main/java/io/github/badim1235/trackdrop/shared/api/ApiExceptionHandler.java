package io.github.badim1235.trackdrop.shared.api;

import io.github.badim1235.trackdrop.catalog.MusicSearchException;
import io.github.badim1235.trackdrop.chart.DailyChartException;
import io.github.badim1235.trackdrop.home.HomeFeedException;
import io.github.badim1235.trackdrop.identity.IdentityException;
import io.github.badim1235.trackdrop.identity.IdentityException.RateLimitedIdentityException;
import io.github.badim1235.trackdrop.identity.IdentityException.SignupBlockedIdentityException;
import io.github.badim1235.trackdrop.moderation.ReportException;
import io.github.badim1235.trackdrop.recommendation.RecommendationException;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaExceededException;
import io.github.badim1235.trackdrop.track.TrackDetailException;
import io.github.badim1235.trackdrop.vote.VoteException;
import java.util.Comparator;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);
	private static final Map<String, Integer> VALIDATION_FIELD_PRIORITY = Map.of(
		"password", 0,
		"email", 1);

	@ExceptionHandler(IdentityException.class)
	ResponseEntity<ApiErrorResponse> identity(IdentityException exception) {
		ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.getStatus());
		if (exception instanceof RateLimitedIdentityException rateLimited) {
			response.header(HttpHeaders.RETRY_AFTER, Long.toString(rateLimited.getRetryAfterSeconds()));
		}
		if (exception instanceof SignupBlockedIdentityException blocked) {
			response.header(HttpHeaders.RETRY_AFTER, Long.toString(blocked.getRetryAfterSeconds()));
		}
		return response.body(new ApiErrorResponse(new ApiError(
			exception.getCode(), exception.getMessage(), Map.of())));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
			.min(Comparator.comparingInt(error ->
				VALIDATION_FIELD_PRIORITY.getOrDefault(error.getField(), Integer.MAX_VALUE)))
			.map(error -> error.getDefaultMessage() == null ? "입력값을 확인해 주세요." : error.getDefaultMessage())
			.orElse("입력값을 확인해 주세요.");
		return ResponseEntity.badRequest().body(new ApiErrorResponse(
			new ApiError("VALIDATION_FAILED", message, Map.of())));
	}

	@ExceptionHandler(MusicSearchException.class)
	ResponseEntity<ApiErrorResponse> musicSearch(MusicSearchException exception) {
		ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.getStatus());
		if (exception.getRetryAfterSeconds() != null) {
			response.header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()));
		}
		return response.body(new ApiErrorResponse(new ApiError(
			exception.getCode(), exception.getMessage(), exception.getDetails())));
	}

	@ExceptionHandler(RecommendationException.class)
	ResponseEntity<ApiErrorResponse> recommendation(RecommendationException exception) {
		return ResponseEntity.status(exception.getStatus()).body(new ApiErrorResponse(new ApiError(
			exception.getCode(), exception.getMessage(), exception.getDetails())));
	}

	@ExceptionHandler(DailyQuotaExceededException.class)
	ResponseEntity<ApiErrorResponse> dailyQuota(DailyQuotaExceededException exception) {
		return ResponseEntity.status(429).body(new ApiErrorResponse(new ApiError(
			"DAILY_LIMIT_EXCEEDED",
			exception.getMessage(),
			Map.of("quota", exception.getQuota()))));
	}

	@ExceptionHandler(VoteException.class)
	ResponseEntity<ApiErrorResponse> vote(VoteException exception) {
		return ResponseEntity.status(exception.getStatus()).body(new ApiErrorResponse(new ApiError(
			exception.getCode(), exception.getMessage(), exception.getDetails())));
	}

	@ExceptionHandler(DailyChartException.class)
	ResponseEntity<ApiErrorResponse> dailyChart(DailyChartException exception) {
		return ResponseEntity.status(exception.getStatus()).body(new ApiErrorResponse(new ApiError(
			exception.getCode(), exception.getMessage(), Map.of())));
	}

	@ExceptionHandler(HomeFeedException.class)
	ResponseEntity<ApiErrorResponse> homeFeed(HomeFeedException exception) {
		return ResponseEntity.status(exception.getStatus()).body(new ApiErrorResponse(new ApiError(
			exception.getCode(), exception.getMessage(), Map.of())));
	}

	@ExceptionHandler(TrackDetailException.class)
	ResponseEntity<ApiErrorResponse> trackDetail(TrackDetailException exception) {
		return ResponseEntity.status(exception.getStatus()).body(new ApiErrorResponse(new ApiError(
			exception.getCode(), exception.getMessage(), Map.of())));
	}

	@ExceptionHandler(ReportException.class)
	ResponseEntity<ApiErrorResponse> report(ReportException exception) {
		return ResponseEntity.status(exception.getStatus()).body(new ApiErrorResponse(new ApiError(
			exception.getCode(), exception.getMessage(), Map.of())));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiErrorResponse> notFound() {
		return ResponseEntity.status(404).body(new ApiErrorResponse(new ApiError(
			"NOT_FOUND", "유효하지 않은 요청입니다.", Map.of())));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> unexpected(Exception exception) {
		LOGGER.error("Unexpected API error", exception);
		return ResponseEntity.internalServerError().body(new ApiErrorResponse(new ApiError(
			"INTERNAL_SERVER_ERROR",
			"요청을 처리하지 못했습니다. 다시 시도해 주세요.",
			Map.of())));
	}

	public record ApiErrorResponse(ApiError error) {
	}

	public record ApiError(String code, String message, Map<String, Object> details) {
	}
}
