package cl.lpa.agenda

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Datos del correo, guardados cifrados en la tablet. */
object MailSettings {
    data class Config(val sender: String, val password: String, val dest: String, val host: String)

    private fun secure(ctx: Context): SharedPreferences {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            ctx, "mail_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun load(ctx: Context): Config {
        val p = secure(ctx)
        return Config(
            p.getString("sender", "") ?: "",
            p.getString("password", "") ?: "",
            p.getString("dest", "") ?: "",
            p.getString("host", "smtp.gmail.com") ?: "smtp.gmail.com"
        )
    }

    fun save(ctx: Context, c: Config) {
        secure(ctx).edit()
            .putString("sender", c.sender).putString("password", c.password)
            .putString("dest", c.dest).putString("host", c.host).apply()
    }

    fun isConfigured(c: Config) = c.sender.isNotBlank() && c.password.isNotBlank() && c.dest.isNotBlank()

    private fun state(ctx: Context) = ctx.getSharedPreferences("agenda_state", Context.MODE_PRIVATE)
    fun lastSent(ctx: Context): String = state(ctx).getString("last_sent_week_end", "") ?: ""
    fun setLastSent(ctx: Context, v: String) = state(ctx).edit().putString("last_sent_week_end", v).apply()
}
