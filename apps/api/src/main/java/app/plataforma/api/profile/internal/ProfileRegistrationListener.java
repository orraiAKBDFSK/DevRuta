package app.plataforma.api.profile.internal;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import app.plataforma.api.identity.UserRegistered;

/**
 * Crea el perfil cuando identity registra una cuenta. Se ejecuta dentro de la transacción del registro:
 * si falla, no queda una cuenta sin perfil.
 */
@Component
class ProfileRegistrationListener {

	private final UserProfileRepository profiles;

	private final Clock clock;

	ProfileRegistrationListener(UserProfileRepository profiles, Clock clock) {
		this.profiles = profiles;
		this.clock = clock;
	}

	@EventListener
	void on(UserRegistered event) {
		profiles.save(new UserProfile(event.userId(), event.displayName(), event.locale(), Instant.now(clock)));
	}

}
