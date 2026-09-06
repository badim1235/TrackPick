package io.github.badim1235.trackdrop.identity;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RememberedSessionCookie {

	public static final String REMEMBERED_ATTRIBUTE = "trackdrop.remembered";

	private final Duration maxAge;
	private final boolean secure;

	public RememberedSessionCookie(
		@Value("${trackdrop.security.remember-me-seconds}") long rememberMeSeconds,
		@Value("${server.servlet.session.cookie.secure}") boolean secure
	) {
		this.maxAge = Duration.ofSeconds(rememberMeSeconds);
		this.secure = secure;
	}

	public void refresh(HttpServletResponse response, HttpSession session) {
		ResponseCookie cookie = ResponseCookie.from("TRACKDROP_SESSION", session.getId())
			.httpOnly(true)
			.secure(secure)
			.sameSite("Lax")
			.path("/")
			.maxAge(maxAge)
			.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public void clear(HttpServletResponse response) {
		ResponseCookie cookie = ResponseCookie.from("TRACKDROP_SESSION", "")
			.httpOnly(true)
			.secure(secure)
			.sameSite("Lax")
			.path("/")
			.maxAge(Duration.ZERO)
			.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}
}
