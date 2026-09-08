package io.github.badim1235.trackdrop.shared.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class SystemHealthControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsPublicHealthStatus() throws Exception {
		mockMvc.perform(get("/api/v1/system/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.service").value("TrackPick"))
			.andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")))
			.andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
			.andExpect(header().string("Permissions-Policy", "camera=(), geolocation=(), microphone=()"));
	}
}
