package app.plataforma.api.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reglas de validación del contenido, sin base de datos. */
class ContentLoaderTests {

	@TempDir
	Path tmp;

	private FixtureContent fixture;

	@BeforeEach
	void setUp() {
		fixture = new FixtureContent(tmp);
	}

	private ContentLoader.LoadResult load() {
		return new ContentLoader().load(tmp);
	}

	private void assertProblem(String fragment) {
		List<String> problems = load().problems();
		assertThat(problems).as("problemas encontrados").anyMatch(p -> p.contains(fragment));
	}

	@Test
	void validContentLoadsWithoutProblems() {
		ContentLoader.LoadResult result = load();

		assertThat(result.problems()).isEmpty();
		assertThat(result.languages()).hasSize(1);
		ContentModel.LanguageContent fx = result.languages().getFirst();
		assertThat(fx.units()).hasSize(2);
		assertThat(fx.lessons()).hasSize(3);
		assertThat(fx.skills()).extracting(ContentModel.Skill::id).containsExactly("fx.a", "fx.b", "fx.c");
		assertThat(fx.lessons().getFirst().body().get("es")).contains("## Lección A").contains("| 1 | 2 |");
	}

	@Test
	void theHashChangesWhenTheContentChanges() {
		String before = load().languages().getFirst().lessons().getFirst().hash();

		fixture.replaceInFile("fx/u01-uno/01-a.yaml", "Lección A", "Lección A editada");

		assertThat(load().languages().getFirst().lessons().getFirst().hash()).isNotEqualTo(before).hasSize(64);
	}

	@Test
	void reportsAMissingTranslation() {
		fixture.replaceInFile("fx/u01-uno/01-a.yaml", "title: {es: 'Lección A', en: 'Lesson A'}",
				"title: {es: 'Lección A'}");

		assertProblem("no tiene texto en 'en'");
	}

	@Test
	void reportsAnUnknownSkill() {
		fixture.replaceInFile("fx/u01-uno/01-a.yaml", "skills: [fx.a]", "skills: [fx.nope]");

		assertProblem("usa la habilidad inexistente fx.nope");
	}

	@Test
	void reportsPrerequisiteCycles() {
		fixture.replaceInFile("fx/skills.yaml", "{id: fx.a, name: {es: 'Habilidad A', en: 'Skill A'}, requires: []}",
				"{id: fx.a, name: {es: 'Habilidad A', en: 'Skill A'}, requires: [fx.c]}");

		assertProblem("ciclo de prerrequisitos");
	}

	@Test
	void reportsAnUnknownPrerequisite() {
		fixture.replaceInFile("fx/skills.yaml", "requires: [fx.a]", "requires: [fx.zzz]");

		assertProblem("requiere una habilidad inexistente: fx.zzz");
	}

	@Test
	void reportsGapsInLessonPositions() {
		fixture.replaceInFile("fx/u01-uno/02-b.yaml", "position: 2", "position: 3");

		assertProblem("las posiciones de las lecciones de fx.u01 deben ser 1..2");
	}

	@Test
	void reportsGapsInUnitPositions() {
		fixture.replaceInFile("fx/u02-dos/unit.yaml", "position: 2", "position: 5");

		assertProblem("las posiciones de las unidades deben ser 1..2");
	}

	@Test
	void reportsDuplicateLessonIds() {
		fixture.replaceInFile("fx/u01-uno/02-b.yaml", "id: fx.u01.l02", "id: fx.u01.l01");

		assertProblem("lección repetida fx.u01.l01");
	}

	@Test
	void reportsALessonInTheFolderOfAnotherUnit() {
		fixture.replaceInFile("fx/u02-dos/01-c.yaml", "unit: fx.u02", "unit: fx.u01");

		assertProblem("declara la unidad fx.u01 pero su carpeta es la de fx.u02");
	}

	@Test
	void aPublishedLessonNeedsAHumanReviewer() {
		fixture.replaceInFile("fx/u01-uno/01-a.yaml", "reviewed_by: [qa]", "reviewed_by: []");

		assertProblem("necesita 'reviewed_by'");
	}

	@Test
	void reportsAnUnknownStatus() {
		fixture.replaceInFile("fx/u01-uno/01-a.yaml", "status: published", "status: listo");

		assertProblem("status desconocido 'listo'");
	}

	@Test
	void reportsInvalidYamlAndKeepsGoing() {
		fixture.write("fx/u01-uno/02-b.yaml", "id: [esto no se cierra\n");
		fixture.replaceInFile("fx/u02-dos/01-c.yaml", "skills: [fx.c]", "skills: [fx.nope]");

		List<String> problems = load().problems();

		assertThat(problems).anyMatch(p -> p.contains("02-b.yaml") && p.contains("no se puede leer el YAML"));
		assertThat(problems).anyMatch(p -> p.contains("fx.nope"));
	}

	@Test
	void reportsAMissingContentFolder() {
		assertThat(new ContentLoader().load(tmp.resolve("no-existe")).problems())
				.anyMatch(p -> p.contains("no existe"));
	}

	@Test
	void rejectsInvalidIdentifiers() {
		fixture.replaceInFile("fx/u01-uno/01-a.yaml", "id: fx.u01.l01", "id: 'Mala Id!'");

		assertProblem("no es un identificador válido");
	}

	// ---- Ejercicios ----

	private void withExercises() {
		fixture.setExercises("fx/u01-uno/01-a.yaml", FixtureContent.ALL_TYPES);
	}

	private void breakExercises(String from, String to) {
		withExercises();
		fixture.replaceInFile("fx/u01-uno/01-a.yaml", from, to);
	}

	@Test
	void loadsExercisesOfEveryTypeWithTheirData() {
		withExercises();

		ContentLoader.LoadResult result = load();

		assertThat(result.problems()).isEmpty();
		List<ContentModel.Exercise> exercises = result.languages().getFirst().lessons().getFirst().exercises();
		assertThat(exercises).extracting(ContentModel.Exercise::type).extracting(Enum::name).containsExactly(
				"MULTIPLE_CHOICE", "PREDICT_OUTPUT", "ORDER_LINES", "FILL_CODE", "WRITE_CODE");
		ContentModel.Exercise choice = exercises.get(0);
		assertThat(choice.options()).hasSize(4);
		assertThat(choice.solution()).containsEntry("answer", 1);
		assertThat(choice.solution().get("distractorTags")).isEqualTo(Map.of("0", "arith.off_by_one", "2", "arith.off_by_one"));
		assertThat(exercises.get(1).solution()).containsEntry("answer", "a\nb");
		assertThat(exercises.get(2).solution().get("lines")).isEqualTo(List.of("x = 1", "y = 2", "print(x + y)"));
		assertThat(exercises.get(3).config()).containsEntry("blankCount", 2);
		assertThat(exercises.get(4).config()).containsEntry("starter", "# tu código\n");
		assertThat(exercises.get(4).solution().get("tests")).asList().hasSize(1);
		assertThat(exercises).extracting(ContentModel.Exercise::position).containsExactly(1, 2, 3, 4, 5);
	}

	@Test
	void theHashChangesWhenAnExerciseChanges() {
		withExercises();
		String before = load().languages().getFirst().lessons().getFirst().hash();

		fixture.replaceInFile("fx/u01-uno/01-a.yaml", "answer: 1", "answer: 2");

		assertThat(load().languages().getFirst().lessons().getFirst().hash()).isNotEqualTo(before);
	}

	@Test
	void reportsAnAnswerOutsideTheOptions() {
		breakExercises("answer: 1", "answer: 9");

		assertProblem("'answer' debe ser el índice");
	}

	@Test
	void reportsAFillCodeWithTheWrongNumberOfHoles() {
		breakExercises("      - ['2']\n", "");

		assertProblem("el código tiene 2 huecos (___) y 'blanks' tiene 1");
	}

	@Test
	void reportsAnUnknownExerciseType() {
		breakExercises("type: predict_output", "type: adivinar");

		assertProblem("tipo de ejercicio desconocido 'adivinar'");
	}

	@Test
	void reportsAnInvalidDifficulty() {
		breakExercises("difficulty: 1", "difficulty: 9");

		assertProblem("'difficulty' debe ser un entero entre 1 y 5");
	}

	@Test
	void reportsAHintWithoutTranslation() {
		breakExercises("{es: 'Usa print.', en: 'Use print.'}", "{es: 'Usa print.'}");

		assertProblem("no tiene texto en 'en'");
	}

	@Test
	void reportsDuplicateExerciseIds() {
		breakExercises("id: fx.u01.l01.e02", "id: fx.u01.l01.e01");

		assertProblem("ejercicio repetido fx.u01.l01.e01");
	}

	@Test
	void reportsAnUnknownSkillInAnExercise() {
		breakExercises("skills: [fx.a]", "skills: [fx.nope]");

		assertProblem("usa la habilidad inexistente fx.nope");
	}

	@Test
	void reportsAnOrderLinesExerciseWithASingleLine() {
		breakExercises("      - 'y = 2'\n      - 'print(x + y)'\n", "");

		assertProblem("necesita al menos 2 líneas");
	}

	@Test
	void reportsAWriteCodeExerciseWithoutTests() {
		breakExercises("tests:\n      - {stdin: \"\", expected_stdout: \"3\\n\", visible: true}\n", "tests: []\n");

		assertProblem("'tests' debe ser una lista con al menos un caso");
	}

	@Test
	void reportsAPredictOutputAnswerThatIsNotText() {
		breakExercises("answer: \"a\\nb\"", "answer: 5");

		assertProblem("'answer' debe ser un texto");
	}

	@Test
	void detectsCyclesInGraphsDirectly() {
		assertThat(ContentLoader.findCycle(Map.of("a", List.of("b"), "b", List.of("a")))).isNotNull();
		assertThat(ContentLoader.findCycle(Map.of("a", List.of(), "b", List.of("a"), "c", List.of("a", "b"))))
				.isNull();
	}

}
