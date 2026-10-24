package app.plataforma.api;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import app.plataforma.api.execution.CodeExecutor;

/** Sustituye al runner en las pruebas de integración: el test decide qué «devuelve» el código del estudiante. */
public class FakeCodeExecutor implements CodeExecutor {

	public volatile boolean available;

	public volatile Function<Request, Result> behaviour;

	public volatile Request lastRequest;

	public void reset() {
		available = false;
		behaviour = null;
		lastRequest = null;
	}

	/** Todos los casos devuelven la misma salida con código de salida 0. */
	public static Result printing(int cases, String stdout) {
		return new Result(Collections.nCopies(cases, new CaseResult(stdout, "", 0, false, false)), List.of());
	}

	@Override
	public boolean isAvailable() {
		return available;
	}

	@Override
	public Result run(Request request) {
		lastRequest = request;
		if (!available || behaviour == null) {
			throw new Unavailable("no disponible", false);
		}
		return behaviour.apply(request);
	}

}
