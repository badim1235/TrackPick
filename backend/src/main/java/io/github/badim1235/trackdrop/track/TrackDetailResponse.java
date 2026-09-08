package io.github.badim1235.trackdrop.track;

import io.github.badim1235.trackdrop.catalog.GenreResponse.Genre;
import io.github.badim1235.trackdrop.catalog.MusicProvider;
import io.github.badim1235.trackdrop.shared.quota.DailyQuotaSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TrackDetailResponse(
	Track track,
	Today today,
	DailyQuotaSnapshot quota,
	Actions actions
) {
	public record Track(
		UUID id,
		String title,
		String artistName,
		String albumName,
		String albumCoverUrl,
		Integer releaseYear,
		String isrc,
		boolean explicit,
		String providerGenreName,
		Genre primaryGenre,
		List<Genre> genres,
		Recommendation recommendation,
		Recommendation latestRecommendation,
		Viewer viewer,
		Preview preview,
		List<ProviderReference> providerReferences
	) {
	}

	public record Recommendation(
		UUID id,
		String comment,
		boolean commentAvailable,
		String recommenderNickname,
		Instant createdAt,
		boolean canReport,
		boolean hasReported
	) {
	}

	public record Viewer(boolean hasVotedToday) {
	}

	public record Preview(
		boolean available,
		MusicProvider provider,
		String kind,
		String startPosition,
		String url
	) {
	}

	public record ProviderReference(
		MusicProvider provider,
		String externalTrackId,
		String externalUrl,
		Instant metadataRefreshedAt
	) {
	}

	public record Today(
		int voteCount,
		Long overallRank,
		Long genreRank,
		Instant asOf
	) {
	}

	public record Actions(
		boolean canVote,
		boolean canRecommend,
		String reason,
		LocalDate recommendationAvailableOn,
		boolean canReport,
		boolean hasReported
	) {
	}
}
