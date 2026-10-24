package app.plataforma.api.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import app.plataforma.api.IntegrationTest;
import app.plataforma.api.TestClient;

/** El catálogo por HTTP, como lo usa el navegador. */
class CatalogApiTests extends IntegrationTest {

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private ContentImporter importer;

	@Autowired
	private JdbcTemplate jdbc;

	@TempDir
	Path tmp;

	private FixtureContent fixture;

	@BeforeEach
	void setUp() {
		CatalogTestSupport.clean(jdbc);
		fixture = new FixtureContent(tmp);
		importer.importFrom(tmp, ContentStatus.PUBLISHED);
	}

	@AfterEach
	void tearDown() {
		CatalogTestSupport.clean(jdbc);
	}

	private TestClient loggedIn() {
		TestClient client = new TestClient(port);
		client.primeCsrf();
		String email = "catalog-" + UUID.randomUUID() + "@example.com";
		String body = """
				{"email":"%s","password":"contrasena-larga-1","displayName":"Ana","locale":"es","acceptTerms":true}"""
				.formatted(email);
		assertThat(client.post("/v1/auth/register", body).status()).isEqualTo(201);
		return client;
	}

	@Test
	void theCatalogRequiresASession() {
		TestClient anonymous = new TestClient(port);

		assertThat(anonymous.get("/v1/catalog/courses").status()).isEqualTo(401);
		assertThat(anonymous.get("/v1/catalog/courses/fx.course").status()).isEqualTo(401);
		assertThat(anonymous.get("/v1/catalog/lessons/fx.u01.l01").status()).isEqualTo(401);
	}

	@Test
	void listsCoursesInTheRequestedLanguage() {
		TestClient client = loggedIn();

		TestClient.Response es = client.getInLanguage("/v1/catalog/courses", "es-PE,es;q=0.9");
		assertThat(es.status()).isEqualTo(200);
		assertThat(es.body()).contains("\"id\":\"fx.course\"").contains("\"title\":\"Curso de prueba\"")
				.contains("\"languageName\":\"Fixture\"").contains("\"unitCount\":2").contains("\"lessonCount\":3");
		assertThat(es.headers().firstValue("Vary").orElse("")).contains("Accept-Language");

		TestClient.Response en = client.getInLanguage("/v1/catalog/courses", "en-US,en;q=0.9");
		assertThat(en.body()).contains("\"title\":\"Test course\"").contains("\"description\":\"Test description\"");
	}

	@Test
	void theCourseMapListsUnitsAndLessonsInOrderWithTheirSkills() {
		TestClient.Response response = loggedIn().getInLanguage("/v1/catalog/courses/fx.course", "en");

		assertThat(response.status()).isEqualTo(200);
		String body = response.body();
		assertThat(body).contains("\"title\":\"Unit one\"").contains("\"title\":\"Unit two\"")
				.contains("\"title\":\"Lesson A\"").contains("\"name\":\"Skill B\"");
		assertThat(body.indexOf("Unit one")).isLessThan(body.indexOf("Unit two"));
		assertThat(body.indexOf("Lesson A")).isLessThan(body.indexOf("Lesson B"));
		assertThat(body.indexOf("Lesson B")).isLessThan(body.indexOf("Lesson C"));
		assertThat(body).contains("\"status\":\"PUBLISHED\"");
	}

	@Test
	void aLessonComesWithItsMarkdownAndNeighbours() {
		TestClient client = loggedIn();

		TestClient.Response first = client.getInLanguage("/v1/catalog/lessons/fx.u01.l01", "es");
		assertThat(first.status()).isEqualTo(200);
		assertThat(first.body()).contains("\"id\":\"fx.u01.l01\"").contains("\"title\":\"Lección A\"")
				.contains("## Lección A").contains("\"courseId\":\"fx.course\"").contains("\"unitId\":\"fx.u01\"")
				.contains("\"previous\":null").contains("\"next\":{\"id\":\"fx.u01.l02\"");

		TestClient.Response middle = client.getInLanguage("/v1/catalog/lessons/fx.u01.l02", "en");
		assertThat(middle.body()).contains("Body in English").contains("\"previous\":{\"id\":\"fx.u01.l01\"")
				.contains("\"next\":{\"id\":\"fx.u02.l01\"");

		TestClient.Response last = client.get("/v1/catalog/lessons/fx.u02.l01");
		assertThat(last.body()).contains("\"next\":null").contains("\"previous\":{\"id\":\"fx.u01.l02\"");
	}

	@Test
	void unknownCoursesAndLessonsAreNotFound() {
		TestClient client = loggedIn();

		TestClient.Response lesson = client.get("/v1/catalog/lessons/no.existe");
		assertThat(lesson.status()).isEqualTo(404);
		assertThat(lesson.json("code")).isEqualTo("NOT_FOUND");
		assertThat(client.get("/v1/catalog/courses/no.existe").status()).isEqualTo(404);
		assertThat(client.get("/v1/catalog/courses/no.existe/skills").status()).isEqualTo(404);
	}

	@Test
	void archivedLessonsDisappearFromEveryEndpoint() {
		fixture.delete("fx/u01-uno/02-b.yaml");
		importer.importFrom(tmp, ContentStatus.PUBLISHED);
		TestClient client = loggedIn();

		assertThat(client.get("/v1/catalog/lessons/fx.u01.l02").status()).isEqualTo(404);
		TestClient.Response course = client.getInLanguage("/v1/catalog/courses/fx.course", "en");
		assertThat(course.body()).doesNotContain("Lesson B");
		assertThat(client.get("/v1/catalog/courses").body()).contains("\"lessonCount\":2");
		// La lección siguiente a la A pasa a ser la primera de la unidad dos.
		assertThat(client.getInLanguage("/v1/catalog/lessons/fx.u01.l01", "en").body())
				.contains("\"next\":{\"id\":\"fx.u02.l01\"");
	}

	@Test
	void exposesTheSkillGraphWithPrerequisites() {
		TestClient.Response response = loggedIn().getInLanguage("/v1/catalog/courses/fx.course/skills", "en");

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.body()).contains("{\"id\":\"fx.a\",\"name\":\"Skill A\",\"requires\":[]}")
				.contains("{\"id\":\"fx.b\",\"name\":\"Skill B\",\"requires\":[\"fx.a\"]}")
				.contains("{\"id\":\"fx.c\",\"name\":\"Skill C\",\"requires\":[\"fx.b\"]}");
	}

}
