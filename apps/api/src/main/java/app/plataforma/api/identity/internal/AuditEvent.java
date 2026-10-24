package app.plataforma.api.identity.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_event")
class AuditEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "event_type", nullable = false)
	private String eventType;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AuditEvent() {
	}

	AuditEvent(UUID userId, String eventType, Instant createdAt) {
		this.userId = userId;
		this.eventType = eventType;
		this.createdAt = createdAt;
	}

}
