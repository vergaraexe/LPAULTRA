package cl.lpa.agenda

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalDateTime

/** Corre cada dia a las ~20:00. Envia la semana el domingo (o recupera el envio si ese dia no hubo internet). */
class WeeklyMailWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        if (!MailSettings.isConfigured(MailSettings.load(ctx))) return Result.success()

        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val weekEnd = WeeklyReport.lastSunday(today)
        val due = today.isAfter(weekEnd) || now.hour >= 19
        if (!due || MailSettings.lastSent(ctx) == weekEnd.toString()) return Result.success()

        return try {
            WeeklyReport.sendWeek(ctx, weekEnd)
            MailSettings.setLastSent(ctx, weekEnd.toString())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
