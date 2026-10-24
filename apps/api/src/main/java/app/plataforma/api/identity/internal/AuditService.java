package app.plataforma.api.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
class AuditService {

	static final String USER_REGISTERED = "USER_REGISTERED";

	static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";

	static final String LOGIN_FAILED = "LOGIN_FAILED";

	static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";

	static final String ACCOUNT_DELETED = "ACCOUNT_DELETED";

	static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";

	static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";

	static final String PASSWORD_RESET = "PASSWORD_RESET";

	private final AuditEventRepository events;

	private final Clock clock;

	AuditService(AuditEventRepository events, Clock clock) {
		this.events = events;
		this.clock = clock;
	}

	void record(String eventType, UUID userId) {
		events.save(new AuditEvent(userId, eventType, Instant.now(clock)));
	}

	void anonymize(UUID userId) {
		events.anonymize(userId);
	}

}
