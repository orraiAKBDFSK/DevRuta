package app.plataforma.api.identity;

import java.util.UUID;

/**
 * Evento: hay que enviar el enlace para restablecer la contraseña. Lo escucha el módulo notifications.
 * Lleva el token en claro, así que {@link #toString()} lo oculta para que no acabe en los registros.
 */
public record PasswordResetRequested(UUID userId, String email, String token) {

	@Override
	public String toString() {
		return "PasswordResetRequested[userId=" + userId + "]";
	}

}
