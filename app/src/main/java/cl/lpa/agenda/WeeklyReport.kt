package cl.lpa.agenda

import android.content.Context
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object WeeklyReport {
    fun lastSunday(today: LocalDate): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    fun thisWeekSunday(today: LocalDate): LocalDate =
        today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    /**
     * Envia la semana lunes..domingo (terminada en weekEnd) con un Word por cada dia que tenga contenido.
     * Devuelve cuantos archivos se enviaron (0 = no habia nada que enviar).
     */
    fun sendWeek(ctx: Context, weekEnd: LocalDate): Int {
        val cfg = MailSettings.load(ctx)
        if (!MailSettings.isConfigured(cfg)) throw IllegalStateException("Faltan los datos del correo (Ajustes).")
        val cfgClean = cfg.copy(password = cfg.password.replace(" ", ""))

        val start = weekEnd.minusDays(6)
        val store = DayStore(ctx)
        val files = (0..6).map { start.plusDays(it.toLong()) }
            .filter { store.hasContent(it) }
            .mapNotNull { DocxExporter.export(ctx, it) }
        if (files.isEmpty()) return 0

        // Gmail acepta ~25 MB por correo: si las fotos originales pesan mucho, se reparte en varios correos
        val maxBytes = 17L * 1024 * 1024
        val groups = ArrayList<List<File>>()
        var cur = ArrayList<File>()
        var size = 0L
        for (file in files) {
            if (cur.isNotEmpty() && size + file.length() > maxBytes) {
                groups.add(cur); cur = ArrayList(); size = 0L
            }
            cur.add(file); size += file.length()
        }
        if (cur.isNotEmpty()) groups.add(cur)

        val f = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        groups.forEachIndexed { i, group ->
            val part = if (groups.size > 1) " (parte ${i + 1} de ${groups.size})" else ""
            MailSender.send(
                cfgClean,
                "Agenda LPA - Semana del ${start.format(f)} al ${weekEnd.format(f)}$part",
                "Se adjuntan los archivos de la agenda de la semana del ${start.format(f)} al ${weekEnd.format(f)} " +
                    "(${files.size} día(s) con registro).$part\n\nEnviado automáticamente por Agenda LPA.",
                group
            )
        }
        return files.size
    }
}
