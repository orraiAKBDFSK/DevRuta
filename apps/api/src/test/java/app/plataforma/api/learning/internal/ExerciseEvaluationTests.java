package app.plataforma.api.learning.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import app.plataforma.api.catalog.ExerciseDefinition;
import app.plataforma.api.catalog.ExerciseType;
import app.plataforma.api.platform.ApiException;

class ExerciseEvaluationTests {

	private static ExerciseDefinition exercise(ExerciseType type, List<String> options, Map<String, Object> config,
			Map<String, Object> solution) {
		return new ExerciseDefinition("e1", "l1", 1, type, 1, "prompt", "explanation", options, List.of("pista"),
				config, solution, List.of());
	}

	private static ExerciseDefinition choice() {
		return exercise(ExerciseType.MULTIPLE_CHOICE, List.of("a", "b", "c", "d"), Map.of(),
				Map.of("answer", 1, "distractorTags", Map.of("0", "arith.off_by_one")));
	}

	private static void assertInvalid(ExerciseDefinition exercise, Map<String, Object> answer) {
		assertThatThrownBy(() -> ExerciseEvaluation.evaluate(exercise, answer))
				.isInstanceOfSatisfying(ApiException.class, e -> {
					assertThat(e.getCode()).isEqualTo("INVALID_ANSWER");
					assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
				});
	}

	// ---- Selección múltiple ----

	@Test
	void multipleChoiceAcceptsTheRightOption() {
		assertThat(ExerciseEvaluation.evaluate(choice(), Map.of("choice", 1)).correct()).isTrue();
	}

	@Test
	void multipleChoiceReportsTheTagOfATypicalMistake() {
		ExerciseEvaluation.Result result = ExerciseEvaluation.evaluate(choice(), Map.of("choice", 0));

		assertThat(result.correct()).isFalse();
		assertThat(result.errorTag()).isEqualTo("arith.off_by_one");
	}

	@Test
	void multipleChoiceUsesAGenericTagForOtherWrongOptions() {
		assertThat(ExerciseEvaluation.evaluate(choice(), Map.of("choice", 3)).errorTag())
				.isEqualTo("choice.wrong_option");
	}

	@Test
	void multipleChoiceRejectsMalformedAnswers() {
		assertInvalid(choice(), Map.of());
		assertInvalid(choice(), Map.of("choice", 4));
		assertInvalid(choice(), Map.of("choice", -1));
		assertInvalid(choice(), Map.of("choice", "1"));
		assertInvalid(choice(), Map.of("text", "x"));
	}

	// ---- Predecir la salida ----

	private static ExerciseDefinition predict(String answer) {
		return exercise(ExerciseType.PREDICT_OUTPUT, List.of(), Map.of("code", "print()"), Map.of("answer", answer));
	}

	@Test
	void predictOutputIgnoresLineEndingsTrailingSpacesAndSurroundingBlankLines() {
		ExerciseDefinition exercise = predict("Hola\nMundo");

		assertThat(ExerciseEvaluation.evaluate(exercise, Map.of("text", "Hola\nMundo")).correct()).isTrue();
		assertThat(ExerciseEvaluation.evaluate(exercise, Map.of("text", "Hola\r\nMundo\r\n")).correct()).isTrue();
		assertThat(ExerciseEvaluation.evaluate(exercise, Map.of("text", "\n\nHola   \nMundo  \n\n")).correct()).isTrue();
	}

	@Test
	void predictOutputDistinguishesCaseAndInnerSpaces() {
		ExerciseDefinition exercise = predict("[   ab]");

		assertThat(ExerciseEvaluation.evaluate(exercise, Map.of("text", "[   ab]")).correct()).isTrue();
		assertThat(ExerciseEvaluation.evaluate(exercise, Map.of("text", "[ab]")).correct()).isFalse();
		assertThat(ExerciseEvaluation.evaluate(predict("True"), Map.of("text", "true")).correct()).isFalse();
		assertThat(ExerciseEvaluation.evaluate(predict("a\nb"), Map.of("text", "a b")).errorTag())
				.isEqualTo("predict.wrong_output");
	}

	@Test
	void predictOutputRejectsMalformedAnswers() {
		assertInvalid(predict("x"), Map.of());
		assertInvalid(predict("x"), Map.of("text", 5));
		assertInvalid(predict("x"), Map.of("text", "a".repeat(2001)));
	}

	@Test
	void normalizeOutputKeepsLeadingSpacesOfALine() {
		assertThat(ExerciseEvaluation.normalizeOutput("  x \n")).isEqualTo("  x");
		assertThat(ExerciseEvaluation.normalizeOutput("")).isEmpty();
		assertThat(ExerciseEvaluation.normalizeOutput("\n\n")).isEmpty();
	}

	// ---- Ordenar líneas ----

	private static ExerciseDefinition order() {
		return exercise(ExerciseType.ORDER_LINES, List.of(), Map.of(),
				Map.of("lines", List.of("def f():", "    return 1", "print(f())")));
	}

	@Test
	void orderLinesNeedsTheExactOrderIncludingIndentation() {
		assertThat(ExerciseEvaluation.evaluate(order(), Map.of("order", List.of("def f():", "    return 1", "print(f())")))
				.correct()).isTrue();
		ExerciseEvaluation.Result swapped = ExerciseEvaluation.evaluate(order(),
				Map.of("order", List.of("print(f())", "def f():", "    return 1")));
		assertThat(swapped.correct()).isFalse();
		assertThat(swapped.errorTag()).isEqualTo("order.wrong_order");
		assertThat(ExerciseEvaluation.evaluate(order(), Map.of("order", List.of("def f():", "return 1", "print(f())")))
				.correct()).isFalse();
	}

	@Test
	void orderLinesRejectsMalformedAnswers() {
		assertInvalid(order(), Map.of());
		assertInvalid(order(), Map.of("order", List.of("def f():")));
		assertInvalid(order(), Map.of("order", List.of("a", "b", 3)));
		assertInvalid(order(), Map.of("order", "def f():"));
		assertInvalid(order(), Map.of("order", List.of("a", "b", "x".repeat(301))));
	}

	// ---- Completar código ----

	private static ExerciseDefinition fill() {
		return exercise(ExerciseType.FILL_CODE, List.of(), Map.of("code", "print(___, ___)", "blankCount", 2),
				Map.of("blanks", List.of(List.of("1", "01"), List.of("2"))));
	}

	@Test
	void fillCodeAcceptsAnyAlternativeAndIgnoresSurroundingSpaces() {
		assertThat(ExerciseEvaluation.evaluate(fill(), Map.of("blanks", List.of("1", "2"))).correct()).isTrue();
		assertThat(ExerciseEvaluation.evaluate(fill(), Map.of("blanks", List.of(" 01 ", "2 "))).correct()).isTrue();
	}

	@Test
	void fillCodeRejectsWrongOrMisplacedValues() {
		ExerciseEvaluation.Result wrong = ExerciseEvaluation.evaluate(fill(), Map.of("blanks", List.of("2", "1")));

		assertThat(wrong.correct()).isFalse();
		assertThat(wrong.errorTag()).isEqualTo("fill.wrong_blank");
		assertThat(ExerciseEvaluation.evaluate(fill(), Map.of("blanks", List.of("1", ""))).correct()).isFalse();
	}

	@Test
	void fillCodeRejectsMalformedAnswers() {
		assertInvalid(fill(), Map.of());
		assertInvalid(fill(), Map.of("blanks", List.of("1")));
		assertInvalid(fill(), Map.of("blanks", List.of("1", "2", "3")));
		assertInvalid(fill(), Map.of("blanks", List.of("1", 2)));
	}

	// ---- Escribir código ----

	@Test
	void writeCodeIsNotEvaluableWithoutTheExecutionEngine() {
		ExerciseDefinition write = exercise(ExerciseType.WRITE_CODE, List.of(), Map.of("starter", "#"), Map.of());

		assertThatThrownBy(() -> ExerciseEvaluation.evaluate(write, Map.of("code", "print(1)")))
				.isInstanceOfSatisfying(ApiException.class, e -> {
					assertThat(e.getCode()).isEqualTo("NOT_EVALUABLE_YET");
					assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
				});
	}

}
