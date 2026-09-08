package io.github.badim1235.trackdrop.feedback;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class FeedbackService {
	private final JdbcClient jdbcClient;
	private final Clock clock;

	FeedbackService(JdbcClient jdbcClient, Clock clock) {
		this.jdbcClient = jdbcClient;
		this.clock = clock;
	}

	@Transactional
	FeedbackResponse create(FeedbackRequest request) {
		UUID id = UUID.randomUUID();
		Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
		jdbcClient.sql("""
				INSERT INTO feedback_submissions (id, category, content, created_at)
				VALUES (:id, :category, :content, :createdAt)
				""")
			.param("id", id)
			.param("category", request.category().name())
			.param("content", request.content().strip())
			.param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
			.update();
		return new FeedbackResponse(id, request.category(), createdAt);
	}
}
