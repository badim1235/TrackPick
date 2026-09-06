package io.github.badim1235.trackdrop.moderation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdminReportResponse {

	private AdminReportResponse() {
	}

	public record FlaggedUsers(List<FlaggedUser> items) {
	}

	public record FlaggedUser(
		UUID id,
		String email,
		String publicNickname,
		String activity,
		int pendingReportCount,
		Instant firstReportedAt,
		Instant latestReportedAt
	) {
	}

	public record UserReports(ReportedUser user, List<ReportRecord> reports) {
	}

	public record ReportedUser(
		UUID id,
		String email,
		String publicNickname,
		String activity,
		int pendingReportCount
	) {
	}

	public record ReportRecord(
		UUID id,
		String reasonCode,
		String details,
		String status,
		Instant createdAt,
		Reporter reporter,
		Recommendation recommendation
	) {
	}

	public record Reporter(UUID id, String publicNickname) {
	}

	public record Recommendation(
		UUID id,
		String comment,
		UUID trackId,
		String trackTitle,
		String artistName
	) {
	}

	public record ModerationResult(
		UUID userId,
		String activity,
		String accountStatus,
		int resolvedReportCount,
		Instant resolvedAt
	) {
	}
}
