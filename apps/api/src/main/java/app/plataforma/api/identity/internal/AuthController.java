package app.plataforma.api.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.plataforma.api.identity.AuthenticatedUser;

@RestController
@RequestMapping("/v1/auth")
class AuthController {

	private final AuthService auth;

	private final SessionSupport sessions;

	AuthController(AuthService auth, SessionSupport sessions) {
		this.auth = auth;
		this.sessions = sessions;
	}

	/** No hace nada: existe para que el navegador obtenga la cookie XSRF-TOKEN antes de su primera petición POST. */
	@GetMapping("/csrf")
	ResponseEntity<Void> csrf() {
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/register")
	ResponseEntity<AuthDtos.AccountResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest body,
			HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication = auth.register(body);
		sessions.start(authentication, request, response);
		return ResponseEntity.status(HttpStatus.CREATED).body(auth.describe(principal(authentication)));
	}

	@PostMapping("/login")
	AuthDtos.AccountResponse login(@Valid @RequestBody AuthDtos.LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		Authentication authentication = auth.login(body.email(), body.password());
		sessions.start(authentication, request, response);
		return auth.describe(principal(authentication));
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) {
		sessions.end(request, response, authentication);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	AuthDtos.AccountResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
		return auth.describe(user);
	}

	@PostMapping("/verify-email")
	ResponseEntity<Void> verifyEmail(@Valid @RequestBody AuthDtos.VerifyEmailRequest body) {
		auth.verifyEmail(body.token());
		return ResponseEntity.noContent().build();
	}

	/** Responde 202 siempre, exista o no el correo, para no revelar qué cuentas hay. */
	@PostMapping("/forgot-password")
	ResponseEntity<Void> forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest body) {
		auth.requestPasswordReset(body.email());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/reset-password")
	ResponseEntity<Void> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest body) {
		// Quien tuviera la contraseña vieja (o una sesión robada) pierde el acceso.
		sessions.terminateAll(auth.resetPassword(body.token(), body.newPassword()));
		return ResponseEntity.noContent().build();
	}

	private static AuthenticatedUser principal(Authentication authentication) {
		return (AuthenticatedUser) authentication.getPrincipal();
	}

}
