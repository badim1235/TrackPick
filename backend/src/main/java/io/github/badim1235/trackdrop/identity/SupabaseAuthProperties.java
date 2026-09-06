package io.github.badim1235.trackdrop.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trackdrop.supabase")
public record SupabaseAuthProperties(
	String url,
	String publishableKey,
	String secretKey,
	String emailRedirectUrl,
	String passwordRecoveryRedirectUrl
) {
}
