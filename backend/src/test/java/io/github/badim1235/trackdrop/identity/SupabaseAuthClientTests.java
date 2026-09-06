package io.github.badim1235.trackdrop.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class SupabaseAuthClientTests {

	@Test
	void distinguishesTheCurrentPasswordFromAnExpiredRecoveryToken() {
		TestClient testClient = testClient();
		testClient.server().expect(requestTo("https://project.supabase.co/auth/v1/user"))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer recovery-token"))
			.andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"code\":\"same_password\",\"message\":\"New password should be different from the old password.\"}"));

		IdentityException exception = catchThrowableOfType(
			() -> testClient.client().updatePassword("recovery-token", "chatgpt5555"),
			IdentityException.class);

		assertThat(exception.getCode()).isEqualTo("PASSWORD_UNCHANGED");
		assertThat(exception.getMessage()).isEqualTo("이전 비밀번호와 다른 비밀번호를 입력해 주세요.");
		assertThat(exception.getStatus()).isEqualTo(422);
		testClient.server().verify();
	}

	@Test
	void keepsInvalidRecoveryTokensSeparateFromPasswordReuse() {
		TestClient testClient = testClient();
		testClient.server().expect(requestTo("https://project.supabase.co/auth/v1/user"))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"code\":\"bad_jwt\",\"message\":\"Invalid JWT\"}"));

		IdentityException exception = catchThrowableOfType(
			() -> testClient.client().updatePassword("expired-token", "changed5555"),
			IdentityException.class);

		assertThat(exception.getCode()).isEqualTo("PASSWORD_RECOVERY_INVALID");
		assertThat(exception.getMessage()).isEqualTo("비밀번호 재설정 링크가 만료되었거나 올바르지 않습니다.");
		assertThat(exception.getStatus()).isEqualTo(401);
		testClient.server().verify();
	}

	@Test
	void deletesAUserThroughTheServerOnlyAdminApi() {
		TestClient testClient = testClient();
		String userId = "715ed5db-f090-4b8c-a067-640ecee36aa0";
		testClient.adminServer().expect(requestTo(
				"https://project.supabase.co/auth/v1/admin/users/" + userId))
			.andExpect(method(HttpMethod.DELETE))
			.andExpect(header("apikey", "secret-key"))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret-key"))
			.andRespond(withStatus(HttpStatus.OK));

		testClient.client().deleteUser(java.util.UUID.fromString(userId));

		testClient.adminServer().verify();
	}

	private static TestClient testClient() {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl("https://project.supabase.co")
			.defaultHeader("apikey", "publishable-key");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClient.Builder adminBuilder = RestClient.builder()
			.baseUrl("https://project.supabase.co");
		MockRestServiceServer adminServer = MockRestServiceServer.bindTo(adminBuilder).build();
		SupabaseAuthProperties properties = new SupabaseAuthProperties(
			"https://project.supabase.co",
			"publishable-key",
			"secret-key",
			"https://trackpick.net/login?verified=1",
			"https://trackpick.net/recover/password");
		return new TestClient(
			new SupabaseAuthClient(
				builder.build(), adminBuilder.build(), new ObjectMapper(), properties),
			server,
			adminServer);
	}

	private record TestClient(
		SupabaseAuthClient client,
		MockRestServiceServer server,
		MockRestServiceServer adminServer
	) {
	}
}
