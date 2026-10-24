package app.plataforma.api.execution.internal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

import app.plataforma.api.execution.CodeExecutor;

/** Cliente HTTP del servicio runner. El token viaja como Bearer; el cuerpo nunca se registra (lleva código ajeno). */
final class RunnerClient implements CodeExecutor {

	private final HttpClient http;

	private final URI endpoint;

	private final String token;

	private final Duration timeout;

	private final JsonMapper json;

	RunnerClient(String baseUrl, String token, Duration connectTimeout, Duration timeout, JsonMapper json) {
		this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/v1/run");
		this.token = token;
		this.timeout = timeout;
		this.json = json;
		this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public Result run(Request request) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("language", request.language());
		body.put("code", request.code());
		body.put("cases", request.stdins().stream().map(s -> Map.of("stdin", s)).toList());
		body.put("checks", request.checks());
		HttpResponse<String> response;
		try {
			response = http.send(HttpRequest.newBuilder(endpoint).timeout(timeout)
					.header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
					HttpResponse.BodyHandlers.ofString());
		}
		catch (IOException e) {
			throw new Unavailable("No se pudo contactar con el runner: " + e.getClass().getSimpleName(), false);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new Unavailable("Interrumpido mientras esperaba al runner", false);
		}
		return switch (response.statusCode()) {
			case 200 -> parse(response.body());
			case 503 -> throw new Unavailable("El runner está ocupado", true);
			default -> throw new Unavailable("El runner respondió " + response.statusCode(), false);
		};
	}

	@SuppressWarnings("unchecked")
	private Result parse(String body) {
		try {
			Map<String, Object> tree = json.readValue(body, Map.class);
			List<CaseResult> cases = new ArrayList<>();
			for (Object raw : (List<Object>) tree.get("cases")) {
				Map<String, Object> c = (Map<String, Object>) raw;
				cases.add(new CaseResult(string(c.get("stdout")), string(c.get("stderr")),
						c.get("exit_code") instanceof Number n ? n.intValue() : null,
						Boolean.TRUE.equals(c.get("timed_out")), Boolean.TRUE.equals(c.get("truncated"))));
			}
			List<Boolean> checks = new ArrayList<>();
			for (Object raw : (List<Object>) tree.get("checks")) {
				checks.add(Boolean.TRUE.equals(((Map<String, Object>) raw).get("ok")));
			}
			return new Result(cases, checks);
		}
		catch (RuntimeException e) {
			throw new Unavailable("Respuesta del runner no válida", false);
		}
	}

	private static String string(Object value) {
		return value instanceof String s ? s : "";
	}

}
