package app.plataforma.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj de pruebas: hora real más un desplazamiento que el test puede mover (p. ej. «mañana»). */
public class OffsetClock extends Clock {

	private volatile Duration offset = Duration.ZERO;

	public void reset() {
		offset = Duration.ZERO;
	}

	/** Coloca el reloj en un instante concreto (sigue avanzando desde ahí). */
	public void setNow(Instant instant) {
		offset = Duration.between(Instant.now(), instant);
	}

	public void advance(Duration amount) {
		offset = offset.plus(amount);
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

	@Override
	public Instant instant() {
		return Instant.now().plus(offset);
	}

}
