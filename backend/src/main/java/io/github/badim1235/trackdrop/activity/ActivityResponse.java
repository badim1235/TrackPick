package io.github.badim1235.trackdrop.activity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ActivityResponse(
	Instant asOf,
	Summary summary,
	List<ActivityItem> items,
	Page page
) {
	public record Summary(
		int recommendationCount,
		int firstPickCount,
		int receivedVoteCount,
		HighestVoted highestVoted
	) {
	}

	public record HighestVoted(
		UUID trackId,
		String title,
		String artistName,
		String albumCoverUrl,
		LocalDate recommendedOn,
		int voteCount
	) {
	}

	public record ActivityItem(
		UUID recommendationId,
		UUID trackId,
		String title,
		String artistName,
		String albumCoverUrl,
		LocalDate recommendedOn,
		Instant createdAt,
		String comment,
		int voteCount,
		boolean firstPick
	) {
	}

	public record Page(int size, boolean hasMore, String nextCursor) {
	}
}
