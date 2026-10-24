package app.plataforma.api.identity;

import java.util.UUID;

/**
 * Evento que identity publica cuando se crea una cuenta. Otros módulos (por ejemplo profile) reaccionan a él
 * dentro de la misma transacción: si fallan, el registro completo se deshace.
 */
public record UserRegistered(UUID userId, String displayName, String locale) {
}
