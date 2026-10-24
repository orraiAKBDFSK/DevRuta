package app.plataforma.api.notifications.internal;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import app.plataforma.api.identity.EmailVerificationRequested;
import app.plataforma.api.identity.PasswordResetRequested;
import app.plataforma.api.platform.email.EmailSender;
import app.plataforma.api.profile.ProfileDirectory;

/**
 * Envía los correos de identidad. Se ejecuta DESPUÉS de confirmar la transacción (si se deshace, no sale ningún
 * correo) y en un hilo aparte (la petición no espera al proveedor). Si el envío falla solo se registra: el usuario
 * puede pedir otro. Un sistema con más volumen necesitaría una bandeja de salida persistente con reintentos.
 */
@Component
class EmailNotificationListener {

	private static final Logger log = LoggerFactory.getLogger(EmailNotificationListener.class);

	private final EmailSender sender;

	private final ProfileDirectory profiles;

	private final String baseUrl;

	private final String productName;

	private final Duration verifyTtl;

	private final Duration resetTtl;

	EmailNotificationListener(EmailSender sender, ProfileDirectory profiles,
			@Value("${app.public-base-url}") String baseUrl, @Value("${app.name:Plataforma}") String productName,
			@Value("${app.security.tokens.verify-ttl:24h}") Duration verifyTtl,
			@Value("${app.security.tokens.reset-ttl:30m}") Duration resetTtl) {
		this.sender = sender;
		this.profiles = profiles;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.productName = productName;
		this.verifyTtl = verifyTtl;
		this.resetTtl = resetTtl;
	}

	@Async("mailExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	void on(EmailVerificationRequested event) {
		deliver(EmailTemplates.Kind.VERIFY_EMAIL, event.userId(), event.email(), "/verificar-correo", event.token(),
				verifyTtl);
	}

	@Async("mailExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	void on(PasswordResetRequested event) {
		deliver(EmailTemplates.Kind.RESET_PASSWORD, event.userId(), event.email(), "/restablecer", event.token(),
				resetTtl);
	}

	private void deliver(EmailTemplates.Kind kind, UUID userId, String to, String path, String token, Duration ttl) {
		try {
			String link = baseUrl + path + "?token=" + token;
			String locale = profiles.localeOf(userId);
			sender.send(EmailTemplates.build(kind, locale, to, productName, link, ttl));
		}
		catch (RuntimeException ex) {
			// Nunca se registra el token ni el enlace.
			log.warn("No se pudo enviar el correo {} al usuario {}", kind, userId, ex);
		}
	}

}
