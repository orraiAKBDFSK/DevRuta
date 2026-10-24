package app.plataforma.api.platform;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Limita cuántas veces puede ocurrir algo por clave dentro de una ventana (intentos de inicio de sesión fallidos,
 * envíos de correo...). Ventana deslizante en memoria: suficiente para una sola instancia del API. Con varias
 * instancias hay que moverlo a un almacén compartido (la base de datos o Redis) o a un proxy delante.
 */
public class AttemptLimiter {

	private final int maxEvents;

	private final Duration window;

	private final Clock clock;

	private final Map<String, Deque<Instant>> events = new HashMap<>();

	public AttemptLimiter(int maxEvents, Duration window, Clock clock) {
		this.maxEvents = maxEvents;
		this.window = window;
		this.clock = clock;
	}

	/** Lanza 429 si la clave ya agotó su cupo dentro de la ventana. */
	public synchronized void checkAllowed(String key) {
		if (isExhausted(key)) {
			Deque<Instant> recent = recent(key);
			long retryAfter = Duration.between(clock.instant(), recent.peekFirst().plus(window)).toSeconds() + 1;
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
					"Demasiados intentos. Inténtalo más tarde", Math.max(retryAfter, 1));
		}
	}

	/** Igual que {@link #checkAllowed} pero sin lanzar: para flujos que no deben revelar el límite. */
	public synchronized boolean isExhausted(String key) {
		return recent(key).size() >= maxEvents;
	}

	public synchronized void record(String key) {
		recent(key).addLast(clock.instant());
	}

	public synchronized void reset(String key) {
		events.remove(key);
	}

	private Deque<Instant> recent(String key) {
		Instant limit = clock.instant().minus(window);
		Deque<Instant> deque = events.computeIfAbsent(key, k -> new ArrayDeque<>());
		while (!deque.isEmpty() && deque.peekFirst().isBefore(limit)) {
			deque.pollFirst();
		}
		return deque;
	}

}
