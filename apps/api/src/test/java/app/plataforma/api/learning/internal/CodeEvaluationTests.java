package app.plataforma.api.learning.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.plataforma.api.catalog.ExerciseDefinition;
import app.plataforma.api.catalog.ExerciseType;
import app.plataforma.api.execution.CodeExecutor;
import app.plataforma.api.platform.ApiException;

class CodeEvaluationTests {

	private static Map<String, Object> test(String stdin, String expected, boolean visible) {
		return Map.of("stdin", stdin, "expectedStdout", expected, "visible", visible);
	}

	private static ExerciseDefinition exercise(List<Map<String, Object>> tests, List<Map<String, Object>> checks) {
		return new ExerciseDefinition("e1", "l1", 1, ExerciseType.WRITE_CODE, 1, "p", "explicación", List.of(),
				List.of("pista"), Map.of("starter", "#"), Map.of("referenceSolution", "x", "tests", tests,
						"staticChecks", checks),
				List.of());
	}

	private static CodeExecutor.CaseResult ran(String stdout) {
		return new CodeExecutor.CaseResult(stdout, "", 0, false, false);
	}

	/** Executor que devuelve resultados fijos y recuerda lo que se le pidió. */
	private static final class Stub implements CodeExecutor {

		private final Result result;

		Request seen;

		Stub(List<CaseResult> cases, Boolean... checks) {
			this.result = new Result(cases, List.of(checks));
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public Result run(Request request) {
			seen = request;
			return result;
		}

	}

	private static Map<String, Object> answer(String code) {
		return Map.of("code", code);
	}

	@Test
	void passesWhenEveryTestMatches() {
		ExerciseDefinition e = exercise(List.of(test("2\n", "4\n", true), test("5\n", "10\n", false)), List.of());
		Stub runner = new Stub(List.of(ran("4\n"), ran("10\n")));

		CodeEvaluation.Verdict verdict = CodeEvaluation.evaluate(e, answer("print(int(input()) * 2)"), runner);

		assertThat(verdict.correct()).isTrue();
		assertThat(verdict.errorTag()).isNull();
		assertThat(verdict.tests()).extracting(t -> t.passed()).containsExactly(true, true);
		assertThat(runner.seen.stdins()).containsExactly("2\n", "5\n");
	}

	@Test
	void ignoresTrailingWhitespaceAndWindowsLineEndingsLikeTheOtherExercises() {
		ExerciseDefinition e = exercise(List.of(test("", "hola\nmundo\n", true)), List.of());

		assertThat(CodeEvaluation.evaluate(e, answer("x"), new Stub(List.of(ran("hola  \r\nmundo\r\n\r\n")))).correct())
				.isTrue();
		assertThat(CodeEvaluation.evaluate(e, answer("x"), new Stub(List.of(ran("Hola\nmundo\n")))).correct())
				.isFalse();
	}

	@Test
	void hiddenTestsNeverRevealTheirInputOrExpectedOutput() {
		ExerciseDefinition e = exercise(List.of(test("1\n", "uno\n", true), test("SECRETO\n", "ESPERADO-OCULTO\n", false)),
				List.of());
		Stub runner = new Stub(List.of(ran("uno\n"), ran("lo que imprimió\n")));

		CodeEvaluation.Verdict verdict = CodeEvaluation.evaluate(e, answer("x"), runner);

		assertThat(verdict.correct()).isFalse();
		LearningDtos.TestOutcome hidden = verdict.tests().get(1);
		assertThat(hidden.visible()).isFalse();
		assertThat(hidden.passed()).isFalse();
		assertThat(hidden.input()).isNull();
		assertThat(hidden.expected()).isNull();
		assertThat(hidden.actual()).isNull();
		assertThat(verdict.tests().get(0).expected()).isEqualTo("uno\n");
	}

	@Test
	void tagsTheKindOfFailure() {
		ExerciseDefinition e = exercise(List.of(test("", "1\n", true)), List.of());

		assertThat(tag(e, new CodeExecutor.CaseResult("", "", null, true, false))).isEqualTo("code.timeout");
		assertThat(tag(e, new CodeExecutor.CaseResult("x", "", 0, false, true))).isEqualTo("code.too_much_output");
		assertThat(tag(e, new CodeExecutor.CaseResult("", "  File \"<string>\"\nSyntaxError: invalid syntax", 1, false, false)))
				.isEqualTo("code.syntax_error");
		assertThat(tag(e, new CodeExecutor.CaseResult("", "NameError: x", 1, false, false)))
				.isEqualTo("code.runtime_error");
		assertThat(tag(e, ran("2\n"))).isEqualTo("code.wrong_output");
	}

	private static String tag(ExerciseDefinition e, CodeExecutor.CaseResult run) {
		return CodeEvaluation.evaluate(e, answer("x"), new Stub(List.of(run))).errorTag();
	}

	@Test
	void aProgramThatPrintsTheRightThingButExitsWithAnErrorFails() {
		ExerciseDefinition e = exercise(List.of(test("", "1\n", true)), List.of());

		CodeExecutor.CaseResult crashed = new CodeExecutor.CaseResult("1\n", "ValueError", 1, false, false);

		assertThat(CodeEvaluation.evaluate(e, answer("x"), new Stub(List.of(crashed))).correct()).isFalse();
	}

	@Test
	void showsTheProgramsOwnErrorOnlyForVisibleTestsAndTruncatesIt() {
		ExerciseDefinition e = exercise(List.of(test("", "1\n", true), test("", "2\n", false)), List.of());
		String longError = "x".repeat(5000) + "\nZeroDivisionError: division by zero";

		CodeEvaluation.Verdict visibleFails = CodeEvaluation.evaluate(e, answer("x"), new Stub(List.of(
				new CodeExecutor.CaseResult("", longError, 1, false, false), ran("2\n"))));
		CodeEvaluation.Verdict hiddenFails = CodeEvaluation.evaluate(e, answer("x"), new Stub(List.of(ran("1\n"),
				new CodeExecutor.CaseResult("", "ValueError: invalid literal for int(): 'SECRETO'", 1, false, false))));

		assertThat(visibleFails.error()).endsWith("ZeroDivisionError: division by zero").hasSizeLessThan(700);
		assertThat(hiddenFails.error()).isNull();
	}

	@Test
	void staticChecksMustAlsoPassAndAreReportedInAStableForm() {
		ExerciseDefinition e = exercise(List.of(test("", "1\n", true)),
				List.of(Map.of("kind", "requires_function", "name", "es_par", "returns", true),
						Map.of("kind", "requires_assignment", "count_min", 1)));

		CodeEvaluation.Verdict verdict = CodeEvaluation.evaluate(e, answer("print(1)"),
				new Stub(List.of(ran("1\n")), false, false));

		assertThat(verdict.correct()).isFalse();
		assertThat(verdict.errorTag()).isEqualTo("code.static_check");
		assertThat(verdict.unmet()).containsExactly("function:es_par:returns", "assignment");
		assertThat(CodeEvaluation.evaluate(e, answer("print(1)"), new Stub(List.of(ran("1\n")), true, true)).correct())
				.isTrue();
	}

	@Test
	void aWrongOutputIsReportedBeforeAMissingRequirement() {
		ExerciseDefinition e = exercise(List.of(test("", "1\n", true)),
				List.of(Map.of("kind", "requires_assignment", "count_min", 1)));

		CodeEvaluation.Verdict verdict = CodeEvaluation.evaluate(e, answer("x"), new Stub(List.of(ran("2\n")), false));

		assertThat(verdict.errorTag()).isEqualTo("code.wrong_output");
	}

	@Test
	void rejectsAnswersWithoutUsableCode() {
		ExerciseDefinition e = exercise(List.of(test("", "1\n", true)), List.of());
		Stub runner = new Stub(List.of(ran("1\n")));

		for (Map<String, Object> bad : List.<Map<String, Object>>of(Map.of(), Map.of("code", 5), Map.of("code", " \n"),
				Map.of("code", "x".repeat(CodeEvaluation.MAX_CODE + 1)))) {
			assertThatThrownBy(() -> CodeEvaluation.evaluate(e, bad, runner))
					.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_ANSWER"));
		}
		assertThat(runner.seen).isNull();
	}

	@Test
	void aRunnerThatReturnsTheWrongNumberOfResultsIsTreatedAsAFailureOfTheRunner() {
		ExerciseDefinition e = exercise(List.of(test("", "1\n", true), test("", "2\n", true)), List.of());

		assertThatThrownBy(() -> CodeEvaluation.evaluate(e, answer("x"), new Stub(List.of(ran("1\n")))))
				.isInstanceOf(CodeExecutor.Unavailable.class);
	}

}
