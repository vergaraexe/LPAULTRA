package cl.lpa.agenda

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ActaFinalizer {
    /**
     * Genera el PDF, lo envia por correo y, SOLO si el servidor de correo acepto el envio,
     * conserva la copia finalizada. Si algo falla lanza una excepcion con el motivo y el acta sigue como borrador.
     */
    fun finalizeAndSend(context: Context, pages: List<ActaSnap>): String {
        if (pages.all { it.strokes.isEmpty() }) {
            throw IllegalStateException("El acta está en blanco. Escribe en ella antes de finalizar.")
        }
        val cfg = MailSettings.load(context)
        if (!MailSettings.isConfigured(cfg)) {
            throw IllegalStateException("Falta configurar el correo. Entra a ⚙️ Ajustes e ingresa los datos.")
        }
        val clean = cfg.copy(password = cfg.password.replace(" ", ""))

        val stamp = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())
        val fileStamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault()).format(Date())
        val tmp = File(context.cacheDir, "Acta_$fileStamp.pdf")
        try {
            ActaPdf.build(context, pages, tmp)

            // Lanza excepcion si el envio falla: el exito solo se informa si no hubo error
            MailSender.send(
                clean,
                "Acta de reunión - Liceo Politécnico Arica ($stamp)",
                "Se adjunta el acta de reunión (PDF, hoja carta) finalizada el $stamp.\n\nEnviado desde Agenda LPA.",
                listOf(tmp)
            )

            val store = ActaStore(context)
            val saved = store.addFinal(tmp)
            MediaBackup.copy(context, saved, "Documents/Actas", MediaBackup.PDF)
            store.clearDraft()
            return "El servidor de correo aceptó el envío del acta a ${cfg.dest}. " +
                "Quedó una copia en Documentos/Actas y en ‘Actas finalizadas’."
        } finally {
            tmp.delete()
        }
    }
}
