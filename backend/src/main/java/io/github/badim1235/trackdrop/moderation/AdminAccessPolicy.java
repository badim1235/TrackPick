package io.github.badim1235.trackdrop.moderation;

import io.github.badim1235.trackdrop.identity.TrackDropPrincipal;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AdminAccessPolicy {

	private final Set<String> adminEmails;

	public AdminAccessPolicy(@Value("${trackdrop.security.admin-emails:}") String configuredEmails) {
		this.adminEmails = Arrays.stream(configuredEmails.split(","))
			.map(AdminAccessPolicy::normalize)
			.filter(email -> !email.isBlank())
			.collect(Collectors.toUnmodifiableSet());
	}

	public boolean isAdmin(TrackDropPrincipal principal) {
		return principal != null && adminEmails.contains(normalize(principal.username()));
	}

	void requireAdmin(TrackDropPrincipal principal) {
		if (!isAdmin(principal)) {
			throw ReportException.adminOnly();
		}
	}

	private static String normalize(String value) {
		return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
	}
}
