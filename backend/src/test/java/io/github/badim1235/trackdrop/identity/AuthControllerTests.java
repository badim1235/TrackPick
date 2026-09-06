package io.github.badim1235.trackdrop.identity;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.badim1235.trackdrop.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@MockitoBean
	private SupabaseAuthGateway supabaseAuth;

	@MockitoBean
	private SupabaseUserDirectory supabaseUsers;

	@Test
	void signsUpThroughSupabaseAndRequiresEmailVerification() throws Exception {
		String email = uniqueEmail();

		mockMvc.perform(post("/api/v1/auth/sign-up")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"chatgpt5555"}
					""".formatted(email)))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.emailVerificationRequired").value(true));

		verify(supabaseAuth).signUp(email, "chatgpt5555");
	}

	@Test
	void rejectsAnEmailThatAlreadyExistsInSupabaseAuth() throws Exception {
		String email = uniqueEmail();
		when(supabaseUsers.existsByEmail(email)).thenReturn(true);

		mockMvc.perform(post("/api/v1/auth/sign-up")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"chatgpt5555"}
					""".formatted(email)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"))
			.andExpect(jsonPath("$.error.message").value("이미 존재하는 이메일입니다."));

		verifyNoInteractions(supabaseAuth);
	}

	@Test
	void rejectsInvalidEmailAndPasswordBeforeCallingSupabase() throws Exception {
		mockMvc.perform(post("/api/v1/auth/sign-up")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"not-an-email","password":"lettersOnly"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.error.message").value("비밀번호는 8~16자의 영문자와 숫자를 포함해야 합니다."));
	}

	@Test
	void logsInThroughSupabaseAndCreatesTheTrackDropProfile() throws Exception {
		String email = uniqueEmail();
		stubLogin(email);

		MvcResult result = login(email, false)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.account.email").value(email))
			.andExpect(jsonPath("$.account.publicNickname").isNotEmpty())
			.andExpect(jsonPath("$.account.emailVerified").value(true))
			.andExpect(jsonPath("$.quota.limit").value(4))
			.andReturn();

		Cookie sessionCookie = result.getResponse().getCookie("TRACKDROP_SESSION");
		mockMvc.perform(get("/api/v1/me").cookie(sessionCookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.account.email").value(email));
	}

	@Test
	void reconnectsALegacyLocalProfileAfterSuccessfulSupabaseLogin() throws Exception {
		String email = uniqueEmail();
		UUID legacyId = UUID.randomUUID();
		UUID supabaseId = UUID.randomUUID();
		String nickname = "연결복구" + UUID.randomUUID().toString().substring(0, 8);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		jdbcClient.sql("""
				INSERT INTO users (
					id, email, email_normalized, email_verified_at,
					public_nickname, status, created_at, updated_at
				)
				VALUES (:id, :email, :email, :now, :nickname, 'ACTIVE', :now, :now)
				""")
			.param("id", legacyId)
			.param("email", email)
			.param("nickname", nickname)
			.param("now", now)
			.update();
		jdbcClient.sql("""
				INSERT INTO daily_recommendation_quotas (
					user_id, quota_date, daily_limit, used_count, updated_at
				)
				VALUES (:userId, :today, 4, 1, :now)
				""")
			.param("userId", legacyId)
			.param("today", today)
			.param("now", now)
			.update();
		when(supabaseAuth.signIn(email, "chatgpt5555"))
			.thenReturn(new SupabaseAuthGateway.AuthenticatedUser(supabaseId, email, Instant.now()));

		login(email, false)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.account.publicNickname").value(nickname))
			.andExpect(jsonPath("$.quota.used").value(1));

		UUID storedUserId = jdbcClient.sql("SELECT id FROM users WHERE email_normalized = :email")
			.param("email", email)
			.query(UUID.class)
			.single();
		UUID quotaUserId = jdbcClient.sql("""
				SELECT user_id FROM daily_recommendation_quotas
				WHERE quota_date = :today AND user_id = :userId
				""")
			.param("today", today)
			.param("userId", supabaseId)
			.query(UUID.class)
			.single();
		org.assertj.core.api.Assertions.assertThat(storedUserId).isEqualTo(supabaseId);
		org.assertj.core.api.Assertions.assertThat(quotaUserId).isEqualTo(supabaseId);
	}

	@Test
	void doesNotTurnUnexpectedSignupFailuresIntoAuthenticationErrors() throws Exception {
		String email = uniqueEmail();
		when(supabaseUsers.existsByEmail(email)).thenThrow(new IllegalStateException("directory unavailable"));

		mockMvc.perform(post("/api/v1/auth/sign-up")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"chatgpt5555"}
					""".formatted(email)))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
			.andExpect(jsonPath("$.error.message").value("요청을 처리하지 못했습니다. 다시 시도해 주세요."));
	}

	@Test
	void keepsRememberedLoginForSevenDaysAndLogsOutCurrentSession() throws Exception {
		String email = uniqueEmail();
		stubLogin(email);

		MvcResult login = login(email, true)
			.andExpect(status().isOk())
			.andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(
				org.hamcrest.Matchers.allOf(
					containsString("TRACKDROP_SESSION="),
					containsString("Max-Age=604800"),
					containsString("HttpOnly"),
					containsString("SameSite=Lax")))))
			.andReturn();

		Cookie sessionCookie = login.getResponse().getCookie("TRACKDROP_SESSION");
		mockMvc.perform(post("/api/v1/auth/logout").cookie(sessionCookie).with(csrf()))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/me").cookie(sessionCookie))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void deletesTheAuthenticatedAccountAndInvalidatesItsSession() throws Exception {
		String email = uniqueEmail();
		UUID userId = UUID.randomUUID();
		when(supabaseAuth.signIn(email, "chatgpt5555"))
			.thenReturn(new SupabaseAuthGateway.AuthenticatedUser(userId, email, Instant.now()));

		MvcResult login = login(email, true).andExpect(status().isOk()).andReturn();
		Cookie sessionCookie = login.getResponse().getCookie("TRACKDROP_SESSION");

		mockMvc.perform(delete("/api/v1/me")
				.cookie(sessionCookie)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"password":"chatgpt5555"}
					"""))
			.andExpect(status().isNoContent())
			.andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(
				containsString("TRACKDROP_SESSION=;"))));

		verify(supabaseAuth).deleteUser(userId);
		org.assertj.core.api.Assertions.assertThat(
			jdbcClient.sql("SELECT count(*) FROM users WHERE id = :userId")
				.param("userId", userId)
				.query(Long.class)
				.single())
			.isZero();
		mockMvc.perform(get("/api/v1/me").cookie(sessionCookie))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void returnsGenericMessageForInvalidCredentials() throws Exception {
		String email = uniqueEmail();
		when(supabaseAuth.signIn(email, "wrongpass1"))
			.thenThrow(IdentityException.invalidCredentials());

		mockMvc.perform(post("/api/v1/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"wrongpass1","rememberMe":false}
					""".formatted(email)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
			.andExpect(jsonPath("$.error.message").value("로그인 정보를 확인해 주세요."));
	}

	@Test
	void requestsAPasswordRecoveryEmailThroughSupabase() throws Exception {
		String email = uniqueEmail();
		mockMvc.perform(post("/api/v1/auth/password-recovery")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s"}
					""".formatted(email)))
			.andExpect(status().isAccepted());

		verify(supabaseAuth).requestPasswordRecovery(email);
	}

	@Test
	void updatesThePasswordWithASupabaseRecoveryToken() throws Exception {
		mockMvc.perform(post("/api/v1/auth/password-reset")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"accessToken":"recovery-access-token","password":"changed5555"}
					"""))
			.andExpect(status().isNoContent());

		verify(supabaseAuth).updatePassword("recovery-access-token", "changed5555");
	}

	private void stubLogin(String email) {
		when(supabaseAuth.signIn(email, "chatgpt5555"))
			.thenReturn(new SupabaseAuthGateway.AuthenticatedUser(
				UUID.randomUUID(), email, Instant.now()));
	}

	private org.springframework.test.web.servlet.ResultActions login(String email, boolean rememberMe)
		throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email":"%s","password":"chatgpt5555","rememberMe":%s}
				""".formatted(email, rememberMe)));
	}

	private static String uniqueEmail() {
		return UUID.randomUUID().toString().substring(0, 8) + "@example.com";
	}
}
