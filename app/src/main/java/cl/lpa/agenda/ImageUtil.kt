package cl.lpa.agenda

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

object ImageUtil {
    /** Grados que hay que girar la foto segun su EXIF (0 si ya esta derecha). */
    fun degrees(file: File): Float = try {
        when (ExifInterface(file.path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        0f
    }

    /** Ancho y alto reales del archivo, sin cargarlo en memoria. */
    fun bounds(file: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        return Pair(o.outWidth, o.outHeight)
    }

    /** Carga una foto reducida (lado maximo maxSide) y con la orientacion correcta. */
    fun load(file: File, maxSide: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeFile(file.path, opts) ?: return null

        val degrees = degrees(file)
        val m = Matrix()
        if (degrees != 0f) m.postRotate(degrees)
        val longest = maxOf(bmp.width, bmp.height)
        if (longest > maxSide) {
            val sc = maxSide.toFloat() / longest
            m.postScale(sc, sc)
        }
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        return bmp
    }
}
