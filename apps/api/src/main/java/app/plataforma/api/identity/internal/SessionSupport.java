package app.plataforma.api.identity.internal;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Inicio y cierre de sesiones de servidor, y revocación de las sesiones de un usuario. */
@Component
class SessionSupport {

	private final SecurityContextRepository securityContextRepository;

	private final CsrfTokenRepository csrfTokenRepository;

	private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

	SessionSupport(SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository,
			FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
		this.securityContextRepository = securityContextRepository;
		this.csrfTokenRepository = csrfTokenRepository;
		this.sessionRepository = sessionRepository;
	}

	void start(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
		// Protección contra fijación de sesión: si ya había sesión (anónima), se cambia su identificador.
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		// Token CSRF nuevo tras iniciar sesión.
		csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
	}

	void end(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		new SecurityContextLogoutHandler().logout(request, response, authentication);
	}

	/** Cierra todas las sesiones del usuario excepto la indicada (por ejemplo, tras cambiar la contraseña). */
	void terminateOthers(UUID userId, String keepSessionId) {
		sessionRepository.findByPrincipalName(userId.toString()).keySet().stream()
				.filter(id -> !id.equals(keepSessionId))
				.forEach(sessionRepository::deleteById);
	}

	void terminateAll(UUID userId) {
		sessionRepository.findByPrincipalName(userId.toString()).keySet().forEach(sessionRepository::deleteById);
	}

}
