package io.github.badim1235.trackdrop.catalog;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trackdrop.music.apple")
record AppleItunesProperties(
	URI baseUrl,
	String storefront,
	int resultLimit,
	int callsPerMinute,
	Duration cacheTtl,
	long cacheMaximumSize,
	Duration connectTimeout,
	Duration readTimeout
) {
}
