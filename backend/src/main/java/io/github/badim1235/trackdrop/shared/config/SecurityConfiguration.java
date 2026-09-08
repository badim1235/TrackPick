package io.github.badim1235.trackdrop.shared.config;

import io.github.badim1235.trackdrop.identity.AuthRateLimitFilter;
import io.github.badim1235.trackdrop.identity.RememberedSessionCookie;
import io.github.badim1235.trackdrop.identity.RememberedSessionFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
	private static final String CONTENT_SECURITY_POLICY = String.join("; ",
		"default-src 'self'",
		"script-src 'self'",
		"style-src 'self'",
		"font-src 'self'",
		"img-src 'self' data: https://*.mzstatic.com",
		"media-src 'self' https://*.itunes.apple.com",
		"connect-src 'self'",
		"object-src 'none'",
		"base-uri 'self'",
		"form-action 'self'",
		"frame-ancestors 'none'");

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	DefaultCookieSerializer cookieSerializer(
		@Value("${server.servlet.session.cookie.secure}") boolean secure
	) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName("TRACKDROP_SESSION");
		serializer.setCookiePath("/");
		serializer.setUseBase64Encoding(false);
		serializer.setSameSite("Lax");
		serializer.setUseHttpOnlyCookie(true);
		serializer.setUseSecureCookie(secure);
		return serializer;
	}

	@Bean
	SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		AuthRateLimitFilter authRateLimitFilter,
		RememberedSessionCookie rememberedSessionCookie,
		SecurityContextRepository securityContextRepository,
		@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
		@Value("${trackdrop.features.reports-enabled:true}") boolean reportsEnabled
	) throws Exception {
		CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrfRepository.setCookiePath("/");

		http
			.authorizeHttpRequests(authorize -> {
				authorize.requestMatchers(
					"/", "/error", "/index.html", "/chart", "/recent", "/recommend", "/login", "/join", "/me", "/activity", "/feedback", "/admin",
					"/tracks/*",
					"/recover/password", "/assets/**", "/favicon.ico", "/favicon-64.png",
					"/apple-touch-icon.png", "/trackpick-logo.png", "/og-image.png").permitAll();
				authorize.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll();
				authorize.requestMatchers(HttpMethod.GET, "/api/v1/genres").permitAll();
				authorize.requestMatchers(
					HttpMethod.GET, "/api/v1/home", "/api/v1/tracks/recent", "/api/v1/tracks/*").permitAll();
				authorize.requestMatchers(HttpMethod.GET, "/api/v1/charts/daily").permitAll();
				authorize.requestMatchers(HttpMethod.POST,
					"/api/v1/auth/sign-up", "/api/v1/auth/login",
					"/api/v1/auth/password-recovery", "/api/v1/auth/password-reset",
					"/api/v1/feedback").permitAll();
				authorize.requestMatchers(
					"/api/v1/system/health", "/actuator/health", "/actuator/info").permitAll();
				if (!reportsEnabled) {
					authorize.requestMatchers(
						HttpMethod.POST, "/api/v1/recommendations/*/reports").permitAll();
				}
				authorize.requestMatchers(request ->
					HttpMethod.GET.matches(request.getMethod())
						&& !request.getRequestURI().startsWith("/api/")
						&& !request.getRequestURI().startsWith("/actuator/")).permitAll();
				authorize.anyRequest().authenticated();
			})
			.csrf(csrf -> {
				csrf.csrfTokenRepository(csrfRepository);
				if (!reportsEnabled) {
					csrf.ignoringRequestMatchers("/api/v1/recommendations/*/reports");
				}
			})
			.requestCache(cache -> cache.disable())
			.headers(headers -> headers
				.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
				.referrerPolicy(referrer -> referrer.policy(
					ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
				.addHeaderWriter(new StaticHeadersWriter(
					"Permissions-Policy", "camera=(), geolocation=(), microphone=()")))
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, exception) -> writeAuthenticationError(
					request, response, handlerMapping))
				.accessDeniedHandler((request, response, exception) -> writeSecurityError(
					response, HttpStatus.FORBIDDEN.value(), "CSRF_TOKEN_INVALID", "요청을 다시 시도해 주세요.")))
			.logout(logout -> logout
				.logoutUrl("/api/v1/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			.addFilterBefore(authRateLimitFilter, CsrfFilter.class)
			.addFilterAfter(new RememberedSessionFilter(rememberedSessionCookie), SecurityContextHolderFilter.class);

		return http.build();
	}

	private static void writeAuthenticationError(
		HttpServletRequest request,
		HttpServletResponse response,
		RequestMappingHandlerMapping handlerMapping
	) throws java.io.IOException {
		if (isUnknownApiRequest(request, handlerMapping)) {
			writeSecurityError(response, HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "유효하지 않은 요청입니다.");
			return;
		}
		writeSecurityError(response, HttpStatus.UNAUTHORIZED.value(), "UNAUTHENTICATED", "로그인이 필요합니다.");
	}

	private static boolean isUnknownApiRequest(
		HttpServletRequest request,
		RequestMappingHandlerMapping handlerMapping
	) {
		if (!request.getRequestURI().startsWith("/api/")) {
			return false;
		}
		try {
			return handlerMapping.getHandler(request) == null;
		}
		catch (Exception ignored) {
			return false;
		}
	}

	private static void writeSecurityError(
		HttpServletResponse response,
		int status,
		String code,
		String message
	) throws java.io.IOException {
		response.setStatus(status);
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().printf(
			"{\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}",
			code,
			message);
	}
}
