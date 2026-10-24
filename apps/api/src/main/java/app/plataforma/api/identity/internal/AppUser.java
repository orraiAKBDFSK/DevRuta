package app.plataforma.api.identity.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "app_user")
class AppUser {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserStatus status = UserStatus.ACTIVE;

	@Column(name = "terms_version", nullable = false)
	private String termsVersion;

	@Column(name = "terms_accepted_at", nullable = false)
	private Instant termsAcceptedAt;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected AppUser() {
	}

	AppUser(String email, String passwordHash, String termsVersion, Instant now) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.termsVersion = termsVersion;
		this.termsAcceptedAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void touch() {
		this.updatedAt = Instant.now();
	}

	UUID getId() {
		return id;
	}

	String getEmail() {
		return email;
	}

	String getPasswordHash() {
		return passwordHash;
	}

	void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	UserStatus getStatus() {
		return status;
	}

	boolean isEmailVerified() {
		return emailVerifiedAt != null;
	}

	void markEmailVerified(Instant now) {
		if (emailVerifiedAt == null) {
			emailVerifiedAt = now;
		}
	}

}
