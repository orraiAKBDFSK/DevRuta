package app.plataforma.api.learning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import app.plataforma.api.FakeCodeExecutor;
import app.plataforma.api.IntegrationTest;
import app.plataforma.api.TestClient;
import app.plataforma.api.catalog.internal.CatalogTestSupport;
import app.plataforma.api.catalog.internal.FixtureContent;
import app.plataforma.api.execution.CodeExecutor;

/** Ejercicios, intentos y pistas por HTTP, contra PostgreSQL real. */
class LearningApiTests extends IntegrationTest {

	private static final String MC = "fx.u01.l01.e01";

	private static final String PREDICT = "fx.u01.l01.e02";

	private static final String ORDER = "fx.u01.l01.e03";

	private static final String FILL = "fx.u01.l01.e04";

	private static final String WRITE = "fx.u01.l01.e05";

	private static final String PASSWORD = "contrasena-larga-1";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private ApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private FakeCodeExecutor runner;

	@TempDir
	Path tmp;

	@BeforeEach
	void setUp() {
		runner.reset();
		CatalogTestSupport.clean(jdbc);
		FixtureContent fixture = new FixtureContent(tmp);
		fixture.setExercises("fx/u01-uno/01-a.yaml", FixtureContent.ALL_TYPES);
		CatalogTestSupport.importContent(context, tmp, false);
	}

	@AfterEach
	void tearDown() {
		CatalogTestSupport.clean(jdbc);
	}

	private TestClient newUser() {
		TestClient client = new TestClient(port);
		client.primeCsrf();
		String body = """
				{"email":"learn-%s@example.com","password":"%s","displayName":"Ana","locale":"es","acceptTerms":true}"""
				.formatted(UUID.randomUUID(), PASSWORD);
		assertThat(client.post("/v1/auth/register", body).status()).isEqualTo(201);
		return client;
	}

	private static String attempt(String exerciseId) {
		return "/v1/exercises/" + exerciseId + "/attempts";
	}

	private static String answer(String json) {
		return "{\"answer\":" + json + "}";
	}

	private int attemptsOf(String userId, String exerciseId) {
		return CatalogTestSupport.count(jdbc, "select count(*) from exercise_attempt a join exercise e on e.id = a.exercise_id "
				+ "where a.user_id = ?::uuid and e.content_id = ?", userId, exerciseId);
	}

	@Test
	void everythingRequiresASession() {
		TestClient anonymous = new TestClient(port);
		anonymous.primeCsrf();

		assertThat(anonymous.get("/v1/lessons/fx.u01.l01/exercises").status()).isEqualTo(401);
		assertThat(anonymous.post(attempt(MC), answer("{\"choice\":1}")).status()).isEqualTo(401);
		assertThat(anonymous.post("/v1/exercises/" + MC + "/hints", "{}").status()).isEqualTo(401);
	}

	@Test
	void listsTheExercisesWithoutRevealingAnySolution() {
		TestClient client = newUser();

		TestClient.Response response = client.getInLanguage("/v1/lessons/fx.u01.l01/exercises", "es");

		assertThat(response.status()).isEqualTo(200);
		String body = response.body();
		for (String id : new String[] { MC, PREDICT, ORDER, FILL, WRITE }) {
			assertThat(body).contains("\"id\":\"" + id + "\"");
		}
		assertThat(body).contains("\"options\":[\"Tres\",\"Cuatro\",\"Cinco\",\"Seis\"]")
				.contains("\"type\":\"WRITE_CODE\"").contains("# tu código").contains("\"blankCount\":2")
				.contains("\"hintsTotal\":2").contains("\"explanation\":null");
		// Nada de lo que delata la respuesta puede salir hacia el navegador.
		assertThat(body).doesNotContain("distractorTags").doesNotContain("solution").doesNotContain("referenceSolution")
				.doesNotContain("Dos más dos son cuatro").doesNotContain("\"answer\"").doesNotContain("expectedStdout")
				.doesNotContain("print(3)").doesNotContain("arith.off_by_one");
		// Las líneas llegan desordenadas.
		assertThat(body).contains("\"lines\":[").doesNotContain("\"lines\":[\"x = 1\",\"y = 2\",\"print(x + y)\"]");
		assertThat(response.headers().firstValue("Vary").orElse("")).contains("Accept-Language");
	}

	@Test
	void answersInTheRequestedLanguage() {
		TestClient client = newUser();

		TestClient.Response en = client.getInLanguage("/v1/lessons/fx.u01.l01/exercises", "en");

		assertThat(en.body()).contains("What is 2 + 2?").contains("\"options\":[\"Three\",\"Four\",\"Five\",\"Six\"]");
	}

	@Test
	void aWrongAttemptIsRecordedWithoutRevealingTheExplanation() {
		TestClient client = newUser();
		String userId = client.get("/v1/auth/me").json("id");

		TestClient.Response wrong = client.post(attempt(MC), answer("{\"choice\":0}"));

		assertThat(wrong.status()).isEqualTo(200);
		assertThat(wrong.body()).contains("\"correct\":false").contains("\"solved\":false").contains("\"attempts\":1")
				.contains("\"hintsAvailable\":2").contains("\"explanation\":null");
		assertThat(jdbc.queryForObject("select error_tag from exercise_attempt where user_id = ?::uuid", String.class,
				userId)).isEqualTo("arith.off_by_one");
		assertThat(wrong.body()).doesNotContain("arith.off_by_one");
	}

	@Test
	void aCorrectAttemptSolvesTheExerciseAndRevealsTheExplanation() {
		TestClient client = newUser();
		String userId = client.get("/v1/auth/me").json("id");
		client.post(attempt(MC), answer("{\"choice\":2}"));

		TestClient.Response right = client.post(attempt(MC), answer("{\"choice\":1}"));

		assertThat(right.body()).contains("\"correct\":true").contains("\"solved\":true").contains("\"attempts\":2")
				.contains("Dos más dos son cuatro.");
		assertThat(attemptsOf(userId, MC)).isEqualTo(2);

		// Al volver a la lección el ejercicio sigue resuelto y se muestra la explicación.
		String list = client.getInLanguage("/v1/lessons/fx.u01.l01/exercises", "es").body();
		assertThat(list).contains("\"solved\":true").contains("\"attempts\":2").contains("Dos más dos son cuatro.");
	}

	@Test
	void aSolvedExerciseStaysSolvedEvenAfterAWrongRetry() {
		TestClient client = newUser();
		client.post(attempt(MC), answer("{\"choice\":1}"));

		TestClient.Response retry = client.post(attempt(MC), answer("{\"choice\":3}"));

		assertThat(retry.body()).contains("\"correct\":false").contains("\"solved\":true")
				.contains("Dos más dos son cuatro.");
	}

	@Test
	void evaluatesPredictOrderAndFillExercises() {
		TestClient client = newUser();

		assertThat(client.post(attempt(PREDICT), answer("{\"text\":\"a\\r\\nb  \\n\"}")).body()).contains("\"correct\":true");
		assertThat(client.post(attempt(PREDICT), answer("{\"text\":\"b\\na\"}")).body()).contains("\"correct\":false");

		assertThat(client.post(attempt(ORDER), answer("{\"order\":[\"x = 1\",\"y = 2\",\"print(x + y)\"]}")).body())
				.contains("\"correct\":true");
		assertThat(client.post(attempt(ORDER), answer("{\"order\":[\"y = 2\",\"x = 1\",\"print(x + y)\"]}")).body())
				.contains("\"correct\":false");

		assertThat(client.post(attempt(FILL), answer("{\"blanks\":[\"01\",\" 2 \"]}")).body())
				.contains("\"correct\":true");
		assertThat(client.post(attempt(FILL), answer("{\"blanks\":[\"2\",\"1\"]}")).body())
				.contains("\"correct\":false");
	}

	@Test
	void rejectsMalformedAnswersWithoutRecordingThem() {
		TestClient client = newUser();
		String userId = client.get("/v1/auth/me").json("id");

		TestClient.Response badShape = client.post(attempt(FILL), answer("{\"blanks\":[\"1\"]}"));
		assertThat(badShape.status()).isEqualTo(400);
		assertThat(badShape.json("code")).isEqualTo("INVALID_ANSWER");
		assertThat(client.post(attempt(MC), answer("{\"choice\":\"uno\"}")).status()).isEqualTo(400);
		TestClient.Response noAnswer = client.post(attempt(MC), "{}");
		assertThat(noAnswer.status()).isEqualTo(400);
		assertThat(noAnswer.json("code")).isEqualTo("VALIDATION_ERROR");

		assertThat(attemptsOf(userId, FILL)).isZero();
		assertThat(attemptsOf(userId, MC)).isZero();
	}

	// ---- Escribir código (el runner se simula con FakeCodeExecutor) ----

	private static final String PRINT3 = answer("{\"code\":\"print(3)\"}");

	@Test
	void withoutARunnerWriteCodeIsListedAsNotEvaluableAndAttemptsAreNotRecorded() {
		TestClient client = newUser();

		String list = client.getInLanguage("/v1/lessons/fx.u01.l01/exercises", "es").body();
		assertThat(list).contains("\"id\":\"" + WRITE + "\"").contains("\"evaluable\":false");

		TestClient.Response response = client.post(attempt(WRITE), PRINT3);
		assertThat(response.status()).isEqualTo(503);
		assertThat(response.json("code")).isEqualTo("RUNNER_UNAVAILABLE");
		assertThat(CatalogTestSupport.count(jdbc, "select count(*) from exercise_attempt")).isZero();
	}

	@Test
	void withARunnerWriteCodeBecomesEvaluable() {
		runner.available = true;
		TestClient client = newUser();

		String list = client.getInLanguage("/v1/lessons/fx.u01.l01/exercises", "es").body();
		assertThat(list).doesNotContain("\"evaluable\":false");
	}

	@Test
	void aProgramWithTheExpectedOutputSolvesTheExercise() {
		runner.available = true;
		runner.behaviour = r -> FakeCodeExecutor.printing(1, "3\n");
		TestClient client = newUser();

		TestClient.Response response = client.post(attempt(WRITE), PRINT3);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.body()).contains("\"correct\":true").contains("\"solved\":true")
				.contains("Basta un print.").contains("\"passed\":true");
		assertThat(runner.lastRequest.code()).isEqualTo("print(3)");
		// El código de lenguaje del curso es el que se envía al runner (el fixture usa "fx").
		assertThat(runner.lastRequest.language()).isEqualTo("fx");
		assertThat(runner.lastRequest.stdins()).containsExactly("");
	}

	@Test
	void aWrongOutputIsRecordedWithItsTagAndShowsWhatTheProgramPrinted() {
		runner.available = true;
		runner.behaviour = r -> FakeCodeExecutor.printing(1, "4\n");
		TestClient client = newUser();

		TestClient.Response response = client.post(attempt(WRITE), answer("{\"code\":\"print(4)\"}"));

		assertThat(response.body()).contains("\"correct\":false").contains("\"solved\":false")
				.contains("\"expected\":\"3\\n\"").contains("\"actual\":\"4\\n\"").doesNotContain("Basta un print.");
		assertThat(CatalogTestSupport.count(jdbc,
				"select count(*) from exercise_attempt where error_tag = 'code.wrong_output'")).isEqualTo(1);
	}

	@Test
	void aCrashShowsTheProgramsOwnErrorMessage() {
		runner.available = true;
		runner.behaviour = r -> new CodeExecutor.Result(List.of(new CodeExecutor.CaseResult("",
				"Traceback...\nNameError: name 'x' is not defined\n", 1, false, false)), List.of());
		TestClient client = newUser();

		TestClient.Response response = client.post(attempt(WRITE), answer("{\"code\":\"print(x)\"}"));

		assertThat(response.body()).contains("\"correct\":false").contains("NameError: name 'x' is not defined");
		assertThat(CatalogTestSupport.count(jdbc,
				"select count(*) from exercise_attempt where error_tag = 'code.runtime_error'")).isEqualTo(1);
	}

	@Test
	void aBusyRunnerAnswers503WithRetryAfterAndDoesNotCountTheAttempt() {
		runner.available = true;
		runner.behaviour = r -> {
			throw new CodeExecutor.Unavailable("ocupado", true);
		};
		TestClient client = newUser();

		TestClient.Response response = client.post(attempt(WRITE), PRINT3);

		assertThat(response.status()).isEqualTo(503);
		assertThat(response.json("code")).isEqualTo("RUNNER_BUSY");
		assertThat(response.headers().firstValue("Retry-After")).contains("5");
		assertThat(CatalogTestSupport.count(jdbc, "select count(*) from exercise_attempt")).isZero();
	}

	@Test
	void aFailingRunnerAnswers503WithoutLeakingDetails() {
		runner.available = true;
		runner.behaviour = r -> {
			throw new CodeExecutor.Unavailable("detalle interno del runner", false);
		};
		TestClient client = newUser();

		TestClient.Response response = client.post(attempt(WRITE), PRINT3);

		assertThat(response.status()).isEqualTo(503);
		assertThat(response.json("code")).isEqualTo("RUNNER_UNAVAILABLE");
		assertThat(response.body()).doesNotContain("detalle interno");
	}

	@Test
	void rejectsMissingOrOversizedCodeWithoutCallingTheRunner() {
		runner.available = true;
		runner.behaviour = r -> FakeCodeExecutor.printing(1, "3\n");
		TestClient client = newUser();

		assertThat(client.post(attempt(WRITE), answer("{\"text\":\"print(3)\"}")).status()).isEqualTo(400);
		assertThat(client.post(attempt(WRITE), answer("{\"code\":\"   \"}")).status()).isEqualTo(400);
		assertThat(client.post(attempt(WRITE), answer("{\"code\":\"" + "x".repeat(10_001) + "\"}")).status())
				.isEqualTo(400);
		assertThat(client.post(attempt(WRITE), answer("{\"code\":5}")).status()).isEqualTo(400);

		assertThat(runner.lastRequest).isNull();
		assertThat(CatalogTestSupport.count(jdbc, "select count(*) from exercise_attempt")).isZero();
	}

	@Test
	void codeRunsAreRateLimitedMoreStrictlyThanOtherRequests() {
		runner.available = true;
		runner.behaviour = r -> FakeCodeExecutor.printing(1, "4\n");
		TestClient client = newUser();

		for (int i = 0; i < 10; i++) {
			assertThat(client.post(attempt(WRITE), answer("{\"code\":\"print(4)\"}")).status()).isEqualTo(200);
		}
		TestClient.Response blocked = client.post(attempt(WRITE), answer("{\"code\":\"print(4)\"}"));

		assertThat(blocked.status()).isEqualTo(429);
		// Las demás peticiones del mismo usuario siguen funcionando.
		assertThat(client.post(attempt(MC), answer("{\"choice\":0}")).status()).isEqualTo(200);
	}

	@Test
	void hintsAreRevealedOneAtATimeAndRemembered() {
		TestClient client = newUser();

		TestClient.Response first = client.post("/v1/exercises/" + MC + "/hints", "{}");
		assertThat(first.body()).contains("\"level\":1").contains("\"total\":2").contains("Cuenta con los dedos.")
				.contains("\"last\":false");
		TestClient.Response second = client.post("/v1/exercises/" + MC + "/hints", "{}");
		assertThat(second.body()).contains("\"level\":2").contains("Es un número par.").contains("\"last\":true");
		TestClient.Response beyond = client.post("/v1/exercises/" + MC + "/hints", "{}");
		assertThat(beyond.body()).contains("\"level\":2").contains("Es un número par.");

		String list = client.getInLanguage("/v1/lessons/fx.u01.l01/exercises", "es").body();
		assertThat(list).contains("\"revealedHints\":[\"Cuenta con los dedos.\",\"Es un número par.\"]");
		assertThat(client.post(attempt(MC), answer("{\"choice\":0}")).body()).contains("\"hintsAvailable\":0");

		assertThat(client.post("/v1/exercises/" + PREDICT + "/hints", "{}").body()).contains("\"last\":true");
	}

	@Test
	void eachUserHasTheirOwnProgress() {
		TestClient ana = newUser();
		TestClient luis = newUser();
		ana.post(attempt(MC), answer("{\"choice\":1}"));
		ana.post("/v1/exercises/" + PREDICT + "/hints", "{}");

		String seenByLuis = luis.getInLanguage("/v1/lessons/fx.u01.l01/exercises", "es").body();

		assertThat(seenByLuis).doesNotContain("\"solved\":true").contains("\"revealedHints\":[]");
		assertThat(seenByLuis).doesNotContain("Dos más dos son cuatro");
	}

	@Test
	void unknownExercisesAndLessonsAreNotFound() {
		TestClient client = newUser();

		assertThat(client.get("/v1/lessons/no.existe/exercises").status()).isEqualTo(404);
		assertThat(client.post(attempt("no.existe"), answer("{\"choice\":0}")).status()).isEqualTo(404);
		assertThat(client.post("/v1/exercises/no.existe/hints", "{}").status()).isEqualTo(404);
	}

	@Test
	void aLessonWithoutExercisesReturnsAnEmptyList() {
		TestClient client = newUser();

		TestClient.Response response = client.get("/v1/lessons/fx.u01.l02/exercises");

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("[]");
	}

	@Test
	void requestsAreRateLimitedPerUser() {
		TestClient client = newUser();

		for (int i = 0; i < 60; i++) {
			assertThat(client.post(attempt(MC), answer("{\"choice\":0}")).status()).isEqualTo(200);
		}
		TestClient.Response blocked = client.post(attempt(MC), answer("{\"choice\":0}"));

		assertThat(blocked.status()).isEqualTo(429);
		assertThat(blocked.headers().firstValue("Retry-After")).isPresent();
		// Otro usuario no se ve afectado.
		assertThat(newUser().post(attempt(MC), answer("{\"choice\":0}")).status()).isEqualTo(200);
	}

	@Test
	void deletingTheAccountDeletesTheAttemptsAndState() {
		TestClient client = newUser();
		String userId = client.get("/v1/auth/me").json("id");
		client.post(attempt(MC), answer("{\"choice\":1}"));
		client.post("/v1/exercises/" + MC + "/hints", "{}");
		assertThat(attemptsOf(userId, MC)).isEqualTo(1);

		assertThat(client.delete("/v1/me", "{\"password\":\"" + PASSWORD + "\"}").status()).isEqualTo(204);

		assertThat(CatalogTestSupport.count(jdbc, "select count(*) from exercise_attempt where user_id = ?::uuid", userId))
				.isZero();
		assertThat(CatalogTestSupport.count(jdbc, "select count(*) from exercise_state where user_id = ?::uuid", userId))
				.isZero();
	}

}
