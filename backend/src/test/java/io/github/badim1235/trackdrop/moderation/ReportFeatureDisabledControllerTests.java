package io.github.badim1235.trackdrop.moderation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.badim1235.trackdrop.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "trackdrop.features.reports-enabled=false")
@AutoConfigureMockMvc
class ReportFeatureDisabledControllerTests {
	@Autowired
	private MockMvc mockMvc;

	@Test
	void hidesTheReportEndpointWhenTheFeatureIsDisabled() throws Exception {
		mockMvc.perform(post("/api/v1/recommendations/{recommendationId}/reports", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"reasonCode":"ABUSIVE_LANGUAGE","details":"신고 설명"}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}
}
