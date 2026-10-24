package app.plataforma.api.catalog.internal;

import java.util.List;

/** El contenido tiene errores: no se escribió nada en la base de datos. */
class ContentValidationException extends RuntimeException {

	private final List<String> problems;

	ContentValidationException(List<String> problems) {
		super("El contenido no es válido (" + problems.size() + " problemas):\n - "
				+ String.join("\n - ", problems.stream().limit(25).toList())
				+ (problems.size() > 25 ? "\n - ... y " + (problems.size() - 25) + " más" : ""));
		this.problems = List.copyOf(problems);
	}

	List<String> getProblems() {
		return problems;
	}

}
