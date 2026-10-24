package app.plataforma.api.learning.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import app.plataforma.api.catalog.ExerciseDefinition;
import app.plataforma.api.platform.ApiException;

/**
 * Corrección de los ejercicios que no necesitan ejecutar código del estudiante. Todo es una comparación de datos con
 * la solución guardada en el servidor; la respuesta del estudiante nunca se ejecuta ni se interpreta como código.
 * <p>
 * Formas de respuesta: selección múltiple {@code {"choice": 2}}, predecir la salida {@code {"text": "..."}}, ordenar
 * líneas {@code {"order": ["...", ...]}} y completar código {@code {"blanks": ["...", ...]}}.
 */
final class ExerciseEvaluation {

	/** Veredicto y, si es incorrecto, una etiqueta del tipo de error (para detectar fallos frecuentes). */
	record Result(boolean correct, String errorTag) {

		static final Result OK = new Result(true, null);

		static Result wrong(String tag) {
			return new Result(false, tag);
		}

	}

	private static final int MAX_TEXT = 2000;

	private static final int MAX_LINE = 300;

	private static final int MAX_BLANK = 100;

	private static final int MAX_ITEMS = 100;

	private ExerciseEvaluation() {
	}

	static Result evaluate(ExerciseDefinition exercise, Map<String, Object> answer) {
		return switch (exercise.type()) {
			case MULTIPLE_CHOICE -> multipleChoice(exercise, answer);
			case PREDICT_OUTPUT -> predictOutput(exercise, answer);
			case ORDER_LINES -> orderLines(exercise, answer);
			case FILL_CODE -> fillCode(exercise, answer);
			case WRITE_CODE -> throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NOT_EVALUABLE_YET",
					"Este tipo de ejercicio necesita el motor de ejecución de código");
		};
	}

	private static Result multipleChoice(ExerciseDefinition exercise, Map<String, Object> answer) {
		if (!(answer.get("choice") instanceof Integer choice) || choice < 0 || choice >= exercise.options().size()) {
			throw invalid("Falta una opción válida (choice)");
		}
		int expected = ((Number) exercise.solution().get("answer")).intValue();
		if (choice == expected) {
			return Result.OK;
		}
		Object tags = exercise.solution().get("distractorTags");
		Object tag = tags instanceof Map<?, ?> map ? map.get(String.valueOf(choice)) : null;
		return Result.wrong(tag instanceof String s ? s : "choice.wrong_option");
	}

	private static Result predictOutput(ExerciseDefinition exercise, Map<String, Object> answer) {
		if (!(answer.get("text") instanceof String text) || text.length() > MAX_TEXT) {
			throw invalid("Falta el texto de la respuesta (text)");
		}
		String expected = (String) exercise.solution().get("answer");
		return normalizeOutput(text).equals(normalizeOutput(expected)) ? Result.OK : Result.wrong("predict.wrong_output");
	}

	private static Result orderLines(ExerciseDefinition exercise, Map<String, Object> answer) {
		List<String> expected = strings(exercise.solution().get("lines"));
		List<String> given = stringList(answer.get("order"), MAX_LINE, "order");
		if (given.size() != expected.size()) {
			throw invalid("Deben enviarse las " + expected.size() + " líneas (order)");
		}
		return given.equals(expected) ? Result.OK : Result.wrong("order.wrong_order");
	}

	private static Result fillCode(ExerciseDefinition exercise, Map<String, Object> answer) {
		List<?> expected = (List<?>) exercise.solution().get("blanks");
		List<String> given = stringList(answer.get("blanks"), MAX_BLANK, "blanks");
		if (given.size() != expected.size()) {
			throw invalid("Deben enviarse los " + expected.size() + " huecos (blanks)");
		}
		for (int i = 0; i < expected.size(); i++) {
			List<String> alternatives = strings(expected.get(i));
			String value = given.get(i).strip();
			if (alternatives.stream().noneMatch(alternative -> alternative.strip().equals(value))) {
				return Result.wrong("fill.wrong_blank");
			}
		}
		return Result.OK;
	}

	/**
	 * Normaliza una salida para compararla: unifica los saltos de línea, quita los espacios del final de cada línea y
	 * las líneas vacías del principio y del final. Distingue mayúsculas y los espacios internos.
	 */
	static String normalizeOutput(String text) {
		List<String> lines = new ArrayList<>(List.of(text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)));
		lines.replaceAll(String::stripTrailing);
		while (!lines.isEmpty() && lines.getFirst().isEmpty()) {
			lines.removeFirst();
		}
		while (!lines.isEmpty() && lines.getLast().isEmpty()) {
			lines.removeLast();
		}
		return String.join("\n", lines);
	}

	private static List<String> stringList(Object value, int maxLength, String field) {
		if (!(value instanceof List<?> list) || list.size() > MAX_ITEMS) {
			throw invalid("'" + field + "' debe ser una lista de textos");
		}
		List<String> result = new ArrayList<>();
		for (Object item : list) {
			if (!(item instanceof String s) || s.length() > maxLength) {
				throw invalid("'" + field + "' debe contener solo textos de hasta " + maxLength + " caracteres");
			}
			result.add(s);
		}
		return result;
	}

	private static List<String> strings(Object value) {
		return ((List<?>) value).stream().map(String::valueOf).toList();
	}

	private static ApiException invalid(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", message);
	}

}
