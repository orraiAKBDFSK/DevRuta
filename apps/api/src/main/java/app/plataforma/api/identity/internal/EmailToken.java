package app.plataforma.api.identity.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "email_token")
class EmailToken {

	enum Purpose {
		VERIFY_EMAIL, RESET_PASSWORD
	}

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Purpose purpose;

	@Column(name = "token_hash", nullable = false)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected EmailToken() {
	}

	EmailToken(UUID userId, Purpose purpose, String tokenHash, Instant expiresAt, Instant createdAt) {
		this.userId = userId;
		this.purpose = purpose;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	UUID getId() {
		return id;
	}

	UUID getUserId() {
		return userId;
	}

	Purpose getPurpose() {
		return purpose;
	}

	Instant getExpiresAt() {
		return expiresAt;
	}

	Instant getUsedAt() {
		return usedAt;
	}

}
