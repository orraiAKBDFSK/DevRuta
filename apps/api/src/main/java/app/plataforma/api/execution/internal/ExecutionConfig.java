package app.plataforma.api.execution.internal;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import app.plataforma.api.execution.CodeExecutor;

@Configuration(proxyBeanMethods = false)
class ExecutionConfig {

	/**
	 * Sin {@code app.runner.url} el motor queda desactivado: la aplicación arranca igual y los ejercicios de escribir
	 * código responden «no disponible». Con URL, el token es obligatorio (el runner no acepta peticiones anónimas).
	 */
	@Bean
	CodeExecutor codeExecutor(@Value("${app.runner.url:}") String url, @Value("${app.runner.token:}") String token,
			@Value("${app.runner.timeout:30s}") Duration timeout, JsonMapper json) {
		if (url.isBlank()) {
			return new CodeExecutor() {
				@Override
				public boolean isAvailable() {
					return false;
				}

				@Override
				public Result run(Request request) {
					throw new Unavailable("El motor de ejecución no está configurado", false);
				}
			};
		}
		if (token.length() < 16) {
			throw new IllegalStateException("app.runner.token es obligatorio (mínimo 16 caracteres) si app.runner.url está definido");
		}
		return new RunnerClient(url, token, Duration.ofSeconds(2), timeout, json);
	}

}
