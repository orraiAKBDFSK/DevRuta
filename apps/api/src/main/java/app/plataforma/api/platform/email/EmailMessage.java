package app.plataforma.api.platform.email;

/**
 * Correo ya redactado. {@code html} es opcional: si es nulo se envía solo texto.
 * El texto y el HTML deben escapar cualquier dato escrito por usuarios antes de llegar aquí.
 */
public record EmailMessage(String to, String subject, String text, String html) {

	/** No se imprime el contenido: puede incluir enlaces con tokens. */
	@Override
	public String toString() {
		return "EmailMessage[subject=" + subject + "]";
	}

}
