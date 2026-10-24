package app.plataforma.api.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.plataforma.api.platform.ApiException;

/**
 * Emite y consume tokens de un solo uso. El token son 256 bits aleatorios; en la base de datos solo se guarda su
 * hash SHA-256 (con tanta entropía no hace falta un hash lento). Todos los fallos al consumir devuelven el mismo
 * error para no revelar si un token existió, caducó o ya se usó.
 */
@Service
class EmailTokenService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final EmailTokenRepository tokens;

	private final Clock clock;

	private final Duration verifyTtl;

	private final Duration resetTtl;

	EmailTokenService(EmailTokenRepository tokens, Clock clock,
			@Value("${app.security.tokens.verify-ttl:24h}") Duration verifyTtl,
			@Value("${app.security.tokens.reset-ttl:30m}") Duration resetTtl) {
		this.tokens = tokens;
		this.clock = clock;
		this.verifyTtl = verifyTtl;
		this.resetTtl = resetTtl;
	}

	/** Crea un token nuevo para el usuario e invalida los anteriores del mismo propósito. Devuelve el token en claro. */
	@Transactional
	String issue(UUID userId, EmailToken.Purpose purpose) {
		Instant now = Instant.now(clock);
		tokens.invalidateOpen(userId, purpose, now);
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Duration ttl = purpose == EmailToken.Purpose.VERIFY_EMAIL ? verifyTtl : resetTtl;
		tokens.save(new EmailToken(userId, purpose, hash(raw), now.plus(ttl), now));
		return raw;
	}

	/** Comprueba que el token es válido sin gastarlo. Devuelve el usuario al que pertenece. */
	@Transactional(readOnly = true)
	UUID peek(String raw, EmailToken.Purpose purpose) {
		return find(raw, purpose).getUserId();
	}

	/** Gasta el token de forma atómica y devuelve el usuario. Un segundo intento con el mismo token falla. */
	@Transactional
	UUID consume(String raw, EmailToken.Purpose purpose) {
		EmailToken token = find(raw, purpose);
		if (tokens.markUsed(token.getId(), Instant.now(clock)) != 1) {
			throw invalid();
		}
		return token.getUserId();
	}

	/** Invalida los tokens abiertos de un propósito (por ejemplo, tras cambiar la contraseña). */
	@Transactional
	void invalidateOpen(UUID userId, EmailToken.Purpose purpose) {
		tokens.invalidateOpen(userId, purpose, Instant.now(clock));
	}

	/** Una vez al día borra los tokens caducados o usados hace más de 7 días. */
	@Scheduled(cron = "0 15 3 * * *")
	@Transactional
	void cleanUp() {
		tokens.deleteStale(Instant.now(clock).minus(Duration.ofDays(7)));
	}

	private EmailToken find(String raw, EmailToken.Purpose purpose) {
		if (raw == null || raw.isBlank() || raw.length() > 200) {
			throw invalid();
		}
		EmailToken token = tokens.findByTokenHash(hash(raw)).orElseThrow(EmailTokenService::invalid);
		if (token.getPurpose() != purpose || token.getUsedAt() != null
				|| !token.getExpiresAt().isAfter(Instant.now(clock))) {
			throw invalid();
		}
		return token;
	}

	static String hash(String raw) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 no disponible", ex);
		}
	}

	private static ApiException invalid() {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "El enlace no es válido o ha caducado");
	}

}
