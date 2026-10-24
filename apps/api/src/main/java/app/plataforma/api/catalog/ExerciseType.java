package app.plataforma.api.catalog;

import java.util.Locale;

/** Tipos de ejercicio. Los de evaluación estática se corrigen en el servidor sin ejecutar código del estudiante. */
public enum ExerciseType {

	MULTIPLE_CHOICE(true), PREDICT_OUTPUT(true), ORDER_LINES(true), FILL_CODE(true),
	/** Necesita ejecutar el código del estudiante en un entorno aislado (motor de ejecución, fase 7). */
	WRITE_CODE(false);

	private final boolean staticallyEvaluable;

	ExerciseType(boolean staticallyEvaluable) {
		this.staticallyEvaluable = staticallyEvaluable;
	}

	public boolean isStaticallyEvaluable() {
		return staticallyEvaluable;
	}

	/** Convierte el nombre usado en los YAML ({@code multiple_choice}) en el tipo. */
	public static ExerciseType fromContent(String value) {
		return valueOf(value.trim().toUpperCase(Locale.ROOT));
	}

}
