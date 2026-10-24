package app.plataforma.api.catalog.internal;

import static app.plataforma.api.catalog.internal.CatalogTestSupport.count;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import app.plataforma.api.IntegrationTest;

/** El importador contra PostgreSQL real: idempotencia, cambios, archivado y atomicidad. */
class ContentImporterTests extends IntegrationTest {

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
	}

	@AfterEach
	void tearDown() {
		CatalogTestSupport.clean(jdbc);
	}

	private ImportReport importAll(ContentStatus min) {
		return importer.importFrom(tmp, min);
	}

	@Test
	void importsTheWholeCatalogWithBothLanguages() {
		ImportReport report = importAll(ContentStatus.PUBLISHED);

		assertThat(report.courses()).isEqualTo(1);
		assertThat(report.units()).isEqualTo(2);
		assertThat(report.skills()).isEqualTo(3);
		assertThat(report.lessonsCreated()).isEqualTo(3);
		assertThat(count(jdbc, "select count(*) from lesson where status = 'PUBLISHED'")).isEqualTo(3);
		assertThat(count(jdbc, "select count(*) from lesson_translation")).isEqualTo(6);
		assertThat(count(jdbc, "select count(*) from unit_translation")).isEqualTo(4);
		assertThat(count(jdbc, "select count(*) from skill_prerequisite")).isEqualTo(2);
		assertThat(count(jdbc, "select count(*) from lesson_skill")).isEqualTo(3);
		assertThat(jdbc.queryForObject("select body_md from lesson_translation lt join lesson l on l.id = lt.lesson_id "
				+ "where l.content_id = 'fx.u01.l01' and lt.locale = 'en'", String.class)).contains("Body in English");
	}

	@Test
	void importingTwiceChangesNothing() {
		importAll(ContentStatus.PUBLISHED);

		ImportReport second = importAll(ContentStatus.PUBLISHED);

		assertThat(second.lessonsCreated()).isZero();
		assertThat(second.lessonsUpdated()).isZero();
		assertThat(second.lessonsUnchanged()).isEqualTo(3);
		assertThat(second.lessonsArchived()).isZero();
		assertThat(count(jdbc, "select count(*) from lesson")).isEqualTo(3);
		assertThat(count(jdbc, "select count(*) from skill")).isEqualTo(3);
	}

	@Test
	void anEditedLessonIsUpdatedInPlaceKeepingItsId() {
		importAll(ContentStatus.PUBLISHED);
		String idBefore = jdbc.queryForObject("select id::text from lesson where content_id = 'fx.u01.l01'",
				String.class);

		fixture.replaceInFile("fx/u01-uno/01-a.yaml", "Body in English", "Edited body");
		ImportReport report = importAll(ContentStatus.PUBLISHED);

		assertThat(report.lessonsUpdated()).isEqualTo(1);
		assertThat(report.lessonsUnchanged()).isEqualTo(2);
		assertThat(jdbc.queryForObject("select id::text from lesson where content_id = 'fx.u01.l01'", String.class))
				.isEqualTo(idBefore);
		assertThat(jdbc.queryForObject("select body_md from lesson_translation lt join lesson l on l.id = lt.lesson_id "
				+ "where l.content_id = 'fx.u01.l01' and lt.locale = 'en'", String.class)).contains("Edited body");
	}

	@Test
	void aLessonRemovedFromTheContentIsArchivedNotDeleted() {
		importAll(ContentStatus.PUBLISHED);

		fixture.delete("fx/u01-uno/02-b.yaml");
		ImportReport report = importAll(ContentStatus.PUBLISHED);

		assertThat(report.lessonsArchived()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select status from lesson where content_id = 'fx.u01.l02'", String.class))
				.isEqualTo("ARCHIVED");
		assertThat(count(jdbc, "select count(*) from lesson")).isEqualTo(3);
	}

	@Test
	void anArchivedLessonIsRestoredWhenItReturns() {
		importAll(ContentStatus.PUBLISHED);
		String original = fixture.read("fx/u01-uno/02-b.yaml");
		fixture.delete("fx/u01-uno/02-b.yaml");
		importAll(ContentStatus.PUBLISHED);

		fixture.write("fx/u01-uno/02-b.yaml", original);
		ImportReport report = importAll(ContentStatus.PUBLISHED);

		assertThat(report.lessonsUpdated()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select status from lesson where content_id = 'fx.u01.l02'", String.class))
				.isEqualTo("PUBLISHED");
	}

	@Test
	void theMinimumStatusKeepsDraftsOutAndArchivesLessonsThatLoseTheirStatus() {
		fixture.replaceInFile("fx/u01-uno/02-b.yaml", "status: published", "status: draft");

		ImportReport published = importAll(ContentStatus.PUBLISHED);
		assertThat(published.lessonsCreated()).isEqualTo(2);
		assertThat(published.lessonsSkippedByStatus()).isEqualTo(1);
		assertThat(count(jdbc, "select count(*) from lesson")).isEqualTo(2);

		ImportReport draft = importAll(ContentStatus.DRAFT);
		assertThat(draft.lessonsCreated()).isEqualTo(1);
		assertThat(draft.lessonsUnchanged()).isEqualTo(2);

		ImportReport again = importAll(ContentStatus.PUBLISHED);
		assertThat(again.lessonsArchived()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select status from lesson where content_id = 'fx.u01.l02'", String.class))
				.isEqualTo("ARCHIVED");
	}

	@Test
	void aUnitWithoutImportableLessonsIsArchived() {
		importAll(ContentStatus.PUBLISHED);

		fixture.replaceInFile("fx/u02-dos/01-c.yaml", "status: published", "status: draft");
		ImportReport report = importAll(ContentStatus.PUBLISHED);

		assertThat(report.units()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select archived from unit where content_id = 'fx.u02'", Boolean.class))
				.isTrue();
		assertThat(jdbc.queryForObject("select archived from unit where content_id = 'fx.u01'", Boolean.class))
				.isFalse();
	}

	@Test
	void whenNothingIsImportableTheCourseIsArchived() {
		importAll(ContentStatus.DRAFT);
		for (String file : new String[] { "fx/u01-uno/01-a.yaml", "fx/u01-uno/02-b.yaml", "fx/u02-dos/01-c.yaml" }) {
			fixture.replaceInFile(file, "status: published", "status: draft");
		}

		ImportReport report = importAll(ContentStatus.PUBLISHED);

		assertThat(report.courses()).isZero();
		assertThat(report.lessonsArchived()).isEqualTo(3);
		assertThat(jdbc.queryForObject("select archived from course where content_id = 'fx.course'", Boolean.class))
				.isTrue();
	}

	@Test
	void invalidContentWritesNothingAndKeepsWhatWasThere() {
		fixture.replaceInFile("fx/u01-uno/01-a.yaml", "skills: [fx.a]", "skills: [fx.nope]");
		assertThatThrownBy(() -> importAll(ContentStatus.PUBLISHED)).isInstanceOf(ContentValidationException.class)
				.hasMessageContaining("fx.nope");
		assertThat(count(jdbc, "select count(*) from lesson")).isZero();
		assertThat(count(jdbc, "select count(*) from skill")).isZero();
		assertThat(count(jdbc, "select count(*) from programming_language")).isZero();

		fixture.replaceInFile("fx/u01-uno/01-a.yaml", "skills: [fx.nope]", "skills: [fx.a]");
		importAll(ContentStatus.PUBLISHED);
		fixture.delete("fx/u01-uno/02-b.yaml");
		fixture.replaceInFile("fx/u01-uno/01-a.yaml", "skills: [fx.a]", "skills: [fx.nope]");
		assertThatThrownBy(() -> importAll(ContentStatus.PUBLISHED)).isInstanceOf(ContentValidationException.class);

		// El contenido roto no archivó la lección que faltaba: la base de datos sigue como estaba.
		assertThat(count(jdbc, "select count(*) from lesson where status = 'PUBLISHED'")).isEqualTo(3);
	}

	@Test
	void importsExercisesWithTranslationsSkillsAndServerOnlySolutions() {
		fixture.setExercises("fx/u01-uno/01-a.yaml", FixtureContent.ALL_TYPES);

		ImportReport report = importAll(ContentStatus.PUBLISHED);

		assertThat(report.exercises()).isEqualTo(5);
		assertThat(count(jdbc, "select count(*) from exercise")).isEqualTo(5);
		assertThat(count(jdbc, "select count(*) from exercise_translation")).isEqualTo(10);
		assertThat(count(jdbc, "select count(*) from exercise_skill")).isEqualTo(5);
		assertThat(jdbc.queryForObject("select type from exercise where content_id = 'fx.u01.l01.e04'", String.class))
				.isEqualTo("FILL_CODE");
		assertThat(jdbc.queryForObject("select solution->>'answer' from exercise where content_id = 'fx.u01.l01.e01'",
				String.class)).isEqualTo("1");
		assertThat(jdbc.queryForObject("select config->>'blankCount' from exercise where content_id = 'fx.u01.l01.e04'",
				String.class)).isEqualTo("2");
		assertThat(jdbc.queryForObject("select options->>1 from exercise_translation t join exercise e on e.id = t.exercise_id "
				+ "where e.content_id = 'fx.u01.l01.e01' and t.locale = 'en'", String.class)).isEqualTo("Four");
		assertThat(jdbc.queryForObject("select hints->>0 from exercise_translation t join exercise e on e.id = t.exercise_id "
				+ "where e.content_id = 'fx.u01.l01.e01' and t.locale = 'es'", String.class))
				.isEqualTo("Cuenta con los dedos.");
	}

	@Test
	void anEditedExerciseUpdatesInPlaceAndARemovedOneIsArchived() {
		fixture.setExercises("fx/u01-uno/01-a.yaml", FixtureContent.ALL_TYPES);
		importAll(ContentStatus.PUBLISHED);
		String idBefore = jdbc.queryForObject("select id::text from exercise where content_id = 'fx.u01.l01.e01'",
				String.class);

		fixture.replaceInFile("fx/u01-uno/01-a.yaml", "answer: 1", "answer: 2");
		ImportReport edited = importAll(ContentStatus.PUBLISHED);
		assertThat(edited.lessonsUpdated()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select id::text from exercise where content_id = 'fx.u01.l01.e01'",
				String.class)).isEqualTo(idBefore);
		assertThat(jdbc.queryForObject("select solution->>'answer' from exercise where content_id = 'fx.u01.l01.e01'",
				String.class)).isEqualTo("2");

		// Sin ejercicios en el archivo, los cinco se archivan (no se borran, por los intentos que ya tengan).
		fixture.write("fx/u01-uno/01-a.yaml",
				FixtureContent.lesson("fx.u01.l01", "fx.u01", 1, "published", "[fx.a]", "Lección A", "Lesson A"));
		importAll(ContentStatus.PUBLISHED);
		assertThat(count(jdbc, "select count(*) from exercise where archived")).isEqualTo(5);
		assertThat(count(jdbc, "select count(*) from exercise")).isEqualTo(5);
	}

	@Test
	void archivingALessonArchivesItsExercises() {
		fixture.setExercises("fx/u01-uno/01-a.yaml", FixtureContent.ALL_TYPES);
		importAll(ContentStatus.PUBLISHED);

		fixture.delete("fx/u01-uno/01-a.yaml");
		fixture.replaceInFile("fx/u01-uno/02-b.yaml", "position: 2", "position: 1");
		importAll(ContentStatus.PUBLISHED);

		assertThat(count(jdbc, "select count(*) from exercise where not archived")).isZero();
	}

	@Test
	void aLessonCanMoveToAnotherUnit() {
		importAll(ContentStatus.PUBLISHED);

		fixture.delete("fx/u01-uno/02-b.yaml");
		fixture.write("fx/u02-dos/02-b.yaml",
				FixtureContent.lesson("fx.u01.l02", "fx.u02", 2, "published", "[fx.b]", "Lección B", "Lesson B"));
		ImportReport report = importAll(ContentStatus.PUBLISHED);

		assertThat(report.lessonsUpdated()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select u.content_id from lesson l join unit u on u.id = l.unit_id "
				+ "where l.content_id = 'fx.u01.l02'", String.class)).isEqualTo("fx.u02");
	}

	@Test
	void prerequisitesFollowTheContent() {
		importAll(ContentStatus.PUBLISHED);

		fixture.replaceInFile("fx/skills.yaml", "requires: [fx.b]", "requires: [fx.a]");
		importAll(ContentStatus.PUBLISHED);

		assertThat(count(jdbc, "select count(*) from skill_prerequisite sp join skill s on s.id = sp.skill_id "
				+ "join skill p on p.id = sp.prerequisite_id where s.content_id = 'fx.c' and p.content_id = 'fx.a'"))
				.isEqualTo(1);
		assertThat(count(jdbc, "select count(*) from skill_prerequisite")).isEqualTo(2);
	}

}
