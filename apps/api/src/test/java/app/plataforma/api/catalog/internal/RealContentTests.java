package app.plataforma.api.catalog.internal;

import static app.plataforma.api.catalog.internal.CatalogTestSupport.count;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import app.plataforma.api.IntegrationTest;

/** Importa el contenido REAL del repositorio (content/): si alguien lo rompe, el CI falla aquí. */
class RealContentTests extends IntegrationTest {

	private static final Path REPOSITORY_CONTENT = Path.of("../../content").toAbsolutePath().normalize();

	@Autowired
	private ContentImporter importer;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		CatalogTestSupport.clean(jdbc);
	}

	@AfterEach
	void tearDown() {
		CatalogTestSupport.clean(jdbc);
	}

	@Test
	void theRepositoryContentImportsCleanlyAndCompletely() {
		ImportReport report = importer.importFrom(REPOSITORY_CONTENT, ContentStatus.DRAFT);

		assertThat(report.courses()).isEqualTo(1);
		assertThat(report.units()).isGreaterThanOrEqualTo(10);
		assertThat(report.lessonsCreated()).isGreaterThanOrEqualTo(48);
		assertThat(report.lessonsSkippedByStatus()).isZero();
		int lessons = count(jdbc, "select count(*) from lesson");
		assertThat(count(jdbc, "select count(*) from lesson_translation")).isEqualTo(lessons * 2);
		assertThat(count(jdbc, "select count(*) from lesson_skill")).isGreaterThan(lessons);
		assertThat(count(jdbc, "select count(*) from skill_prerequisite")).isGreaterThan(20);
		assertThat(count(jdbc, "select count(*) from unit u where not exists "
				+ "(select 1 from lesson l where l.unit_id = u.id)")).isZero();
	}

	@Test
	void withTheProductionMinimumOnlyReviewedLessonsGetIn() {
		// Con el mínimo de producción (PUBLISHED) solo entran lecciones publicadas, que el cargador ya exige
		// que tengan revisor humano. Mientras todo esté en borrador el catálogo queda vacío.
		ImportReport report = importer.importFrom(REPOSITORY_CONTENT, ContentStatus.PUBLISHED);

		assertThat(count(jdbc, "select count(*) from lesson where status <> 'PUBLISHED'")).isZero();
		assertThat(report.lessonsCreated() + report.lessonsSkippedByStatus()).isGreaterThanOrEqualTo(48);
	}

}
