package app.plataforma.api.identity.internal;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import app.plataforma.api.identity.AuthenticatedUser;

/**
 * Autentica con correo y contraseña. Siempre compara contra un hash (el real o uno de relleno) para que el tiempo
 * de respuesta no revele si el correo existe, y devuelve el mismo error en todos los fallos.
 */
@Component
class EmailPasswordAuthenticationProvider implements AuthenticationProvider {

	static final List<SimpleGrantedAuthority> STUDENT_AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_STUDENT"));

	private final UserRepository users;

	private final PasswordEncoder encoder;

	private final String dummyHash;

	EmailPasswordAuthenticationProvider(UserRepository users, PasswordEncoder encoder) {
		this.users = users;
		this.encoder = encoder;
		this.dummyHash = encoder.encode("relleno-para-igualar-tiempos");
	}

	@Override
	public Authentication authenticate(Authentication authentication) {
		String email = normalize(authentication.getName());
		Object credentials = authentication.getCredentials();
		String password = credentials == null ? "" : credentials.toString();

		Optional<AppUser> user = users.findByEmail(email);
		boolean matches = encoder.matches(password, user.map(AppUser::getPasswordHash).orElse(dummyHash));
		if (user.isEmpty() || !matches || user.get().getStatus() != UserStatus.ACTIVE) {
			throw new BadCredentialsException("Credenciales inválidas");
		}
		AuthenticatedUser principal = new AuthenticatedUser(user.get().getId(), user.get().getEmail());
		return UsernamePasswordAuthenticationToken.authenticated(principal, null, STUDENT_AUTHORITIES);
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}

	static String normalize(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}

}
