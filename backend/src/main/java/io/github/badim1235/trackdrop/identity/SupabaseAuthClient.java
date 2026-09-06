package io.github.badim1235.trackdrop.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.Duration;
import java.net.http.HttpClient;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class SupabaseAuthClient implements SupabaseAuthGateway {

	private final RestClient restClient;
	private final RestClient adminRestClient;
	private final ObjectMapper objectMapper;
	private final SupabaseAuthProperties properties;

	@Autowired
	public SupabaseAuthClient(
		ObjectMapper objectMapper,
		SupabaseAuthProperties properties
	) {
		this(createRestClient(properties), createAdminRestClient(properties), objectMapper, properties);
	}

	SupabaseAuthClient(
		RestClient restClient,
		RestClient adminRestClient,
		ObjectMapper objectMapper,
		SupabaseAuthProperties properties
	) {
		this.restClient = restClient;
		this.adminRestClient = adminRestClient;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	private static RestClient createAdminRestClient(SupabaseAuthProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(3))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(5));
		return RestClient.builder()
			.baseUrl(properties.url())
			.requestFactory(requestFactory)
			.build();
	}

	private static RestClient createRestClient(SupabaseAuthProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(3))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(5));
		return RestClient.builder()
			.baseUrl(properties.url())
			.requestFactory(requestFactory)
			.defaultHeader("apikey", properties.publishableKey())
			.build();
	}

	@Override
	public void signUp(String email, String password) {
		invoke(
			"/auth/v1/signup?redirect_to={redirectUrl}",
			new Credentials(email, password),
			properties.emailRedirectUrl(),
			false);
	}

	@Override
	public AuthenticatedUser signIn(String email, String password) {
		AuthResponse response = invoke(
			"/auth/v1/token?grant_type=password",
			new Credentials(email, password),
			null,
			true);
		if (response == null || response.user() == null) {
			throw IdentityException.invalidCredentials();
		}
		AuthUser user = response.user();
		return new AuthenticatedUser(
			UUID.fromString(user.id()),
			IdentityNormalizer.email(user.email()),
			user.emailConfirmedAt() == null ? Instant.now() : user.emailConfirmedAt());
	}

	@Override
	public void requestPasswordRecovery(String email) {
		invoke(
			"/auth/v1/recover?redirect_to={redirectUrl}",
			new RecoveryRequest(email),
			properties.passwordRecoveryRedirectUrl(),
			false);
	}

	@Override
	public void updatePassword(String accessToken, String password) {
		try {
			restClient.put()
				.uri("/auth/v1/user")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.body(new PasswordUpdateRequest(password))
				.retrieve()
				.onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
					JsonNode error = objectMapper.readTree(response.getBody());
					String code = text(error, "error_code", "code");
					if ("same_password".equals(code)) {
						throw IdentityException.passwordUnchanged();
					}
					throw IdentityException.passwordRecoveryInvalid();
				})
				.toBodilessEntity();
		}
		catch (IdentityException exception) {
			throw exception;
		}
		catch (RestClientException exception) {
			throw IdentityException.authProviderUnavailable();
		}
	}

	@Override
	public void deleteUser(UUID userId) {
		if (properties.secretKey() == null || properties.secretKey().isBlank()) {
			throw IdentityException.accountDeletionUnavailable();
		}
		try {
			adminRestClient.delete()
				.uri("/auth/v1/admin/users/{userId}", userId)
				.header("apikey", properties.secretKey())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.secretKey())
				.retrieve()
				.onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
					throw IdentityException.accountDeletionUnavailable();
				})
				.toBodilessEntity();
		}
		catch (IdentityException exception) {
			throw exception;
		}
		catch (RestClientException exception) {
			throw IdentityException.accountDeletionUnavailable();
		}
	}

	private AuthResponse invoke(
		String uri,
		Object body,
		String redirectUrl,
		boolean login
	) {
		try {
			RestClient.RequestBodySpec request = redirectUrl == null
				? restClient.post().uri(uri)
				: restClient.post().uri(uri, redirectUrl);
			return request
				.body(body)
				.retrieve()
				.onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
					JsonNode error = objectMapper.readTree(response.getBody());
					String code = text(error, "error_code", "code");
					String message = text(error, "msg", "message", "error_description");
					if (login || "email_not_confirmed".equals(code)
						|| message.toLowerCase().contains("invalid login credentials")) {
						throw IdentityException.invalidCredentials();
					}
					if (response.getStatusCode().value() == 429) {
						throw IdentityException.rateLimited(60);
					}
					throw new IdentityException(
						"AUTH_PROVIDER_REJECTED",
						"인증 요청을 처리하지 못했습니다. 입력 내용을 확인해 주세요.",
						response.getStatusCode().value());
				})
				.body(AuthResponse.class);
		}
		catch (IdentityException exception) {
			throw exception;
		}
		catch (RestClientException exception) {
			throw IdentityException.authProviderUnavailable();
		}
	}

	private static String text(JsonNode node, String... fields) {
		for (String field : fields) {
			if (node.hasNonNull(field)) {
				return node.get(field).asText("");
			}
		}
		return "";
	}

	private record Credentials(String email, String password) {
	}

	private record RecoveryRequest(String email) {
	}

	private record PasswordUpdateRequest(String password) {
	}

	private record AuthResponse(AuthUser user) {
	}

	private record AuthUser(
		String id,
		String email,
		@JsonProperty("email_confirmed_at") Instant emailConfirmedAt
	) {
	}
}
