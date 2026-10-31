package app.plataforma.api.profile;

import java.util.UUID;

/** API pública del módulo profile para los demás módulos. */
public interface ProfileDirectory {

	/** Idioma preferido del usuario ({@code es} o {@code en}); {@code es} si no tiene perfil. */
	String localeOf(UUID userId);

	/** Zona horaria guardada en el perfil (p. ej. {@code America/Bogota}); {@code UTC} si no hay perfil. */
	String timeZoneOf(UUID userId);

}
