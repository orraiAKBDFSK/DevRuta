package app.plataforma.api;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente HTTP real con jarra de cookies, para probar la API como lo haría un navegador:
 * lee la cookie XSRF-TOKEN y la devuelve en la cabecera X-XSRF-TOKEN.
 */
public class TestClient {

	public record Response(int status, String body, HttpHeaders headers) {

		public String json(String field) {
			Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(body);
			return matcher.find() ? matcher.group(1) : null;
		}

		/** Valor de un campo numérico o booleano del JSON, como texto ({@code "xp":20} devuelve {@code "20"}). */
		public String raw(String field) {
			Matcher matcher = Pattern.compile("\"" + field + "\":(-?[0-9]+|true|false)").matcher(body);
			return matcher.find() ? matcher.group(1) : null;
		}

	}

	private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

	private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

	private final String base;

	public TestClient(int port) {
		this.base = "http://localhost:" + port + "/api";
	}

	public Response get(String path) {
		return send(request(path).GET().build());
	}

	/** GET pidiendo un idioma con la cabecera Accept-Language, como hace el navegador. */
	public Response getInLanguage(String path, String language) {
		return send(request(path).header("Accept-Language", language).GET().build());
	}

	public Response post(String path, String json) {
		return send(withCsrf(request(path)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)).build());
	}

	/** POST con una cabecera extra (p. ej. {@code X-Time-Zone}). */
	public Response postWithHeader(String path, String json, String header, String value) {
		return send(withCsrf(request(path)).header("Content-Type", "application/json").header(header, value)
				.POST(HttpRequest.BodyPublishers.ofString(json)).build());
	}

	/** GET con una cabecera extra. */
	public Response getWithHeader(String path, String header, String value) {
		return send(request(path).header(header, value).GET().build());
	}

	/** POST sin enviar el token CSRF, para comprobar que se rechaza. */
	public Response postWithoutCsrf(String path, String json) {
		return send(request(path).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)).build());
	}

	public Response patch(String path, String json) {
		return send(withCsrf(request(path)).header("Content-Type", "application/json")
				.method("PATCH", HttpRequest.BodyPublishers.ofString(json)).build());
	}

	public Response delete(String path, String json) {
		return send(withCsrf(request(path)).header("Content-Type", "application/json")
				.method("DELETE", HttpRequest.BodyPublishers.ofString(json)).build());
	}

	/** Pide la cookie CSRF, como hace la aplicación web antes de su primera petición POST. */
	public void primeCsrf() {
		get("/v1/auth/csrf");
	}

	public String cookie(String name) {
		return cookies.getCookieStore().getCookies().stream()
				.filter(c -> c.getName().equals(name)).map(HttpCookie::getValue).findFirst().orElse(null);
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create(base + path));
	}

	private HttpRequest.Builder withCsrf(HttpRequest.Builder builder) {
		String token = cookie("XSRF-TOKEN");
		return token == null ? builder : builder.header("X-XSRF-TOKEN", token);
	}

	private Response send(HttpRequest request) {
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			return new Response(response.statusCode(), response.body(), response.headers());
		}
		catch (IOException | InterruptedException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
