package app.plataforma.api.identity.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Autenticación por sesión de servidor (cookie HttpOnly + SameSite=Lax) con protección CSRF por doble envío
 * de cookie, que es lo que Angular hace por defecto (cookie XSRF-TOKEN -> cabecera X-XSRF-TOKEN).
 * Las rutas llegan sin el prefijo /api (es el context-path).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
			SecurityContextRepository securityContextRepository) throws Exception {
		http
				.authorizeHttpRequests(requests -> requests
						// /error es el destino interno de sendError; si no es público, un 403 acaba como 401.
						.requestMatchers("/error").permitAll()
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/v1/auth/csrf", "/v1/auth/register", "/v1/auth/login", "/v1/auth/logout",
								"/v1/auth/verify-email", "/v1/auth/forgot-password", "/v1/auth/reset-password")
						.permitAll()
						.anyRequest().authenticated())
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfTokenRepository)
						.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
				.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
				.securityContext(context -> context.securityContextRepository(securityContextRepository))
				// Sin "petición guardada": en una API no hay a dónde volver tras iniciar sesión, y guardarla crearía
				// una sesión (una fila en spring_session) por cada visitante anónimo rechazado.
				.requestCache(cache -> cache.requestCache(new NullRequestCache()))
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
						// También cubre los fallos de CSRF: 403 con un código estable que el frontend entiende.
						.accessDeniedHandler((request, response, ex) -> {
							response.setStatus(HttpStatus.FORBIDDEN.value());
							response.setContentType("application/problem+json");
							response.getWriter().write("{\"status\":403,\"code\":\"FORBIDDEN\"}");
						}))
				// El inicio y cierre de sesión los hacen los controladores JSON, no los formularios de Spring.
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable);
		return http.build();
	}

	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		// Ruta "/" para que la aplicación (servida desde "/") pueda leer la cookie aunque la API cuelgue de /api.
		repository.setCookiePath("/");
		return repository;
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	AuthenticationManager authenticationManager(EmailPasswordAuthenticationProvider provider) {
		return new ProviderManager(provider);
	}

	/**
	 * Argon2id con los mínimos que recomienda OWASP: 19 MiB de memoria, 2 iteraciones y 1 de paralelismo.
	 * El formato del hash guarda los parámetros, así que se podrán subir más adelante sin romper los hashes viejos.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
	}

}
