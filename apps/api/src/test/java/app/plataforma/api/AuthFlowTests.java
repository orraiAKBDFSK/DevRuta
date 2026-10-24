package app.plataforma.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/** Pruebas de extremo a extremo de registro, sesión, perfil y borrado de cuenta, contra PostgreSQL real. */
class AuthFlowTests extends IntegrationTest {

	private static final String PASSWORD = "contrasena-larga-1";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	private static String registerBody(String email, String password) {
		return """
				{"email":"%s","password":"%s","displayName":"Ana","locale":"es","acceptTerms":true}"""
				.formatted(email, password);
	}

	private static String loginBody(String email, String password) {
		return """
				{"email":"%s","password":"%s"}""".formatted(email, password);
	}

	private TestClient newClient() {
		TestClient client = new TestClient(port);
		client.primeCsrf();
		return client;
	}

	private TestClient registered(String email) {
		TestClient client = newClient();
		assertThat(client.post("/v1/auth/register", registerBody(email, PASSWORD)).status()).isEqualTo(201);
		return client;
	}

	@Test
	void registrationCreatesAccountProfileAndAHardenedSession() {
		String email = uniqueEmail();
		TestClient client = newClient();

		TestClient.Response response = client.post("/v1/auth/register", registerBody(email, PASSWORD));

		assertThat(response.status()).isEqualTo(201);
		assertThat(response.json("email")).isEqualTo(email);
		String sessionCookie = response.headers().allValues("Set-Cookie").stream()
				.filter(c -> c.startsWith("SESSION=")).findFirst().orElseThrow();
		assertThat(sessionCookie).contains("HttpOnly").contains("SameSite=Lax");

		assertThat(client.get("/v1/auth/me").json("email")).isEqualTo(email);
		TestClient.Response profile = client.get("/v1/me/profile");
		assertThat(profile.status()).isEqualTo(200);
		assertThat(profile.json("displayName")).isEqualTo("Ana");
		assertThat(profile.json("locale")).isEqualTo("es");
	}

	@Test
	void passwordIsStoredAsArgon2idAndSessionIsIndexedByUser() {
		String email = uniqueEmail();
		TestClient client = registered(email);

		String hash = jdbc.queryForObject("select password_hash from app_user where email = ?", String.class, email);
		assertThat(hash).startsWith("$argon2id$").doesNotContain(PASSWORD);

		String userId = client.get("/v1/auth/me").json("id");
		Integer sessions = jdbc.queryForObject("select count(*) from spring_session where principal_name = ?",
				Integer.class, userId);
		assertThat(sessions).isEqualTo(1);
	}

	@Test
	void postWithoutCsrfTokenIsForbidden() {
		TestClient client = newClient();
		String email = uniqueEmail();

		TestClient.Response response = client.postWithoutCsrf("/v1/auth/register", registerBody(email, PASSWORD));

		assertThat(response.status()).isEqualTo(403);
		assertThat(response.json("code")).isEqualTo("FORBIDDEN");
		assertThat(jdbc.queryForObject("select count(*) from app_user where email = ?", Integer.class, email))
				.isZero();
	}

	@Test
	void protectedEndpointsRequireAuthentication() {
		TestClient client = newClient();

		assertThat(client.get("/v1/auth/me").status()).isEqualTo(401);
		assertThat(client.get("/v1/me/profile").status()).isEqualTo(401);
	}

	@Test
	void anonymousVisitorsDoNotCreateSessions() {
		Integer before = jdbc.queryForObject("select count(*) from spring_session", Integer.class);
		TestClient client = newClient();

		for (int i = 0; i < 5; i++) {
			assertThat(client.get("/v1/auth/me").status()).isEqualTo(401);
			assertThat(client.get("/v1/me/profile").status()).isEqualTo(401);
		}
		client.post("/v1/auth/login", loginBody(uniqueEmail(), PASSWORD));

		assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class)).isEqualTo(before);
	}

	@Test
	void registrationRejectsDuplicateEmailWeakPasswordAndMissingTerms() {
		String email = uniqueEmail();
		registered(email);
		TestClient client = newClient();

		TestClient.Response duplicate = client.post("/v1/auth/register", registerBody(email.toUpperCase(), PASSWORD));
		assertThat(duplicate.status()).isEqualTo(409);
		assertThat(duplicate.json("code")).isEqualTo("EMAIL_ALREADY_REGISTERED");

		TestClient.Response weak = client.post("/v1/auth/register", registerBody(uniqueEmail(), "corta"));
		assertThat(weak.status()).isEqualTo(400);
		assertThat(weak.json("code")).isEqualTo("PASSWORD_TOO_SHORT");

		TestClient.Response noTerms = client.post("/v1/auth/register", """
				{"email":"%s","password":"%s","displayName":"Ana","acceptTerms":false}"""
				.formatted(uniqueEmail(), PASSWORD));
		assertThat(noTerms.status()).isEqualTo(400);
		assertThat(noTerms.json("code")).isEqualTo("VALIDATION_ERROR");
		assertThat(noTerms.body()).contains("acceptTerms");
	}

	@Test
	void loginFailuresAreGenericAndRateLimited() {
		String email = uniqueEmail();
		registered(email);
		TestClient client = newClient();

		TestClient.Response wrongPassword = client.post("/v1/auth/login", loginBody(email, "otra-contrasena-1"));
		TestClient.Response unknownUser = client.post("/v1/auth/login", loginBody(uniqueEmail(), PASSWORD));
		assertThat(wrongPassword.status()).isEqualTo(401);
		assertThat(unknownUser.status()).isEqualTo(401);
		assertThat(wrongPassword.json("code")).isEqualTo("INVALID_CREDENTIALS");
		assertThat(unknownUser.json("code")).isEqualTo("INVALID_CREDENTIALS");

		for (int i = 0; i < 4; i++) {
			client.post("/v1/auth/login", loginBody(email, "otra-contrasena-1"));
		}
		TestClient.Response blocked = client.post("/v1/auth/login", loginBody(email, PASSWORD));
		assertThat(blocked.status()).isEqualTo(429);
		assertThat(blocked.headers().firstValue("Retry-After")).isPresent();
	}

	@Test
	void loginAndLogoutManageTheSession() {
		String email = uniqueEmail();
		TestClient first = registered(email);
		first.post("/v1/auth/logout", "{}");
		assertThat(first.get("/v1/auth/me").status()).isEqualTo(401);

		TestClient second = newClient();
		assertThat(second.post("/v1/auth/login", loginBody(email.toUpperCase(), PASSWORD)).status()).isEqualTo(200);
		assertThat(second.get("/v1/auth/me").status()).isEqualTo(200);
	}

	@Test
	void profileCanBeUpdatedPartiallyAndIsValidated() {
		TestClient client = registered(uniqueEmail());

		TestClient.Response updated = client.patch("/v1/me/profile",
				"{\"displayName\":\"Luz\",\"experienceLevel\":\"SOME\",\"dailyGoalMinutes\":20,\"timezone\":\"America/Lima\"}");
		assertThat(updated.status()).isEqualTo(200);
		assertThat(updated.json("displayName")).isEqualTo("Luz");
		assertThat(updated.json("timezone")).isEqualTo("America/Lima");
		assertThat(updated.body()).contains("\"dailyGoalMinutes\":20").contains("\"experienceLevel\":\"SOME\"");
		assertThat(updated.json("locale")).isEqualTo("es");

		assertThat(client.patch("/v1/me/profile", "{\"dailyGoalMinutes\":3}").status()).isEqualTo(400);
		TestClient.Response badZone = client.patch("/v1/me/profile", "{\"timezone\":\"Marte/Olimpo\"}");
		assertThat(badZone.status()).isEqualTo(400);
		assertThat(badZone.json("code")).isEqualTo("INVALID_TIMEZONE");
	}

	@Test
	void changingPasswordEndsTheOtherSessions() {
		String email = uniqueEmail();
		TestClient laptop = registered(email);
		TestClient phone = newClient();
		phone.post("/v1/auth/login", loginBody(email, PASSWORD));
		assertThat(phone.get("/v1/auth/me").status()).isEqualTo(200);

		TestClient.Response wrongCurrent = laptop.post("/v1/me/password",
				"{\"currentPassword\":\"incorrecta-123\",\"newPassword\":\"nueva-contrasena-2\"}");
		assertThat(wrongCurrent.status()).isEqualTo(400);
		assertThat(wrongCurrent.json("code")).isEqualTo("INVALID_CURRENT_PASSWORD");

		TestClient.Response changed = laptop.post("/v1/me/password",
				"{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"nueva-contrasena-2\"}");
		assertThat(changed.status()).isEqualTo(204);

		assertThat(laptop.get("/v1/auth/me").status()).isEqualTo(200);
		assertThat(phone.get("/v1/auth/me").status()).isEqualTo(401);

		TestClient fresh = newClient();
		assertThat(fresh.post("/v1/auth/login", loginBody(email, PASSWORD)).status()).isEqualTo(401);
		assertThat(fresh.post("/v1/auth/login", loginBody(email, "nueva-contrasena-2")).status()).isEqualTo(200);
	}

	@Test
	void deletingTheAccountRemovesDataAndAnonymizesTheAuditTrail() {
		String email = uniqueEmail();
		TestClient client = registered(email);
		String userId = client.get("/v1/auth/me").json("id");

		TestClient.Response wrong = client.delete("/v1/me", "{\"password\":\"incorrecta-123\"}");
		assertThat(wrong.status()).isEqualTo(400);

		assertThat(client.delete("/v1/me", "{\"password\":\"" + PASSWORD + "\"}").status()).isEqualTo(204);

		assertThat(client.get("/v1/auth/me").status()).isEqualTo(401);
		assertThat(jdbc.queryForObject("select count(*) from app_user where email = ?", Integer.class, email))
				.isZero();
		assertThat(jdbc.queryForObject("select count(*) from user_profile where user_id = ?::uuid", Integer.class,
				userId)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from audit_event where user_id = ?::uuid", Integer.class,
				userId)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from spring_session where principal_name = ?",
				Integer.class, userId)).isZero();
		assertThat(newClient().post("/v1/auth/login", loginBody(email, PASSWORD)).status()).isEqualTo(401);
	}

}
