package app.plataforma.api.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.plataforma.api.identity.AuthenticatedUser;
import app.plataforma.api.identity.EmailVerificationRequested;
import app.plataforma.api.identity.PasswordResetRequested;
import app.plataforma.api.identity.UserRegistered;
import app.plataforma.api.platform.ApiException;
import app.plataforma.api.platform.AttemptLimiter;

@Service
class AuthService {

	/** Versión de los términos que acepta el usuario. Provisional hasta tener los textos legales definitivos. */
	static final String TERMS_VERSION = "2026-10-borrador";

	private static final String DEFAULT_LOCALE = "es";

	private final UserRepository users;

	private final PasswordEncoder encoder;

	private final AuthenticationManager authenticationManager;

	private final AttemptLimiter loginLimiter;

	private final AttemptLimiter emailLimiter;

	private final AuditService audit;

	private final EmailTokenService tokens;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	AuthService(UserRepository users, PasswordEncoder encoder, AuthenticationManager authenticationManager,
			@Qualifier("loginLimiter") AttemptLimiter loginLimiter,
			@Qualifier("emailLimiter") AttemptLimiter emailLimiter, AuditService audit, EmailTokenService tokens,
			ApplicationEventPublisher events, Clock clock) {
		this.users = users;
		this.encoder = encoder;
		this.authenticationManager = authenticationManager;
		this.loginLimiter = loginLimiter;
		this.emailLimiter = emailLimiter;
		this.audit = audit;
		this.tokens = tokens;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	Authentication register(AuthDtos.RegisterRequest request) {
		String email = EmailPasswordAuthenticationProvider.normalize(request.email());
		PasswordPolicy.validate(request.password(), email);
		if (users.existsByEmail(email)) {
			throw emailTaken();
		}
		AppUser user = new AppUser(email, encoder.encode(request.password()), TERMS_VERSION, Instant.now(clock));
		try {
			users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// Dos registros simultáneos con el mismo correo: gana el índice único de la base de datos.
			throw emailTaken();
		}
		audit.record(AuditService.USER_REGISTERED, user.getId());
		String locale = request.locale() == null ? DEFAULT_LOCALE : request.locale();
		events.publishEvent(new UserRegistered(user.getId(), request.displayName().trim(), locale));
		publishVerification(user);
		emailLimiter.record(verifyKey(user.getId()));
		return authenticated(new AuthenticatedUser(user.getId(), user.getEmail()));
	}

	Authentication login(String rawEmail, String password) {
		String email = EmailPasswordAuthenticationProvider.normalize(rawEmail);
		loginLimiter.checkAllowed(email);
		try {
			Authentication authentication = authenticationManager
					.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
			loginLimiter.reset(email);
			audit.record(AuditService.LOGIN_SUCCEEDED, ((AuthenticatedUser) authentication.getPrincipal()).id());
			return authentication;
		}
		catch (BadCredentialsException ex) {
			loginLimiter.record(email);
			audit.record(AuditService.LOGIN_FAILED, null);
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Correo o contraseña incorrectos");
		}
	}

	@Transactional(readOnly = true)
	AuthDtos.AccountResponse describe(AuthenticatedUser user) {
		AppUser account = find(user.id());
		return new AuthDtos.AccountResponse(account.getId(), account.getEmail(), account.isEmailVerified());
	}

	// ---- Verificación de correo ----

	/** Reenvía el enlace de verificación. Limitado por cuenta para que no sirva para enviar correo en masa. */
	@Transactional
	void resendVerification(AuthenticatedUser user) {
		String key = verifyKey(user.id());
		emailLimiter.checkAllowed(key);
		AppUser account = find(user.id());
		if (account.isEmailVerified()) {
			return;
		}
		publishVerification(account);
		emailLimiter.record(key);
	}

	@Transactional
	void verifyEmail(String token) {
		UUID userId = tokens.consume(token, EmailToken.Purpose.VERIFY_EMAIL);
		find(userId).markEmailVerified(Instant.now(clock));
		audit.record(AuditService.EMAIL_VERIFIED, userId);
	}

	// ---- Recuperación de contraseña ----

	/**
	 * Pide el enlace de recuperación. Nunca revela si el correo existe: devuelve siempre lo mismo, exista o no, esté
	 * o no agotado el límite. El correo se envía en segundo plano, así que tampoco cambia el tiempo de respuesta.
	 */
	@Transactional
	void requestPasswordReset(String rawEmail) {
		String email = EmailPasswordAuthenticationProvider.normalize(rawEmail);
		String key = "reset:" + email;
		if (emailLimiter.isExhausted(key)) {
			return;
		}
		emailLimiter.record(key);
		users.findByEmail(email).filter(user -> user.getStatus() == UserStatus.ACTIVE).ifPresent(user -> {
			String token = tokens.issue(user.getId(), EmailToken.Purpose.RESET_PASSWORD);
			events.publishEvent(new PasswordResetRequested(user.getId(), user.getEmail(), token));
			audit.record(AuditService.PASSWORD_RESET_REQUESTED, user.getId());
		});
	}

	/**
	 * Cambia la contraseña con un token de recuperación. Comprueba la política antes de gastar el token, para que
	 * una contraseña débil no obligue a pedir otro enlace. Recibir el enlace demuestra que el correo es suyo, así que
	 * también lo marca como verificado. Devuelve el usuario para que quien llama cierre sus sesiones.
	 */
	@Transactional
	UUID resetPassword(String token, String newPassword) {
		UUID userId = tokens.peek(token, EmailToken.Purpose.RESET_PASSWORD);
		AppUser user = find(userId);
		PasswordPolicy.validate(newPassword, user.getEmail());
		tokens.consume(token, EmailToken.Purpose.RESET_PASSWORD);
		user.setPasswordHash(encoder.encode(newPassword));
		user.markEmailVerified(Instant.now(clock));
		audit.record(AuditService.PASSWORD_RESET, userId);
		return userId;
	}

	// ---- Acciones sobre la propia cuenta ----

	@Transactional
	void changePassword(AuthenticatedUser user, String currentPassword, String newPassword) {
		AppUser account = verifyPassword(user, currentPassword);
		PasswordPolicy.validate(newPassword, account.getEmail());
		account.setPasswordHash(encoder.encode(newPassword));
		// Un enlace de recuperación pedido antes del cambio ya no debe servir.
		tokens.invalidateOpen(account.getId(), EmailToken.Purpose.RESET_PASSWORD);
		audit.record(AuditService.PASSWORD_CHANGED, account.getId());
	}

	@Transactional
	void deleteAccount(AuthenticatedUser user, String password) {
		AppUser account = verifyPassword(user, password);
		audit.anonymize(account.getId());
		// La base de datos borra el perfil y los tokens en cascada (on delete cascade).
		users.delete(account);
		audit.record(AuditService.ACCOUNT_DELETED, null);
	}

	/** Pide de nuevo la contraseña antes de una acción sensible, con el mismo límite de intentos que el login. */
	private AppUser verifyPassword(AuthenticatedUser user, String password) {
		String key = "user:" + user.id();
		loginLimiter.checkAllowed(key);
		AppUser account = find(user.id());
		if (!encoder.matches(password, account.getPasswordHash())) {
			loginLimiter.record(key);
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", "La contraseña es incorrecta");
		}
		loginLimiter.reset(key);
		return account;
	}

	private void publishVerification(AppUser user) {
		String token = tokens.issue(user.getId(), EmailToken.Purpose.VERIFY_EMAIL);
		events.publishEvent(new EmailVerificationRequested(user.getId(), user.getEmail(), token));
	}

	private AppUser find(UUID userId) {
		return users.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sesión no válida"));
	}

	private static String verifyKey(UUID userId) {
		return "verify:" + userId;
	}

	static Authentication authenticated(AuthenticatedUser principal) {
		return UsernamePasswordAuthenticationToken.authenticated(principal, null,
				EmailPasswordAuthenticationProvider.STUDENT_AUTHORITIES);
	}

	private static ApiException emailTaken() {
		return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Ese correo ya está registrado");
	}

}
