package app.plataforma.api.platform.email;

/**
 * Puerto de salida para enviar correo. La implementación actual usa SMTP, que sirve con cualquier proveedor
 * (Resend, Amazon SES, Scaleway...) y con Mailpit en desarrollo; cambiar de proveedor es solo configuración.
 */
public interface EmailSender {

	/** Envía el correo o lanza {@link EmailSendException}. */
	void send(EmailMessage message);

}
