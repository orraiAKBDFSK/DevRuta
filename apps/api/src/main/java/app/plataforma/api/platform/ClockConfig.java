package app.plataforma.api.platform;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

	/** Reloj inyectable: las pruebas pueden sustituirlo para controlar el tiempo. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
