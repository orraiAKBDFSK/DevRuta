package app.plataforma.api.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.plataforma.api.identity.AuthenticatedUser;

/** Acciones sensibles sobre la propia cuenta: piden la contraseña de nuevo. */
@RestController
@RequestMapping("/v1/me")
class AccountController {

	private final AuthService auth;

	private final SessionSupport sessions;

	AccountController(AuthService auth, SessionSupport sessions) {
		this.auth = auth;
		this.sessions = sessions;
	}

	@PostMapping("/password")
	ResponseEntity<Void> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest body,
			@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
		auth.changePassword(user, body.currentPassword(), body.newPassword());
		// Si alguien más tenía la sesión abierta, deja de tenerla.
		sessions.terminateOthers(user.id(), request.getSession().getId());
		return ResponseEntity.noContent().build();
	}

	/** Reenvía el correo de verificación (limitado por cuenta). */
	@PostMapping("/verification-email")
	ResponseEntity<Void> resendVerification(@AuthenticationPrincipal AuthenticatedUser user) {
		auth.resendVerification(user);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping
	ResponseEntity<Void> deleteAccount(@Valid @RequestBody AuthDtos.DeleteAccountRequest body,
			@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) {
		auth.deleteAccount(user, body.password());
		sessions.end(request, response, authentication);
		sessions.terminateAll(user.id());
		return ResponseEntity.noContent().build();
	}

}
