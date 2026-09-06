package io.github.badim1235.trackdrop.shared.config;

import io.github.badim1235.trackdrop.identity.AuthRateLimitFilter;
import io.github.badim1235.trackdrop.identity.RememberedSessionCookie;
import io.github.badim1235.trackdrop.identity.RememberedSessionFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

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
		@Value("${trackdrop.features.reports-enabled:true}") boolean reportsEnabled
	) throws Exception {
		CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrfRepository.setCookiePath("/");

		http
			.authorizeHttpRequests(authorize -> {
				authorize.requestMatchers(
					"/", "/error", "/index.html", "/chart", "/recent", "/recommend", "/login", "/join", "/me", "/admin",
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
					"/api/v1/auth/password-recovery", "/api/v1/auth/password-reset").permitAll();
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
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, exception) -> writeSecurityError(
					response, HttpStatus.UNAUTHORIZED.value(), "UNAUTHENTICATED", "로그인이 필요합니다."))
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
