package io.github.badim1235.trackdrop.shared.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.badim1235.trackdrop.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SpaForwardControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void forwardsAnUnknownTopLevelPageToTheSpaWithNotFoundStatus() throws Exception {
		mockMvc.perform(get("/missing-page"))
			.andExpect(status().isNotFound())
			.andExpect(forwardedUrl("/index.html"));
	}

	@Test
	void forwardsAnUnknownNestedPageToTheSpaWithNotFoundStatus() throws Exception {
		mockMvc.perform(get("/missing/nested/page"))
			.andExpect(status().isNotFound())
			.andExpect(forwardedUrl("/index.html"));
	}

	@Test
	void keepsUnknownApiRoutesBehindAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/missing"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void returnsNotFoundForAnUnknownStaticResource() throws Exception {
		mockMvc.perform(get("/missing.png"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.error.message").value("유효하지 않은 요청입니다."));
	}
}
