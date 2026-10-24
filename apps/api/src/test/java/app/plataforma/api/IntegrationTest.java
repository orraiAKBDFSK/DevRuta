package app.plataforma.api;

import java.time.Duration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de las pruebas de integración: aplicación completa con PostgreSQL real (bean con
 * {@code @ServiceConnection}) y Mailpit real como servidor SMTP. Todas las pruebas comparten el mismo contexto.
 * <p>
 * Mailpit no es un bean: se arranca aquí, antes de crear el contexto, porque la autoconfiguración del correo
 * decide si crea el {@code JavaMailSender} mirando {@code spring.mail.host} al empezar, y las propiedades de un
 * bean llegarían tarde. El contenedor se comparte en toda la ejecución y Testcontainers lo limpia al terminar.
 */
@Import({ TestcontainersConfiguration.class, TestExecutorConfiguration.class })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTest {

	// Misma imagen que en infra/compose.
	static final String MAILPIT_IMAGE = "axllent/mailpit:v1.31.3";

	@SuppressWarnings("resource")
	private static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse(MAILPIT_IMAGE))
			.withExposedPorts(1025, 8025)
			.waitingFor(Wait.forHttp("/api/v1/info").forPort(8025))
			.withStartupTimeout(Duration.ofSeconds(60));

	static {
		MAILPIT.start();
	}

	@DynamicPropertySource
	static void mailProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.mail.host", IntegrationTest::mailpitHost);
		registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
		registry.add("app.mail.from", () -> "Plataforma <no-reply@plataforma.test>");
		registry.add("app.public-base-url", () -> "http://localhost:4200");
		registry.add("mailpit.api", () -> "http://" + mailpitHost() + ":" + MAILPIT.getMappedPort(8025));
	}

	/**
	 * En Windows, "localhost" intenta primero IPv6 y cada conexión a un puerto publicado por Docker Desktop tarda
	 * unos segundos en caer a IPv4. Usar 127.0.0.1 evita esa espera (las pruebas pasaban de 70 s a pocos segundos).
	 */
	private static String mailpitHost() {
		String host = MAILPIT.getHost();
		return "localhost".equals(host) ? "127.0.0.1" : host;
	}

}
