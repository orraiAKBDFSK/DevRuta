package app.plataforma.api.learning.internal;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import app.plataforma.api.platform.AttemptLimiter;

@Configuration(proxyBeanMethods = false)
class LearningConfig {

	/** Intentos y pistas por usuario y minuto: frena los scripts que prueban respuestas a ciegas. */
	@Bean
	AttemptLimiter exerciseLimiter(@Value("${app.learning.max-requests-per-minute:60}") int max, Clock clock) {
		return new AttemptLimiter(max, Duration.ofMinutes(1), clock);
	}

	/** Cada ejecución arranca un contenedor: se limita más que las demás peticiones. */
	@Bean
	AttemptLimiter codeRunLimiter(@Value("${app.learning.max-code-runs-per-minute:10}") int max, Clock clock) {
		return new AttemptLimiter(max, Duration.ofMinutes(1), clock);
	}

}
