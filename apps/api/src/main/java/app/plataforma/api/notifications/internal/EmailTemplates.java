package app.plataforma.api.notifications.internal;

import java.time.Duration;

import org.springframework.web.util.HtmlUtils;

import app.plataforma.api.platform.email.EmailMessage;

/** Textos de los correos en español e inglés. Solo incluyen datos que genera el sistema (enlace, duración). */
final class EmailTemplates {

	enum Kind {
		VERIFY_EMAIL, RESET_PASSWORD
	}

	private EmailTemplates() {
	}

	static EmailMessage build(Kind kind, String locale, String to, String productName, String link, Duration ttl) {
		boolean english = "en".equals(locale);
		String duration = describe(ttl, english);
		String subject;
		String intro;
		String outro;
		String action;
		switch (kind) {
			case VERIFY_EMAIL -> {
				subject = english ? "Verify your email" : "Verifica tu correo";
				intro = english ? "To verify your email on " + productName + ", open this link:"
						: "Para verificar tu correo en " + productName + ", abre este enlace:";
				outro = english
						? "The link expires in " + duration + ". If you did not create an account, you can ignore this message."
						: "El enlace caduca en " + duration + ". Si no creaste una cuenta, puedes ignorar este mensaje.";
				action = english ? "Verify email" : "Verificar correo";
			}
			case RESET_PASSWORD -> {
				subject = english ? "Reset your password" : "Restablece tu contraseña";
				intro = english ? "We received a request to reset your password on " + productName
						+ ". Open this link to choose a new one:"
						: "Recibimos una solicitud para restablecer tu contraseña en " + productName
								+ ". Abre este enlace para elegir una nueva:";
				outro = english
						? "The link expires in " + duration + " and works only once. If you did not ask for it, ignore this message: your password will not change."
						: "El enlace caduca en " + duration + " y solo sirve una vez. Si no lo pediste, ignora este mensaje: tu contraseña no cambiará.";
				action = english ? "Reset password" : "Restablecer contraseña";
			}
			default -> throw new IllegalArgumentException("Tipo de correo desconocido: " + kind);
		}
		String greeting = english ? "Hello," : "Hola,";
		String text = greeting + "\n\n" + intro + "\n\n" + link + "\n\n" + outro + "\n";
		String safeLink = HtmlUtils.htmlEscape(link);
		String html = "<p>" + greeting + "</p><p>" + HtmlUtils.htmlEscape(intro) + "</p><p><a href=\"" + safeLink + "\">"
				+ HtmlUtils.htmlEscape(action) + "</a></p><p>" + HtmlUtils.htmlEscape(outro) + "</p>";
		return new EmailMessage(to, subject, text, html);
	}

	static String describe(Duration duration, boolean english) {
		long minutes = duration.toMinutes();
		if (minutes >= 60 && minutes % 60 == 0) {
			long hours = minutes / 60;
			return hours + (english ? (hours == 1 ? " hour" : " hours") : (hours == 1 ? " hora" : " horas"));
		}
		return minutes + (english ? (minutes == 1 ? " minute" : " minutes") : (minutes == 1 ? " minuto" : " minutos"));
	}

}
