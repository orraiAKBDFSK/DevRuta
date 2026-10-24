package app.plataforma.api.execution.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import app.plataforma.api.execution.CodeExecutor;

/** El cliente contra un runner simulado con el servidor HTTP del JDK. */
class RunnerClientTests {

	private static final String TOKEN = "token-de-prueba-0123456789";

	private final JsonMapper json = JsonMapper.builder().build();

	private HttpServer server;

	private final AtomicReference<String> authorization = new AtomicReference<>();

	private final AtomicReference<String> body = new AtomicReference<>();

	private volatile int status = 200;

	private volatile String response = "{}";

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/run", exchange -> {
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private RunnerClient client(Duration timeout) {
		return new RunnerClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", TOKEN, Duration.ofSeconds(2),
				timeout, json);
	}

	private static CodeExecutor.Request request() {
		return new CodeExecutor.Request("python", "print(input())", List.of("a\n", "b\n"),
				List.of(Map.of("kind", "requires_assignment", "count_min", 1)));
	}

	@Test
	void sendsTheCodeCasesAndChecksWithTheBearerToken() {
		response = """
				{"status":"ok","checks":[{"kind":"requires_assignment","ok":true}],
				 "cases":[{"stdout":"a\\n","stderr":"","exit_code":0,"timed_out":false,"truncated":false,"duration_ms":12},
				          {"stdout":"","stderr":"boom","exit_code":1,"timed_out":false,"truncated":true,"duration_ms":3}]}""";

		CodeExecutor.Result result = client(Duration.ofSeconds(5)).run(request());

		assertThat(authorization.get()).isEqualTo("Bearer " + TOKEN);
		Map<?, ?> sent = json.readValue(body.get(), Map.class);
		assertThat(sent.get("language")).isEqualTo("python");
		assertThat(sent.get("code")).isEqualTo("print(input())");
		assertThat(sent.get("cases")).isEqualTo(List.of(Map.of("stdin", "a\n"), Map.of("stdin", "b\n")));
		assertThat(sent.get("checks")).isEqualTo(List.of(Map.of("kind", "requires_assignment", "count_min", 1)));
		assertThat(result.checks()).containsExactly(true);
		assertThat(result.cases()).containsExactly(new CodeExecutor.CaseResult("a\n", "", 0, false, false),
				new CodeExecutor.CaseResult("", "boom", 1, false, true));
	}

	@Test
	void aCaseThatNeverRanHasNoExitCode() {
		response = "{\"checks\":[],\"cases\":[{\"stdout\":\"\",\"stderr\":\"\",\"exit_code\":null,\"timed_out\":true}]}";

		CodeExecutor.CaseResult result = client(Duration.ofSeconds(5)).run(request()).cases().get(0);

		assertThat(result.exitCode()).isNull();
		assertThat(result.timedOut()).isTrue();
	}

	@Test
	void busyRunnerIsReportedAsBusy() {
		status = 503;
		response = "{\"error\":\"BUSY\"}";

		assertThatThrownBy(() -> client(Duration.ofSeconds(5)).run(request()))
				.isInstanceOfSatisfying(CodeExecutor.Unavailable.class, e -> assertThat(e.isBusy()).isTrue());
	}

	@Test
	void otherErrorsAreReportedAsUnavailableAndDoNotLeakTheBody() {
		for (int code : new int[] { 400, 401, 413, 500, 502 }) {
			status = code;
			response = "{\"detail\":\"secreto del runner\"}";

			assertThatThrownBy(() -> client(Duration.ofSeconds(5)).run(request()))
					.isInstanceOfSatisfying(CodeExecutor.Unavailable.class, e -> {
						assertThat(e.isBusy()).isFalse();
						assertThat(e.getMessage()).doesNotContain("secreto");
					});
		}
	}

	@Test
	void malformedResponsesAreUnavailableNotCorrect() {
		for (String bad : new String[] { "no es json", "{}", "{\"cases\":5,\"checks\":[]}", "{\"cases\":[1],\"checks\":[]}" }) {
			response = bad;

			assertThatThrownBy(() -> client(Duration.ofSeconds(5)).run(request())).as(bad)
					.isInstanceOf(CodeExecutor.Unavailable.class);
		}
	}

	@Test
	void anUnreachableRunnerIsUnavailable() {
		server.stop(0);

		assertThatThrownBy(() -> client(Duration.ofSeconds(5)).run(request())).isInstanceOf(CodeExecutor.Unavailable.class);
	}

	@Test
	void aRunnerThatDoesNotAnswerInTimeIsUnavailable() {
		server.removeContext("/v1/run");
		server.createContext("/v1/run", exchange -> {
			try {
				Thread.sleep(1500);
			}
			catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
			exchange.close();
		});

		assertThatThrownBy(() -> client(Duration.ofMillis(300)).run(request())).isInstanceOf(CodeExecutor.Unavailable.class);
	}

}
