package app.plataforma.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Sustitutos de pruebas: el runner de código y un reloj que se puede adelantar. */
@TestConfiguration(proxyBeanMethods = false)
public class TestExecutorConfiguration {

	@Bean
	@Primary
	FakeCodeExecutor fakeCodeExecutor() {
		return new FakeCodeExecutor();
	}

	@Bean
	@Primary
	OffsetClock offsetClock() {
		return new OffsetClock();
	}

}
