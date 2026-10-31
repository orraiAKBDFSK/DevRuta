package app.plataforma.api.learning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
import app.plataforma.api.OffsetClock;
import app.plataforma.api.TestClient;
import app.plataforma.api.catalog.internal.CatalogTestSupport;
import app.plataforma.api.catalog.internal.FixtureContent;

/** XP, rachas y logros por HTTP, contra PostgreSQL real. Dificultades del fixture: e01 = 1, e02 = 2, e03 = 2, e04 = 3. */
class ProgressApiTests extends IntegrationTest {

	private static final String MC = "fx.u01.l01.e01";

	private static final String PREDICT = "fx.u01.l01.e02";

	private static final String ORDER = "fx.u01.l01.e03";

	private static final String FILL = "fx.u01.l01.e04";

	private static final String WRITE = "fx.u01.l01.e05";

	private static final String RIGHT_MC = "{\"answer\":{\"choice\":1}}";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private ApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private FakeCodeExecutor runner;

	@Autowired
	private OffsetClock clock;

	@TempDir
	Path tmp;

	@BeforeEach
	void setUp() {
		runner.reset();
		clock.reset();
		CatalogTestSupport.clean(jdbc);
		new FixtureContent(tmp).setExercises("fx/u01-uno/01-a.yaml", FixtureContent.ALL_TYPES);
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
				{"email":"prog-%s@example.com","password":"contrasena-larga-1","displayName":"Ana","locale":"es","acceptTerms":true}"""
				.formatted(UUID.randomUUID());
		assertThat(client.post("/v1/auth/register", body).status()).isEqualTo(201);
		return client;
	}

	private static String attempt(String id) {
		return "/v1/exercises/" + id + "/attempts";
	}

	private static String order() {
		return "{\"answer\":{\"order\":[\"x = 1\",\"y = 2\",\"print(x + y)\"]}}";
	}

	private static String fill() {
		return "{\"answer\":{\"blanks\":[\"1\",\"2\"]}}";
	}

	private static String predict() {
		return "{\"answer\":{\"text\":\"a\\nb\"}}";
	}

	private int xp(TestClient client) {
		return Integer.parseInt(client.get("/v1/progress").raw("xp"));
	}

	@Test
	void progressRequiresASession() {
		TestClient anonymous = new TestClient(port);
		anonymous.primeCsrf();

		assertThat(anonymous.get("/v1/progress").status()).isEqualTo(401);
	}

	@Test
	void aNewUserStartsAtLevelOneWithNothing() {
		TestClient.Response response = newUser().get("/v1/progress");

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.raw("xp")).isEqualTo("0");
		assertThat(response.raw("level")).isEqualTo("1");
		assertThat(response.raw("streak")).isEqualTo("0");
		assertThat(response.body()).contains("\"achievements\":[]").contains("\"completedLessons\":[]");
		assertThat(response.headers().firstValue("Cache-Control")).hasValueSatisfying(v -> assertThat(v).contains("no-store"));
	}

	@Test
	void solvingAnExerciseGivesXpByDifficultyAndTheFirstAchievement() {
		TestClient client = newUser();

		TestClient.Response solved = client.post(attempt(PREDICT), predict());

		assertThat(solved.body()).contains("\"xpEarned\":20").contains("\"first_solve\"");
		TestClient.Response progress = client.get("/v1/progress");
		assertThat(progress.raw("xp")).isEqualTo("20");
		assertThat(progress.raw("streak")).isEqualTo("1");
		assertThat(progress.raw("solvedExercises")).isEqualTo("1");
		assertThat(progress.body()).contains("\"activeToday\":true").contains("\"code\":\"first_solve\"");
	}

	@Test
	void wrongAnswersGiveNothing() {
		TestClient client = newUser();

		TestClient.Response wrong = client.post(attempt(MC), "{\"answer\":{\"choice\":0}}");

		assertThat(wrong.body()).contains("\"correct\":false").doesNotContain("\"xpEarned\":1");
		assertThat(xp(client)).isZero();
		assertThat(client.get("/v1/progress").raw("streak")).isEqualTo("0");
	}

	@Test
	void repeatingACorrectAnswerNeverGivesXpTwice() {
		TestClient client = newUser();

		client.post(attempt(MC), RIGHT_MC);
		TestClient.Response again = client.post(attempt(MC), RIGHT_MC);

		assertThat(again.body()).contains("\"correct\":true").contains("\"xpEarned\":null");
		assertThat(xp(client)).isEqualTo(10);
		assertThat(CatalogTestSupport.count(jdbc, "select count(*) from xp_event")).isEqualTo(1);
	}

	@Test
	void usingHintsReducesTheXp() {
		TestClient client = newUser();
		client.post("/v1/exercises/" + MC + "/hints", "{}");

		TestClient.Response solved = client.post(attempt(MC), RIGHT_MC);

		assertThat(solved.body()).contains("\"xpEarned\":8");
		assertThat(xp(client)).isEqualTo(8);
	}

	@Test
	void twoSimultaneousCorrectSubmissionsGiveXpOnce() {
		TestClient client = newUser();

		List<CompletableFuture<Integer>> calls = List.of(
				CompletableFuture.supplyAsync(() -> client.post(attempt(MC), RIGHT_MC).status()),
				CompletableFuture.supplyAsync(() -> client.post(attempt(MC), RIGHT_MC).status()),
				CompletableFuture.supplyAsync(() -> client.post(attempt(MC), RIGHT_MC).status()));
		calls.forEach(c -> assertThat(c.join()).isEqualTo(200));

		assertThat(xp(client)).isEqualTo(10);
		assertThat(CatalogTestSupport.count(jdbc, "select count(*) from xp_event")).isEqualTo(1);
	}

	@Test
	void theStreakGrowsDayByDayBreaksAfterAGapAndKeepsTheRecord() {
		TestClient client = newUser();

		client.post(attempt(MC), RIGHT_MC);
		assertThat(client.get("/v1/progress").raw("streak")).isEqualTo("1");

		clock.advance(Duration.ofDays(1));
		client.post(attempt(PREDICT), predict());
		TestClient.Response second = client.get("/v1/progress");
		assertThat(second.raw("streak")).isEqualTo("2");
		assertThat(second.raw("longestStreak")).isEqualTo("2");

		// Un día sin practicar todavía no rompe la racha, pero dos sí; se ve en cuanto pasa el plazo, sin esperar a resolver.
		clock.advance(Duration.ofDays(1));
		assertThat(client.get("/v1/progress").raw("streak")).isEqualTo("2");
		assertThat(client.get("/v1/progress").body()).contains("\"activeToday\":false");
		clock.advance(Duration.ofDays(1));
		assertThat(client.get("/v1/progress").raw("streak")).isEqualTo("0");

		client.post(attempt(ORDER), order());
		TestClient.Response restarted = client.get("/v1/progress");
		assertThat(restarted.raw("streak")).isEqualTo("1");
		assertThat(restarted.raw("longestStreak")).isEqualTo("2");
	}

	@Test
	void threeDaysInARowUnlockTheStreakAchievement() {
		TestClient client = newUser();
		String[][] daily = { { MC, RIGHT_MC }, { PREDICT, predict() }, { ORDER, order() }, { FILL, fill() } };

		client.post(attempt(daily[0][0]), daily[0][1]);
		clock.advance(Duration.ofDays(1));
		client.post(attempt(daily[1][0]), daily[1][1]);
		clock.advance(Duration.ofDays(1));
		TestClient.Response third = client.post(attempt(daily[2][0]), daily[2][1]);

		assertThat(third.body()).contains("\"streak_3\"");
		assertThat(client.get("/v1/progress").body()).contains("\"code\":\"streak_3\"").doesNotContain("streak_7");
	}

	@Test
	void finishingEveryExerciseOfALessonCompletesItAndUnlocksTheAchievementOnce() {
		runner.available = true;
		runner.behaviour = r -> FakeCodeExecutor.printing(1, "3\n");
		TestClient client = newUser();

		client.post(attempt(MC), RIGHT_MC);
		client.post(attempt(PREDICT), predict());
		client.post(attempt(ORDER), order());
		assertThat(client.get("/v1/progress").body()).contains("\"completedLessons\":[]");
		client.post(attempt(FILL), fill());
		TestClient.Response last = client.post(attempt(WRITE), "{\"answer\":{\"code\":\"print(3)\"}}");

		assertThat(last.body()).contains("\"lesson_complete\"").contains("\"xpEarned\":30");
		TestClient.Response progress = client.get("/v1/progress");
		assertThat(progress.body()).contains("\"completedLessons\":[\"fx.u01.l01\"]");
		assertThat(progress.raw("xp")).isEqualTo(String.valueOf(10 + 20 + 20 + 30 + 30));
		assertThat(CatalogTestSupport.count(jdbc, "select count(*) from user_achievement where code = 'lesson_complete'"))
				.isEqualTo(1);
	}

	@Test
	void theStreakDayFollowsTheTimeZoneSentByTheBrowser() {
		TestClient utc = newUser();
		TestClient pacific = newUser();
		clock.setNow(java.time.Instant.parse("2026-10-23T23:30:00Z"));
		utc.post(attempt(MC), RIGHT_MC);
		pacific.postWithHeader(attempt(MC), RIGHT_MC, "X-Time-Zone", "America/Los_Angeles");

		// 01:30 UTC del día siguiente: en UTC es otro día, en Los Ángeles (16:30) todavía es el mismo.
		clock.advance(Duration.ofHours(2));
		utc.post(attempt(PREDICT), predict());
		pacific.postWithHeader(attempt(PREDICT), predict(), "X-Time-Zone", "America/Los_Angeles");

		assertThat(utc.get("/v1/progress").raw("streak")).isEqualTo("2");
		assertThat(pacific.getWithHeader("/v1/progress", "X-Time-Zone", "America/Los_Angeles").raw("streak"))
				.isEqualTo("1");
	}

	@Test
	void theTimeZoneSavedInTheProfileWinsOverTheHeader() {
		TestClient client = newUser();
		assertThat(client.patch("/v1/me/profile", "{\"timezone\":\"America/Los_Angeles\"}").status()).isEqualTo(200);
		clock.setNow(java.time.Instant.parse("2026-10-23T23:30:00Z"));
		client.post(attempt(MC), RIGHT_MC);

		// El navegador dice UTC, pero el perfil dice Los Ángeles: dos horas después sigue siendo el mismo día allí.
		clock.advance(Duration.ofHours(2));
		client.postWithHeader(attempt(PREDICT), predict(), "X-Time-Zone", "UTC");

		assertThat(client.getWithHeader("/v1/progress", "X-Time-Zone", "UTC").raw("streak")).isEqualTo("1");
	}

	@Test
	void anInvalidTimeZoneFallsBackToUtcInsteadOfFailing() {
		TestClient client = newUser();

		TestClient.Response solved = client.postWithHeader(attempt(MC), RIGHT_MC, "X-Time-Zone", "no/existe");

		assertThat(solved.status()).isEqualTo(200);
		assertThat(client.getWithHeader("/v1/progress", "X-Time-Zone", "x".repeat(200)).status()).isEqualTo(200);
	}

	@Test
	void usersHaveIndependentProgress() {
		TestClient ana = newUser();
		TestClient luis = newUser();

		ana.post(attempt(MC), RIGHT_MC);

		assertThat(xp(ana)).isEqualTo(10);
		assertThat(xp(luis)).isZero();
	}

	@Test
	void deletingTheAccountDeletesXpStreakAndAchievements() {
		TestClient client = newUser();
		client.post(attempt(MC), RIGHT_MC);
		assertThat(CatalogTestSupport.count(jdbc, "select count(*) from user_achievement")).isEqualTo(1);

		assertThat(client.delete("/v1/me", "{\"password\":\"contrasena-larga-1\"}").status()).isEqualTo(204);

		assertThat(CatalogTestSupport.count(jdbc, "select count(*) from xp_event")).isZero();
		assertThat(CatalogTestSupport.count(jdbc, "select count(*) from user_progress")).isZero();
		assertThat(CatalogTestSupport.count(jdbc, "select count(*) from user_achievement")).isZero();
	}

}
