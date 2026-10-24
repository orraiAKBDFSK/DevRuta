package app.plataforma.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verificación de correo y recuperación de contraseña de extremo a extremo: la aplicación envía correos reales por
 * SMTP a un Mailpit en contenedor, y las pruebas leen el enlace del correo igual que lo haría una persona.
 */
class EmailFlowTests extends IntegrationTest {

	private static final String PASSWORD = "contrasena-larga-1";

	private static final String NEW_PASSWORD = "otra-contrasena-larga-2";

	@Value("${local.server.port}")
	private int port;

	@Value("${mailpit.api}")
	private String mailpitApi;

	@Autowired
	private JdbcTemplate jdbc;

	private MailpitClient mailbox() {
		return new MailpitClient(mailpitApi);
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	private static String registerBody(String email, String locale) {
		return """
				{"email":"%s","password":"%s","displayName":"Ana","locale":"%s","acceptTerms":true}"""
				.formatted(email, PASSWORD, locale);
	}

	private TestClient newClient() {
		TestClient client = new TestClient(port);
		client.primeCsrf();
		return client;
	}

	private TestClient register(String email, String locale) {
		TestClient client = newClient();
		assertThat(client.post("/v1/auth/register", registerBody(email, locale)).status()).isEqualTo(201);
		return client;
	}

	private static String tokenBody(String token) {
		return "{\"token\":\"" + token + "\"}";
	}

	private static String resetBody(String token, String password) {
		return "{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}";
	}

	private static String emailBody(String email) {
		return "{\"email\":\"" + email + "\"}";
	}

	@Test
	void registrationSendsALinkThatVerifiesTheEmail() {
		String email = uniqueEmail();
		TestClient client = register(email, "es");

		assertThat(client.get("/v1/auth/me").body()).contains("\"emailVerified\":false");

		List<MailpitClient.Mail> mails = mailbox().waitFor(email, 1);
		assertThat(mails).hasSize(1);
		MailpitClient.Mail mail = mails.getFirst();
		assertThat(mail.subject()).isEqualTo("Verifica tu correo");
		assertThat(mail.link()).startsWith("http://localhost:4200/verificar-correo?token=");

		TestClient visitor = newClient();
		assertThat(visitor.post("/v1/auth/verify-email", tokenBody(mail.token())).status()).isEqualTo(204);
		assertThat(client.get("/v1/auth/me").body()).contains("\"emailVerified\":true");

		TestClient.Response again = visitor.post("/v1/auth/verify-email", tokenBody(mail.token()));
		assertThat(again.status()).isEqualTo(400);
		assertThat(again.json("code")).isEqualTo("INVALID_TOKEN");
	}

	@Test
	void theEmailIsWrittenInTheLanguageOfTheProfile() {
		String email = uniqueEmail();
		register(email, "en");

		MailpitClient.Mail mail = mailbox().waitFor(email, 1).getFirst();

		assertThat(mail.subject()).isEqualTo("Verify your email");
		assertThat(mail.text()).contains("Hello,").contains("expires in 24 hours");
	}

	@Test
	void tokensAreStoredOnlyAsHashes() {
		String email = uniqueEmail();
		TestClient client = register(email, "es");
		String token = mailbox().waitFor(email, 1).getFirst().token();
		String userId = client.get("/v1/auth/me").json("id");

		String stored = jdbc.queryForObject("select token_hash from email_token where user_id = ?::uuid",
				String.class, userId);

		assertThat(stored).hasSize(64).matches("[0-9a-f]+").isNotEqualTo(token).doesNotContain(token);
	}

	@Test
	void verificationCanBeResentButOnlyTheLatestLinkWorksAndTheRateIsLimited() {
		String email = uniqueEmail();
		TestClient client = register(email, "es");
		String first = mailbox().waitFor(email, 1).getFirst().token();

		assertThat(client.post("/v1/me/verification-email", "{}").status()).isEqualTo(204);
		List<MailpitClient.Mail> mails = mailbox().waitFor(email, 2);
		assertThat(mails).hasSize(2);

		TestClient visitor = newClient();
		assertThat(visitor.post("/v1/auth/verify-email", tokenBody(first)).status()).isEqualTo(400);
		String latest = mails.stream().map(MailpitClient.Mail::token).filter(t -> !t.equals(first)).findFirst()
				.orElseThrow();
		assertThat(visitor.post("/v1/auth/verify-email", tokenBody(latest)).status()).isEqualTo(204);

		// Con la cuenta ya verificada, reenviar no hace nada.
		assertThat(client.post("/v1/me/verification-email", "{}").status()).isEqualTo(204);
	}

	@Test
	void resendingVerificationTooOftenIsRateLimited() {
		TestClient client = register(uniqueEmail(), "es");

		// El registro cuenta como el primer envío; el límite es de 3 por hora.
		assertThat(client.post("/v1/me/verification-email", "{}").status()).isEqualTo(204);
		assertThat(client.post("/v1/me/verification-email", "{}").status()).isEqualTo(204);
		TestClient.Response blocked = client.post("/v1/me/verification-email", "{}");

		assertThat(blocked.status()).isEqualTo(429);
		assertThat(blocked.headers().firstValue("Retry-After")).isPresent();
	}

	@Test
	void forgotPasswordAnswersTheSameWhetherOrNotTheEmailExists() {
		String known = uniqueEmail();
		register(known, "es");
		mailbox().waitFor(known, 1);
		String unknown = uniqueEmail();
		TestClient visitor = newClient();

		TestClient.Response forUnknown = visitor.post("/v1/auth/forgot-password", emailBody(unknown));
		TestClient.Response forKnown = visitor.post("/v1/auth/forgot-password", emailBody(known));

		assertThat(forUnknown.status()).isEqualTo(202);
		assertThat(forKnown.status()).isEqualTo(202);
		assertThat(forUnknown.body()).isEqualTo(forKnown.body());

		List<MailpitClient.Mail> mails = mailbox().waitFor(known, 2);
		assertThat(mails).extracting(MailpitClient.Mail::subject).contains("Restablece tu contraseña");
		assertThat(mailbox().messagesTo(unknown)).isEmpty();
	}

	@Test
	void resettingThePasswordWorksOnceEndsOtherSessionsAndVerifiesTheEmail() {
		String email = uniqueEmail();
		TestClient laptop = register(email, "es");
		mailbox().waitFor(email, 1);
		TestClient visitor = newClient();
		visitor.post("/v1/auth/forgot-password", emailBody(email));
		String token = mailbox().waitFor(email, 2).stream().filter(m -> m.subject().startsWith("Restablece"))
				.findFirst().orElseThrow().token();

		TestClient.Response weak = visitor.post("/v1/auth/reset-password", resetBody(token, "corta"));
		assertThat(weak.status()).isEqualTo(400);
		assertThat(weak.json("code")).isEqualTo("PASSWORD_TOO_SHORT");

		// La contraseña débil no gastó el enlace.
		assertThat(visitor.post("/v1/auth/reset-password", resetBody(token, NEW_PASSWORD)).status()).isEqualTo(204);

		assertThat(visitor.post("/v1/auth/reset-password", resetBody(token, NEW_PASSWORD)).status()).isEqualTo(400);
		assertThat(laptop.get("/v1/auth/me").status()).isEqualTo(401);

		TestClient fresh = newClient();
		assertThat(fresh.post("/v1/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
				.status()).isEqualTo(401);
		TestClient.Response login = fresh.post("/v1/auth/login",
				"{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, NEW_PASSWORD));
		assertThat(login.status()).isEqualTo(200);
		assertThat(login.body()).contains("\"emailVerified\":true");
	}

	@Test
	void aVerificationLinkCannotResetAPassword() {
		String email = uniqueEmail();
		register(email, "es");
		String verificationToken = mailbox().waitFor(email, 1).getFirst().token();

		TestClient.Response response = newClient().post("/v1/auth/reset-password",
				resetBody(verificationToken, NEW_PASSWORD));

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.json("code")).isEqualTo("INVALID_TOKEN");
	}

	@Test
	void expiredAndUnknownTokensAreRejectedWithTheSameError() {
		String email = uniqueEmail();
		TestClient client = register(email, "es");
		String token = mailbox().waitFor(email, 1).getFirst().token();
		jdbc.update("update email_token set expires_at = now() - interval '1 minute' where user_id = ?::uuid",
				client.get("/v1/auth/me").json("id"));
		TestClient visitor = newClient();

		TestClient.Response expired = visitor.post("/v1/auth/verify-email", tokenBody(token));
		TestClient.Response unknown = visitor.post("/v1/auth/verify-email", tokenBody("no-existe-" + UUID.randomUUID()));

		assertThat(expired.status()).isEqualTo(400);
		assertThat(unknown.status()).isEqualTo(400);
		assertThat(expired.json("code")).isEqualTo("INVALID_TOKEN");
		assertThat(unknown.json("code")).isEqualTo("INVALID_TOKEN");
	}

	@Test
	void passwordResetRequestsAreRateLimitedPerEmailWithoutRevealingIt() throws InterruptedException {
		String email = uniqueEmail();
		register(email, "es");
		mailbox().waitFor(email, 1);
		TestClient visitor = newClient();

		for (int i = 0; i < 5; i++) {
			assertThat(visitor.post("/v1/auth/forgot-password", emailBody(email)).status()).isEqualTo(202);
		}

		// 1 correo de verificación + 3 de recuperación (el límite); los demás pedidos se ignoran en silencio.
		assertThat(mailbox().waitFor(email, 4)).hasSize(4);
		Thread.sleep(1000);
		assertThat(mailbox().messagesTo(email)).hasSize(4);
	}

	@Test
	void changingThePasswordInvalidatesPendingResetLinks() {
		String email = uniqueEmail();
		TestClient client = register(email, "es");
		mailbox().waitFor(email, 1);
		newClient().post("/v1/auth/forgot-password", emailBody(email));
		String resetToken = mailbox().waitFor(email, 2).stream().filter(m -> m.subject().startsWith("Restablece"))
				.findFirst().orElseThrow().token();

		assertThat(client.post("/v1/me/password",
				"{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}").status())
				.isEqualTo(204);

		TestClient.Response response = newClient().post("/v1/auth/reset-password",
				resetBody(resetToken, "una-tercera-contrasena-3"));
		assertThat(response.status()).isEqualTo(400);
	}

}
