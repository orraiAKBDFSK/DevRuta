package app.plataforma.api.platform;

import org.springframework.http.HttpStatus;

/**
 * Error de negocio con un código estable (por ejemplo {@code EMAIL_ALREADY_REGISTERED}).
 * El frontend traduce el código; el texto de {@link #getMessage()} es solo para depuración.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	private final Long retryAfterSeconds;

	public ApiException(HttpStatus status, String code, String message) {
		this(status, code, message, null);
	}

	public ApiException(HttpStatus status, String code, String message, Long retryAfterSeconds) {
		super(message);
		this.status = status;
		this.code = code;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public Long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

}
