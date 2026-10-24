package app.plataforma.api.profile;

import java.util.UUID;

/** API pública del módulo profile para los demás módulos. */
public interface ProfileDirectory {

	/** Idioma preferido del usuario ({@code es} o {@code en}); {@code es} si no tiene perfil. */
	String localeOf(UUID userId);

}
