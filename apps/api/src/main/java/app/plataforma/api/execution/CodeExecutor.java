package app.plataforma.api.execution;

import java.util.List;
import java.util.Map;

/**
 * Puerta de entrada al motor de ejecución aislada. Quien la usa solo ve «código + entradas → salidas»: dónde y cómo se
 * aísla el código es asunto del servicio runner (apps/runner), no del resto de la aplicación.
 */
public interface CodeExecutor {

	/** {@code false} si no hay runner configurado: los ejercicios de escribir código no se pueden corregir. */
	boolean isAvailable();

	/**
	 * Ejecuta {@code code} una vez por cada entrada (stdin) y evalúa las comprobaciones estáticas.
	 *
	 * @throws Unavailable si el runner no está configurado, está ocupado o falla (el intento no debe contarse)
	 */
	Result run(Request request);

	/** {@code checks} son los {@code static_checks} del contenido, tal cual (los valida el runner). */
	record Request(String language, String code, List<String> stdins, List<Map<String, Object>> checks) {
	}

	record Result(List<CaseResult> cases, List<Boolean> checks) {
	}

	/** {@code exitCode} es {@code null} si el caso no llegó a ejecutarse (se acabó el tiempo total). */
	record CaseResult(String stdout, String stderr, Integer exitCode, boolean timedOut, boolean truncated) {
	}

	class Unavailable extends RuntimeException {

		private final boolean busy;

		public Unavailable(String message, boolean busy) {
			super(message);
			this.busy = busy;
		}

		/** {@code true} si el runner está bien pero saturado: reintentar en unos segundos tiene sentido. */
		public boolean isBusy() {
			return busy;
		}

	}

}
