package app.plataforma.api.identity.internal;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import app.plataforma.api.platform.AttemptLimiter;

@Configuration(proxyBeanMethods = false)
class LimiterConfig {

	/** Inicios de sesión fallidos y comprobaciones de contraseña. */
	@Bean
	AttemptLimiter loginLimiter(@Value("${app.security.login.max-failures:5}") int max,
			@Value("${app.security.login.window:15m}") Duration window, Clock clock) {
		return new AttemptLimiter(max, window, clock);
	}

	/** Correos de verificación y de recuperación de contraseña por cuenta. */
	@Bean
	AttemptLimiter emailLimiter(@Value("${app.security.email.max-sends:3}") int max,
			@Value("${app.security.email.window:1h}") Duration window, Clock clock) {
		return new AttemptLimiter(max, window, clock);
	}

}
