package io.github.badim1235.trackdrop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class AppleItunesClientTests {

	@Test
	void searchesTheKrStorefrontAndMapsExplicitSongs() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://itunes.apple.com");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AppleItunesClient client = new AppleItunesClient(
			builder.build(),
			properties(),
			new ProviderCallRateLimiter(15, Clock.systemUTC()),
			new ObjectMapper());

		server.expect(method(HttpMethod.GET))
			.andExpect(queryParam("term", "Radiohead"))
			.andExpect(queryParam("country", "KR"))
			.andExpect(queryParam("media", "music"))
			.andExpect(queryParam("entity", "song"))
			.andExpect(queryParam("limit", "20"))
			.andExpect(queryParam("explicit", "Yes"))
			.andExpect(queryParam("lang", "en_us"))
			.andRespond(withSuccess("""
				{
				  "resultCount": 2,
				  "results": [
				    {
				      "kind": "song",
				      "trackId": 1234,
				      "trackName": "Creep",
				      "artistName": "Radiohead",
				      "collectionName": "Pablo Honey",
				      "artworkUrl100": "https://example.com/cover.jpg",
				      "trackViewUrl": "https://music.apple.com/kr/album/creep/1234",
				      "previewUrl": "https://example.com/preview.m4a",
					  "trackExplicitness": "explicit",
					  "primaryGenreName": "Alternative",
				      "releaseDate": "1993-02-22T12:00:00Z"
				    },
				    {
				      "kind": "music-video",
				      "trackId": 5678,
				      "trackName": "Creep (Video)",
				      "artistName": "Radiohead"
				    }
				  ]
				}
				""", MediaType.parseMediaType("text/javascript;charset=utf-8")));

		var tracks = client.search("Radiohead");

		assertThat(tracks).hasSize(1);
		assertThat(tracks.getFirst())
			.extracting(
				MusicCatalogTrack::externalTrackId,
				MusicCatalogTrack::title,
				MusicCatalogTrack::explicit,
				MusicCatalogTrack::primaryGenreName,
				MusicCatalogTrack::releaseYear)
			.containsExactly("1234", "Creep", true, "Alternative", 1993);
		server.verify();
	}

	@Test
	void percentEncodesPlusSignsInsteadOfSendingThemAsSpaces() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://itunes.apple.com");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AppleItunesClient client = new AppleItunesClient(
			builder.build(),
			properties(),
			new ProviderCallRateLimiter(15, Clock.systemUTC()),
			new ObjectMapper());

		server.expect(requestTo(
			"https://itunes.apple.com/search?term=0%2B0&country=KR&media=music&entity=song&limit=20&explicit=Yes&lang=en_us"))
			.andRespond(withSuccess("""
				{"results":[{
				  "kind":"song",
				  "trackId":1857589963,
				  "trackName":"0+0",
				  "artistName":"한로로"
				}]}
				""", MediaType.parseMediaType("text/javascript;charset=utf-8")));

		var tracks = client.search("0+0");

		assertThat(tracks).singleElement().satisfies(track -> {
			assertThat(track.title()).isEqualTo("0+0");
			assertThat(track.artistName()).isEqualTo("한로로");
		});
		server.verify();
	}

	@Test
	void sendsBrowserCompatibleHeaderAndPercentEncodesKoreanSearchTerms() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://itunes.apple.com");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AppleItunesClient client = new AppleItunesClient(
			builder.build(),
			properties(),
			new ProviderCallRateLimiter(15, Clock.systemUTC()),
			new ObjectMapper());

		server.expect(requestTo(
			"https://itunes.apple.com/search?term=%ED%95%9C%EB%A1%9C%EB%A1%9C&country=KR&media=music&entity=song&limit=20&explicit=Yes&lang=en_us"))
			.andExpect(header("User-Agent", "Mozilla/5.0 (compatible; TrackPick/1.0)"))
			.andRespond(withSuccess("""
				{"results":[{
				  "kind":"song",
				  "trackId":1828393595,
				  "trackName":"0+0",
				  "artistName":"한로로"
				}]}
				""", MediaType.parseMediaType("text/javascript;charset=utf-8")));

		assertThat(client.search("한로로"))
			.singleElement()
			.extracting(MusicCatalogTrack::artistName)
			.isEqualTo("한로로");
		server.verify();
	}

	@Test
	void usesUsDiscoveryOnlyWhenKrIsEmptyAndReturnsKrLocalizedMetadata() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://itunes.apple.com");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AppleItunesClient client = new AppleItunesClient(
			builder.build(),
			properties(),
			new ProviderCallRateLimiter(15, Clock.systemUTC()),
			new ObjectMapper());

		server.expect(requestTo(
			"https://itunes.apple.com/search?term=Radiohead&country=KR&media=music&entity=song&limit=20&explicit=Yes&lang=en_us"))
			.andRespond(withSuccess("{\"resultCount\":0,\"results\":[]}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(
			"https://itunes.apple.com/search?term=Radiohead&country=US&media=music&entity=song&limit=20&explicit=Yes&lang=en_us"))
			.andRespond(withSuccess("""
				{"results":[{
				  "kind":"song",
				  "trackId":1097862870,
				  "trackName":"Let Down (English discovery)",
				  "artistName":"Radiohead"
				}]}
				""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(
			"https://itunes.apple.com/lookup?id=1097862870&country=KR&entity=song&lang=en_us"))
			.andRespond(withSuccess("""
				{"results":[{
				  "kind":"song",
				  "trackId":1097862870,
				  "trackName":"렛 다운",
				  "artistName":"라디오헤드"
				}]}
				""", MediaType.APPLICATION_JSON));

		assertThat(client.search("Radiohead"))
			.singleElement()
			.extracting(MusicCatalogTrack::title, MusicCatalogTrack::artistName)
			.containsExactly("렛 다운", "라디오헤드");
		server.verify();
	}

	@Test
	void looksUpTheSelectedTrackBeforeRecommendation() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://itunes.apple.com");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AppleItunesClient client = new AppleItunesClient(
			builder.build(),
			properties(),
			new ProviderCallRateLimiter(15, Clock.systemUTC()),
			new ObjectMapper());

		server.expect(requestTo(
			"https://itunes.apple.com/lookup?id=1828393595&country=KR&entity=song&lang=en_us"))
			.andRespond(withSuccess("""
				{"results":[{
				  "kind":"song",
				  "trackId":1828393595,
				  "trackName":"0+0",
				  "artistName":"한로로",
				  "primaryGenreName":"Rock"
				}]}
				""", MediaType.parseMediaType("text/javascript;charset=utf-8")));

		var track = client.lookup("1828393595");

		assertThat(track).hasValueSatisfying(found -> {
			assertThat(found.title()).isEqualTo("0+0");
			assertThat(found.primaryGenreName()).isEqualTo("Rock");
		});
		server.verify();
	}

	@Test
	void doesNotReturnANonKrTrackWhenTheKrLookupIsEmpty() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://itunes.apple.com");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		AppleItunesClient client = new AppleItunesClient(
			builder.build(),
			properties(),
			new ProviderCallRateLimiter(15, Clock.systemUTC()),
			new ObjectMapper());

		server.expect(requestTo(
			"https://itunes.apple.com/lookup?id=1828393595&country=KR&entity=song&lang=en_us"))
			.andRespond(withSuccess("{\"resultCount\":0,\"results\":[]}", MediaType.APPLICATION_JSON));
		assertThat(client.lookup("1828393595")).isEmpty();
		server.verify();
	}

	private static AppleItunesProperties properties() {
		return new AppleItunesProperties(
			URI.create("https://itunes.apple.com"),
			"KR",
			20,
			15,
			Duration.ofMinutes(5),
			500,
			Duration.ofSeconds(2),
			Duration.ofSeconds(4));
	}
}
