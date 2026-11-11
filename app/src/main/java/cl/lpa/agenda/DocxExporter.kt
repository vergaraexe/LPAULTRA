package cl.lpa.agenda

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Genera UN archivo Word por dia (todas las paginas + evidencias con su descripcion),
 * lo guarda en files/agenda/docx/AAAA-MM-DD.docx y deja una copia en Documentos/Agenda.
 */
object DocxExporter {
    private const val EMU_PER_CM = 360000.0
    private class Img(val name: String, val bytes: ByteArray, val w: Int, val h: Int)

    @Synchronized
    fun export(context: Context, date: LocalDate): File? {
        val store = DayStore(context)
        if (!store.hasContent(date)) return null

        val imgs = ArrayList<Img>()
        val body = StringBuilder()
        val dia = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es", "CL"))
            .replaceFirstChar { it.uppercase() }
        val fecha = "%02d/%02d/%d".format(date.dayOfMonth, date.monthValue, date.year)
        body.append(textPara("Agenda LPA - Liceo Politécnico Arica - $dia $fecha", 20, color = "666666"))

        // Paginas del dia
        var first = true
        for (p in 1..store.pageCount(date)) {
            val pd = store.loadPage(date, p)
            if (pd.strokes.isEmpty() && pd.images.isEmpty()) continue
            val bmp = PageRenderer.render(context, date, pd.strokes, pd.w, pd.h, pd.images)
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
            val img = Img("img${imgs.size + 1}.png", bos.toByteArray(), bmp.width, bmp.height)
            bmp.recycle()
            imgs.add(img)
            val (cw, ch) = fit(img.w, img.h, 18.0, 24.5)
            body.append(imageXml(imgs.size, cw, ch, pageBreakBefore = !first))
            first = false
        }

        // Evidencias (fotos con descripcion)
        val photos = store.loadPhotos(date)
        if (photos.isNotEmpty()) {
            body.append(textPara("Evidencias", 32, bold = true, pageBreakBefore = imgs.isNotEmpty()))
            photos.forEachIndexed { i, ph ->
                // Se adjunta la foto ORIGINAL (el archivo tal cual lo saco la camara)
                val orig = store.photoFile(date, ph.file)
                if (!orig.exists()) return@forEachIndexed
                val img = if (ImageUtil.degrees(orig) == 0f) {
                    val (bw, bh) = ImageUtil.bounds(orig)
                    Img("img${imgs.size + 1}.jpg", orig.readBytes(), bw, bh)
                } else {
                    // Word no siempre respeta el giro EXIF: se gira y se guarda casi sin perdida
                    val bmp = ImageUtil.load(orig, 4096) ?: return@forEachIndexed
                    val bos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, bos)
                    val r = Img("img${imgs.size + 1}.jpg", bos.toByteArray(), bmp.width, bmp.height)
                    bmp.recycle()
                    r
                }
                imgs.add(img)
                val (cw, ch) = fit(img.w, img.h, 16.0, 11.0)
                body.append(imageXml(imgs.size, cw, ch, pageBreakBefore = false))
                val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ph.time))
                body.append(textPara("Foto ${i + 1} ($hora): ${ph.description}", 22))
            }
        }

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="720" w:right="720" w:bottom="720" w:left="720" w:header="360" w:footer="360" w:gutter="0"/></w:sectPr></w:body></w:document>"""

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Default Extension="jpg" ContentType="image/jpeg"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""

        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

        val docRels = StringBuilder()
        docRels.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        imgs.forEachIndexed { i, img ->
            docRels.append("""<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/${img.name}"/>""")
        }
        docRels.append("</Relationships>")

        val outDir = File(context.filesDir, "agenda/docx").apply { mkdirs() }
        val out = File(outDir, "$date.docx")
        val tmp = File(outDir, "$date.tmp")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { z ->
            fun put(name: String, data: ByteArray) {
                z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry()
            }
            put("[Content_Types].xml", contentTypes.toByteArray(Charsets.UTF_8))
            put("_rels/.rels", rootRels.toByteArray(Charsets.UTF_8))
            put("word/document.xml", documentXml.toByteArray(Charsets.UTF_8))
            put("word/_rels/document.xml.rels", docRels.toString().toByteArray(Charsets.UTF_8))
            imgs.forEach { put("word/media/${it.name}", it.bytes) }
        }
        out.delete()
        tmp.renameTo(out)

        MediaBackup.copy(context, out, "Documents/Agenda", MediaBackup.DOCX)
        return out
    }

    // ---------- XML ----------
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")

    private fun textPara(
        text: String, size: Int, bold: Boolean = false,
        color: String = "000000", pageBreakBefore: Boolean = false
    ): String {
        val pb = if (pageBreakBefore) "<w:pageBreakBefore/>" else ""
        val b = if (bold) "<w:b/>" else ""
        return "<w:p><w:pPr>$pb<w:spacing w:after=\"120\"/></w:pPr><w:r><w:rPr>$b<w:color w:val=\"$color\"/><w:sz w:val=\"$size\"/></w:rPr><w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r></w:p>"
    }

    private fun fit(wPx: Int, hPx: Int, maxWcm: Double, maxHcm: Double): Pair<Long, Long> {
        val s = minOf(maxWcm / wPx, maxHcm / hPx)
        return Pair((wPx * s * EMU_PER_CM).toLong(), (hPx * s * EMU_PER_CM).toLong())
    }

    private fun imageXml(index: Int, cx: Long, cy: Long, pageBreakBefore: Boolean): String {
        val pb = if (pageBreakBefore) "<w:pageBreakBefore/>" else ""
        return "<w:p><w:pPr>$pb<w:jc w:val=\"center\"/></w:pPr><w:r><w:drawing>" +
            "<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"$cx\" cy=\"$cy\"/>" +
            "<wp:docPr id=\"$index\" name=\"Imagen $index\"/>" +
            "<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>" +
            "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"$index\" name=\"Imagen $index\"/><pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed=\"rId$index\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>" +
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>" +
            "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"
    }

    /**
     * Deja el Word del dia al dia: lo reescribe (mismo nombre) con todo el contenido actual.
     * Si el dia quedo sin nada, elimina ese Word y su copia en Documentos/Agenda.
     */
    @Synchronized
    fun sync(context: Context, date: LocalDate) {
        val f = export(context, date)
        if (f == null) {
            File(context.filesDir, "agenda/docx/$date.docx").delete()
            MediaBackup.delete(context, "$date.docx", "Documents/Agenda")
        }
        DayStore(context).unmarkDirty(date)
    }
}
