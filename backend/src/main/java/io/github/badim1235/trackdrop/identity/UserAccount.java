package io.github.badim1235.trackdrop.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 320)
	private String email;

	@Column(name = "email_normalized", nullable = false, unique = true, length = 320)
	private String emailNormalized;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "public_nickname", nullable = false, unique = true, length = 50)
	private String publicNickname;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserActivity activity;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UserAccount() {
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public Instant getEmailVerifiedAt() {
		return emailVerifiedAt;
	}

	public String getPublicNickname() {
		return publicNickname;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public UserActivity getActivity() {
		return activity;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
