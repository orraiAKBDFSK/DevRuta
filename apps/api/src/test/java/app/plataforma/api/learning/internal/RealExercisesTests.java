package app.plataforma.api.learning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import app.plataforma.api.IntegrationTest;
import app.plataforma.api.catalog.ExerciseCatalog;
import app.plataforma.api.catalog.ExerciseDefinition;
import app.plataforma.api.catalog.internal.CatalogTestSupport;

/**
 * Comprueba que TODOS los ejercicios reales del repositorio se importan y que el evaluador acepta la solución de cada
 * uno y rechaza una respuesta incorrecta. Así el formato del contenido y el evaluador no pueden desalinearse sin que
 * el CI lo note.
 */
class RealExercisesTests extends IntegrationTest {

	private static final Path REPOSITORY_CONTENT = Path.of("../../content").toAbsolutePath().normalize();

	@Autowired
	private ApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ExerciseCatalog catalog;

	@BeforeEach
	void setUp() {
		CatalogTestSupport.clean(jdbc);
		CatalogTestSupport.importContent(context, REPOSITORY_CONTENT, true);
	}

	@AfterEach
	void tearDown() {
		CatalogTestSupport.clean(jdbc);
	}

	@Test
	void everyRealExerciseIsImportedAndEveryStaticOneAcceptsItsOwnSolution() {
		List<String> lessons = jdbc.queryForList("select content_id from lesson where status <> 'ARCHIVED' order by 1",
				String.class);
		int evaluated = 0;
		int needRunner = 0;
		for (String lesson : lessons) {
			List<ExerciseDefinition> spanish = catalog.exercisesOfLesson(lesson, "es").orElseThrow();
			List<ExerciseDefinition> english = catalog.exercisesOfLesson(lesson, "en").orElseThrow();
			assertThat(english).as("ejercicios en inglés de " + lesson).hasSameSizeAs(spanish);
			for (int i = 0; i < spanish.size(); i++) {
				ExerciseDefinition exercise = spanish.get(i);
				String where = exercise.id();
				assertThat(exercise.prompt()).as(where).isNotBlank();
				assertThat(english.get(i).prompt()).as(where + " (en)").isNotBlank();
				assertThat(exercise.explanation()).as(where).isNotBlank();
				assertThat(exercise.hints()).as(where).isNotEmpty();
				assertThat(english.get(i).hints()).as(where + " (en)").hasSameSizeAs(exercise.hints());
				assertThat(exercise.skillIds()).as(where).isNotEmpty();
				if (!exercise.type().isStaticallyEvaluable()) {
					needRunner++;
					assertThat(exercise.config()).as(where).containsKey("starter");
					assertThat(exercise.solution()).as(where).containsKeys("tests", "referenceSolution");
					continue;
				}
				assertThat(ExerciseEvaluation.evaluate(exercise, correctAnswer(exercise)).correct())
						.as("la solución de " + where + " debe ser correcta").isTrue();
				assertThat(ExerciseEvaluation.evaluate(exercise, wrongAnswer(exercise)).correct())
						.as("la respuesta equivocada de " + where + " debe ser incorrecta").isFalse();
				evaluated++;
			}
		}
		assertThat(evaluated).isGreaterThanOrEqualTo(270);
		assertThat(needRunner).isGreaterThanOrEqualTo(142);
		assertThat(CatalogTestSupport.count(jdbc, "select count(*) from exercise")).isEqualTo(evaluated + needRunner);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> correctAnswer(ExerciseDefinition e) {
		return switch (e.type()) {
			case MULTIPLE_CHOICE -> Map.of("choice", e.solution().get("answer"));
			case PREDICT_OUTPUT -> Map.of("text", e.solution().get("answer"));
			case ORDER_LINES -> Map.of("order", e.solution().get("lines"));
			case FILL_CODE -> Map.of("blanks",
					((List<List<String>>) e.solution().get("blanks")).stream().map(List::getFirst).toList());
			case WRITE_CODE -> throw new IllegalStateException("No evaluable");
		};
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> wrongAnswer(ExerciseDefinition e) {
		return switch (e.type()) {
			case MULTIPLE_CHOICE -> Map.of("choice",
					(((Number) e.solution().get("answer")).intValue() + 1) % e.options().size());
			case PREDICT_OUTPUT -> Map.of("text", e.solution().get("answer") + "x");
			case ORDER_LINES -> {
				List<String> lines = new ArrayList<>((List<String>) e.solution().get("lines"));
				Collections.reverse(lines);
				yield Map.of("order", lines);
			}
			case FILL_CODE -> Map.of("blanks", ((List<?>) e.solution().get("blanks")).stream().map(b -> "###").toList());
			case WRITE_CODE -> throw new IllegalStateException("No evaluable");
		};
	}

}
