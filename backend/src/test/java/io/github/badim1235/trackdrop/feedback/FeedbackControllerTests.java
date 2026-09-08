package io.github.badim1235.trackdrop.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.badim1235.trackdrop.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedbackControllerTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void acceptsAnonymousFeedbackWithoutStoringIdentity() throws Exception {
		mockMvc.perform(post("/api/v1/feedback")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"category":"UI_USABILITY","content":"  차트 날짜 선택이 더 잘 보이면 좋겠습니다.  "}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.category").value("UI_USABILITY"))
			.andExpect(jsonPath("$.createdAt").isNotEmpty());

		FeedbackRow feedback = jdbcClient.sql("""
				SELECT category, content FROM feedback_submissions
				""")
			.query((row, rowNumber) -> new FeedbackRow(
				row.getString("category"),
				row.getString("content")))
			.single();
		assertThat(feedback.category()).isEqualTo("UI_USABILITY");
		assertThat(feedback.content()).isEqualTo("차트 날짜 선택이 더 잘 보이면 좋겠습니다.");
	}

	@Test
	void rejectsMissingCategoryAndBlankContent() throws Exception {
		mockMvc.perform(post("/api/v1/feedback")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"content":"   "}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

		assertThat(jdbcClient.sql("SELECT COUNT(*) FROM feedback_submissions")
			.query(Integer.class)
			.single()).isZero();
	}

	private record FeedbackRow(String category, String content) {
	}
}
