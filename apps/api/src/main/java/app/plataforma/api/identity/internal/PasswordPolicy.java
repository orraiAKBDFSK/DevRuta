package app.plataforma.api.identity.internal;

import java.util.Locale;

import org.springframework.http.HttpStatus;

import app.plataforma.api.platform.ApiException;

/**
 * Política de contraseñas: longitud entre 10 y 128 y que no sea el propio correo.
 * Sigue la guía de NIST/OWASP de priorizar la longitud sobre reglas de composición.
 * Pendiente: rechazar contraseñas filtradas conocidas (por ejemplo con la API de Have I Been Pwned).
 */
final class PasswordPolicy {

	static final int MIN_LENGTH = 10;

	static final int MAX_LENGTH = 128;

	private PasswordPolicy() {
	}

	static void validate(String password, String email) {
		if (password == null || password.length() < MIN_LENGTH) {
			throw weak("PASSWORD_TOO_SHORT", "La contraseña debe tener al menos " + MIN_LENGTH + " caracteres");
		}
		if (password.length() > MAX_LENGTH) {
			throw weak("PASSWORD_TOO_LONG", "La contraseña no puede superar " + MAX_LENGTH + " caracteres");
		}
		String normalized = password.toLowerCase(Locale.ROOT);
		String normalizedEmail = email == null ? "" : email.toLowerCase(Locale.ROOT);
		if (!normalizedEmail.isEmpty() && normalized.equals(normalizedEmail)) {
			throw weak("PASSWORD_EQUALS_EMAIL", "La contraseña no puede ser igual al correo");
		}
	}

	private static ApiException weak(String code, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message);
	}

}
