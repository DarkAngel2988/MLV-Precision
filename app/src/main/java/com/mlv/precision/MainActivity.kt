package com.mlv.precision

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * MLV Precision v0.1: offline photo-angle marking prototype.
 * Camera, perspective correction and precision calibration belong to later
 * phases; this app NEVER claims to measure physical squareness from a photo.
 */
class MainActivity : Activity() {
    private val background = Color.rgb(5, 20, 29)
    private val panel = Color.rgb(13, 41, 52)
    private val accent = Color.rgb(68, 231, 216)
    private val amber = Color.rgb(255, 190, 96)
    private val white = Color.rgb(237, 251, 254)
    private val muted = Color.rgb(165, 193, 202)

    private lateinit var analyzer: PrecisionImageView
    private lateinit var stepLabel: TextView
    private lateinit var angleLabel: TextView
    private lateinit var deviationLabel: TextView
    private lateinit var saveButton: TextView
    private lateinit var undoButton: TextView
    private lateinit var resetButton: TextView

    companion object { private const val SELECT_PHOTO = 101 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(background)
        scroll.isFillViewport = true
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(14), dp(16), dp(30))
        scroll.addView(body)
        setContentView(scroll)

        val top = horizontal()
        top.gravity = Gravity.CENTER_VERTICAL
        body.addView(top)
        val brand = LinearLayout(this)
        brand.orientation = LinearLayout.VERTICAL
        top.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        brand.addView(label("◈ MLV PRECISION", 21f, accent, true))
        brand.addView(label("FOTOANÁLISIS  /  PROTOTIPO 0.1", 10f, muted, true))
        val version = label("BETA", 11f, amber, true)
        version.setPadding(dp(10), dp(7), dp(10), dp(7))
        version.background = shape(panel, amber, 10, 1)
        top.addView(version)

        val lead = label("Analiza una escuadra en una fotografía", 17f, white, true)
        addWithTopMargin(body, lead, 16)
        val hint = label("Marca los extremos de dos bordes. Calcularemos el ángulo aparente entre las líneas, sin asumir que la foto está tomada de frente.",
            13f, muted, false)
        addWithTopMargin(body, hint, 5)

        val open = action("▧  ABRIR FOTO", true)
        addWithTopMargin(body, open, 16)
        open.setOnClickListener { selectImage() }

        analyzer = PrecisionImageView(this) { count, angle -> updateReadout(count, angle) }
        analyzer.background = shape(panel, accent, 14, 1)
        val screenHeight = resources.displayMetrics.heightPixels
        val desired = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            dp(245) else max(dp(280), min(dp(420), screenHeight / 2))
        val canvasParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, desired)
        canvasParams.topMargin = dp(12)
        body.addView(analyzer, canvasParams)

        stepLabel = label("Primero abre una fotografía.", 13f, muted, false)
        addWithTopMargin(body, stepLabel, 10)

        val results = horizontal()
        val resultsParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT)
        resultsParams.topMargin = dp(12)
        body.addView(results, resultsParams)
        val angleCard = resultCard("ÁNGULO APARENTE")
        val deviationCard = resultCard("DIFERENCIA CON 90°")
        angleLabel = angleCard.second
        deviationLabel = deviationCard.second
        val leftParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        leftParams.marginEnd = dp(6)
        results.addView(angleCard.first, leftParams)
        val rightParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        rightParams.marginStart = dp(6)
        results.addView(deviationCard.first, rightParams)

        val controls = horizontal()
        val controlParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT)
        controlParams.topMargin = dp(12)
        body.addView(controls, controlParams)
        undoButton = action("↶  Deshacer", false)
        resetButton = action("↺  Reiniciar", false)
        val undoParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        undoParams.marginEnd = dp(6)
        controls.addView(undoButton, undoParams)
        val resetParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        resetParams.marginStart = dp(6)
        controls.addView(resetButton, resetParams)
        undoButton.setOnClickListener { analyzer.undo() }
        resetButton.setOnClickListener { analyzer.clearPoints() }

        saveButton = action("▣  GUARDAR FOTO CON MEDICIÓN", true)
        addWithTopMargin(body, saveButton, 12)
        saveButton.setOnClickListener { saveAnnotatedPhoto() }

        val warning = label("AVISO: Una fotografía puede deformar los ángulos por perspectiva. Este prototipo mide únicamente la imagen 2D. No reemplaza un nivel ni una escuadra física.",
            12f, amber, false)
        warning.setPadding(dp(12), dp(11), dp(12), dp(11))
        warning.background = shape(panel, amber, 12, 1)
        addWithTopMargin(body, warning, 15)

        val footer = label("Funciona sin cuenta, sin Internet y sin acceso general a tu galería.",
            11f, muted, false)
        footer.gravity = Gravity.CENTER
        addWithTopMargin(body, footer, 13)

        updateReadout(0, null)
    }

    private fun resultCard(name: String): Pair<LinearLayout, TextView> {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(12), dp(12), dp(12), dp(12))
        card.background = shape(panel, Color.rgb(36, 86, 99), 12, 1)
        card.addView(label(name, 10f, muted, true))
        val value = label("--.--°", 24f, white, true)
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT)
        params.topMargin = dp(4)
        card.addView(value, params)
        return card to value
    }

    private fun updateReadout(count: Int, value: Double?) {
        if (!::stepLabel.isInitialized) return
        stepLabel.text = when {
            !analyzer.hasImage -> "Primero abre una fotografía."
            count == 0 -> "Paso 1/4 · Toca el inicio de la primera línea (turquesa)."
            count == 1 -> "Paso 2/4 · Toca el final de la primera línea (turquesa)."
            count == 2 -> "Paso 3/4 · Toca el inicio de la segunda línea (naranja)."
            count == 3 -> "Paso 4/4 · Toca el final de la segunda línea (naranja)."
            value == null -> "Línea demasiado corta. Toca Deshacer y marca puntos separados."
            else -> "Medición completada. Puedes guardar o reiniciar las líneas."
        }
        if (::angleLabel.isInitialized) {
            angleLabel.text = if (value == null) "--.--°" else "%.2f°".format(Locale.US, value)
            deviationLabel.text = if (value == null) "--.--°" else
                "%.2f°".format(Locale.US, AngleMath.deviationFromSquareDegrees(value))
            saveButton.isEnabled = value != null
            saveButton.alpha = if (value != null) 1f else 0.45f
            undoButton.isEnabled = count > 0
            undoButton.alpha = if (count > 0) 1f else 0.45f
            resetButton.isEnabled = count > 0
            resetButton.alpha = if (count > 0) 1f else 0.45f
        }
    }

    private fun selectImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, SELECT_PHOTO)
    }

    @Deprecated("Activity result API is sufficient for the permission-free first prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SELECT_PHOTO || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val source = ImageDecoder.createSource(contentResolver, uri)
            val photo = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longer = max(info.size.width, info.size.height)
                val sample = ceil(longer / 2200.0).toInt().coerceAtLeast(1)
                decoder.setTargetSampleSize(sample)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            analyzer.setPhoto(photo)
            message("Foto lista. Marca los cuatro puntos.")
        } catch (error: Exception) {
            message("No pude abrir esa imagen: ${error.localizedMessage ?: "formato no compatible"}")
        } catch (_: OutOfMemoryError) {
            message("La imagen es demasiado grande para analizarla.")
        }
    }

    private fun saveAnnotatedPhoto() {
        val bitmap: Bitmap = try {
            analyzer.annotatedBitmap() ?: run {
                message("Primero marca dos líneas válidas.")
                return
            }
        } catch (_: OutOfMemoryError) {
            message("La imagen es demasiado grande para exportarla.")
            return
        }
        saveButton.isEnabled = false
        Thread {
            var created: Uri? = null
            try {
                val filename = "MLV_Precision_${System.currentTimeMillis()}.jpg"
                val meta = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MLV Precision")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val media = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                created = contentResolver.insert(media, meta)
                    ?: throw IllegalStateException("No se pudo crear la imagen")
                contentResolver.openOutputStream(created!!)?.use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output))
                        throw IllegalStateException("Error al codificar la fotografía")
                } ?: throw IllegalStateException("No se pudo escribir la imagen")
                contentResolver.update(created!!, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
                runOnUiThread { message("Guardada en Fotos > MLV Precision") }
            } catch (error: Exception) {
                created?.let { runCatching { contentResolver.delete(it, null, null) } }
                runOnUiThread {
                    message("No se pudo guardar: ${error.localizedMessage ?: "error desconocido"}")
                }
            } finally {
                bitmap.recycle()
                runOnUiThread { saveButton.isEnabled = analyzer.angle() != null }
            }
        }.start()
    }

    private fun action(content: String, prominent: Boolean): TextView {
        val result = label(content, 13f, if (prominent) background else accent, true)
        result.gravity = Gravity.CENTER
        result.setPadding(dp(12), dp(12), dp(12), dp(12))
        result.minHeight = dp(48)
        result.background = shape(if (prominent) accent else panel, accent, 12, 1)
        result.isClickable = true
        return result
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean): TextView {
        val item = TextView(this)
        item.text = value
        item.textSize = size
        item.setTextColor(color)
        item.setLineSpacing(dp(2).toFloat(), 1f)
        if (bold) item.typeface = Typeface.DEFAULT_BOLD
        return item
    }

    private fun shape(color: Int, stroke: Int, radius: Int, line: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(line), stroke)
        }
    }

    private fun horizontal() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun addWithTopMargin(parent: LinearLayout, child: View, margin: Int) {
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT)
        params.topMargin = dp(margin)
        parent.addView(child, params)
    }

    private fun dp(amount: Int) = (amount * resources.displayMetrics.density + 0.5f).toInt()
    private fun message(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
}
