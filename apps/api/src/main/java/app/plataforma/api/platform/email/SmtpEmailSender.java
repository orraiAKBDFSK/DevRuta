package app.plataforma.api.platform.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Envío por SMTP. La conexión se configura con las propiedades estándar {@code spring.mail.*}. */
@Component
class SmtpEmailSender implements EmailSender {

	private final JavaMailSender mailSender;

	private final String from;

	SmtpEmailSender(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	@Override
	public void send(EmailMessage message) {
		try {
			MimeMessage mime = mailSender.createMimeMessage();
			// MimeMessageHelper codifica las cabeceras, lo que evita la inyección de saltos de línea.
			MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
			helper.setFrom(from);
			helper.setTo(message.to());
			helper.setSubject(message.subject());
			if (message.html() != null) {
				helper.setText(message.text(), message.html());
			}
			else {
				helper.setText(message.text(), false);
			}
			mailSender.send(mime);
		}
		catch (MessagingException | MailException ex) {
			throw new EmailSendException("No se pudo enviar el correo \"" + message.subject() + "\"", ex);
		}
	}

}
