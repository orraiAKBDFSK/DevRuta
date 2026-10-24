package app.plataforma.api.identity;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/**
 * Usuario autenticado. Es parte de la API pública del módulo identity: los demás módulos lo obtienen
 * con {@code @AuthenticationPrincipal} y no dependen de nada más de identity.
 * Es {@link Serializable} porque Spring Session lo guarda en la base de datos junto con la sesión.
 * {@link #getName()} (el id) es el nombre de principal que indexa las sesiones de este usuario.
 */
public record AuthenticatedUser(UUID id, String email) implements Principal, Serializable {

	@Override
	public String getName() {
		return id.toString();
	}

}
