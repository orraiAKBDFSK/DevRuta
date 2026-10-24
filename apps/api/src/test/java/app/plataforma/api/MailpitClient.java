package app.plataforma.api;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lee los correos que la aplicación envió a Mailpit, por su API HTTP. */
class MailpitClient {

	record Mail(String subject, String text) {

		/** Extrae el token del enlace {@code ...?token=XXXX} del cuerpo. */
		String token() {
			Matcher matcher = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(text);
			if (!matcher.find()) {
				throw new AssertionError("El correo no contiene un enlace con token:\n" + text);
			}
			return matcher.group(1);
		}

		String link() {
			Matcher matcher = Pattern.compile("(https?://\\S+token=[A-Za-z0-9_-]+)").matcher(text);
			return matcher.find() ? matcher.group(1) : null;
		}

	}

	private final HttpClient http = HttpClient.newHttpClient();

	private final String api;

	MailpitClient(String api) {
		this.api = api;
	}

	/** Espera (los correos se envían en segundo plano) hasta que haya al menos {@code count} para ese destinatario. */
	List<Mail> waitFor(String to, int count) {
		long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		List<Mail> found = new ArrayList<>();
		while (System.nanoTime() < deadline) {
			found = messagesTo(to);
			if (found.size() >= count) {
				return found;
			}
			sleep(100);
		}
		return found;
	}

	/** Cuántos correos hay para ese destinatario ahora mismo. */
	List<Mail> messagesTo(String to) {
		String query = URLEncoder.encode("to:" + to, StandardCharsets.UTF_8);
		String list = get("/api/v1/search?query=" + query);
		List<Mail> mails = new ArrayList<>();
		Matcher ids = Pattern.compile("\"ID\":\"([^\"]+)\"").matcher(list);
		List<String> messageIds = new ArrayList<>();
		while (ids.find()) {
			if (!messageIds.contains(ids.group(1))) {
				messageIds.add(ids.group(1));
			}
		}
		for (String id : messageIds) {
			String message = get("/api/v1/message/" + id);
			mails.add(new Mail(extract(message, "Subject"), unescape(extract(message, "Text"))));
		}
		return mails;
	}

	private static String extract(String json, String field) {
		Matcher matcher = Pattern.compile("\"" + field + "\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
		return matcher.find() ? matcher.group(1) : "";
	}

	private static String unescape(String value) {
		return value.replace("\\n", "\n").replace("\\r", "\r").replace("\\\"", "\"").replace("\\/", "/")
				.replace("\\u0026", "&");
	}

	private String get(String path) {
		try {
			HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(api + path)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			return response.body();
		}
		catch (IOException | InterruptedException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
