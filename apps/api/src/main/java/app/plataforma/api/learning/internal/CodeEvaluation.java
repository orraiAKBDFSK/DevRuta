package app.plataforma.api.learning.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import app.plataforma.api.catalog.ExerciseDefinition;
import app.plataforma.api.execution.CodeExecutor;
import app.plataforma.api.learning.internal.LearningDtos.TestOutcome;
import app.plataforma.api.platform.ApiException;

/**
 * Corrección de los ejercicios de escribir código: el código del estudiante se ejecuta en el runner (nunca aquí) una vez
 * por test, y esta clase solo compara las salidas con las esperadas, que no salen del servidor.
 */
final class CodeEvaluation {

	static final int MAX_CODE = 10_000;

	private static final int MAX_SHOWN = 2_000;

	private static final int MAX_ERROR = 600;

	record Verdict(boolean correct, String errorTag, List<TestOutcome> tests, String error, List<String> unmet) {
	}

	private CodeEvaluation() {
	}

	@SuppressWarnings("unchecked")
	static Verdict evaluate(ExerciseDefinition exercise, Map<String, Object> answer, CodeExecutor executor) {
		if (!(answer.get("code") instanceof String code) || code.isBlank() || code.length() > MAX_CODE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", "Falta el código (code)");
		}
		List<Map<String, Object>> tests = (List<Map<String, Object>>) exercise.solution().get("tests");
		List<Map<String, Object>> checks = (List<Map<String, Object>>) exercise.solution().get("staticChecks");
		CodeExecutor.Result result = executor.run(new CodeExecutor.Request(exercise.language(), code,
				tests.stream().map(t -> (String) t.get("stdin")).toList(), checks));
		if (result.cases().size() != tests.size() || result.checks().size() != checks.size()) {
			throw new CodeExecutor.Unavailable("El runner devolvió un número de resultados inesperado", false);
		}

		List<TestOutcome> outcomes = new ArrayList<>();
		String tag = null;
		String error = null;
		for (int i = 0; i < tests.size(); i++) {
			Map<String, Object> test = tests.get(i);
			CodeExecutor.CaseResult run = result.cases().get(i);
			String expected = (String) test.get("expectedStdout");
			boolean visible = Boolean.TRUE.equals(test.get("visible"));
			boolean passed = !run.timedOut() && !run.truncated() && Integer.valueOf(0).equals(run.exitCode())
					&& ExerciseEvaluation.normalizeOutput(run.stdout()).equals(ExerciseEvaluation.normalizeOutput(expected));
			outcomes.add(new TestOutcome(i + 1, visible, passed, run.timedOut(), visible ? shown((String) test.get("stdin")) : null,
					visible ? shown(expected) : null, visible ? shown(run.stdout()) : null));
			if (!passed && tag == null) {
				tag = failureTag(run);
				if ((visible || run.compileError()) && !run.timedOut() && !Integer.valueOf(0).equals(run.exitCode())) {
					error = tail(run.stderr());
				}
			}
		}
		boolean testsPassed = tag == null;
		List<String> unmet = new ArrayList<>();
		for (int i = 0; i < checks.size(); i++) {
			if (!result.checks().get(i)) {
				unmet.add(describe(checks.get(i)));
			}
		}
		if (testsPassed && !unmet.isEmpty()) {
			tag = "code.static_check";
		}
		return new Verdict(tag == null, tag, outcomes, error, unmet);
	}

	private static String failureTag(CodeExecutor.CaseResult run) {
		if (run.compileError()) {
			return "code.compile_error";
		}
		if (run.timedOut()) {
			return "code.timeout";
		}
		if (run.truncated()) {
			return "code.too_much_output";
		}
		if (!Integer.valueOf(0).equals(run.exitCode())) {
			return run.stderr().contains("SyntaxError") ? "code.syntax_error" : "code.runtime_error";
		}
		return "code.wrong_output";
	}

	/** Forma estable que el frontend traduce: {@code assignment}, {@code function:nombre}, {@code function:nombre:returns}. */
	private static String describe(Map<String, Object> check) {
		return switch (String.valueOf(check.get("kind"))) {
			case "requires_assignment" -> "assignment";
			case "requires_function" -> "function:" + check.get("name") + (Boolean.TRUE.equals(check.get("returns")) ? ":returns" : "");
			default -> "unknown";
		};
	}

	private static String shown(String text) {
		return text.length() <= MAX_SHOWN ? text : text.substring(0, MAX_SHOWN) + "…";
	}

	private static String tail(String text) {
		String trimmed = text.strip();
		return trimmed.length() <= MAX_ERROR ? trimmed : "…" + trimmed.substring(trimmed.length() - MAX_ERROR);
	}

}
