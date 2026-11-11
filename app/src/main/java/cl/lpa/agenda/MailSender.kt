package cl.lpa.agenda

import java.io.File
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

object MailSender {
    /** Envia un correo con archivos adjuntos por SMTP (Gmail con contrasena de aplicacion). */
    fun send(cfg: MailSettings.Config, subject: String, body: String, files: List<File>) {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", cfg.host)
            put("mail.smtp.port", "587")
            put("mail.smtp.connectiontimeout", "20000")
            put("mail.smtp.timeout", "30000")
            put("mail.smtp.writetimeout", "60000")
        }
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(cfg.sender, cfg.password)
        })
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(cfg.sender))
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(cfg.dest))
        message.setSubject(subject, "UTF-8")

        val multipart = MimeMultipart()
        val text = MimeBodyPart()
        text.setText(body, "UTF-8")
        multipart.addBodyPart(text)
        files.forEach { f ->
            val part = MimeBodyPart()
            part.attachFile(f)
            part.setFileName(f.name)
            multipart.addBodyPart(part)
        }
        message.setContent(multipart)
        Transport.send(message)
    }
}
