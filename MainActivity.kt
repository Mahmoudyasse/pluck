package com.pluck.app

import android.app.Activity
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

class MainActivity : Activity() {
    private val bg = Color.parseColor("#0B0718")
    private val card = Color.parseColor("#120B2E")
    private val edge = Color.parseColor("#2A1F5C")
    private val lime = Color.parseColor("#C6FF00")
    private val pink = Color.parseColor("#FF2E93")
    private val grey = Color.parseColor("#9A94B8")
    private lateinit var urlBox: EditText
    private lateinit var goBtn: Button
    private lateinit var bar: ProgressBar
    private lateinit var status: TextView
    private lateinit var audioBtn: RadioButton
    @Volatile private var ready = false
    private var busy = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun shape(fill: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(12).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun lp(top: Int = 10) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun label(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun button(s: String, fill: Int, textColor: Int, stroke: Int? = null) = Button(this).apply {
        text = s
        isAllCaps = false
        textSize = 16f
        setTextColor(textColor)
        background = shape(fill, stroke)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(56), dp(24), dp(24))
        }
        root.addView(col)

        col.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) },
            LinearLayout.LayoutParams(dp(88), dp(88)))
        col.addView(label("Pluck", 32f, lime, true), lp(8))
        col.addView(label("انسخ الرابط، الصقه، ونزّل", 15f, grey), lp(2))

        urlBox = EditText(this).apply {
            hint = "https://..."
            setHintTextColor(grey)
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            maxLines = 1
            background = shape(card, edge)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        col.addView(urlBox, lp(24))

        val pasteBtn = button("لصق من الحافظة", card, Color.WHITE, edge)
        pasteBtn.setOnClickListener { paste() }
        col.addView(pasteBtn, lp())

        val rg = RadioGroup(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val videoBtn = RadioButton(this).apply { text = "فيديو MP4"; setTextColor(Color.WHITE); id = View.generateViewId(); isChecked = true }
        audioBtn = RadioButton(this).apply { text = "صوت MP3"; setTextColor(Color.WHITE); id = View.generateViewId() }
        rg.addView(videoBtn)
        rg.addView(audioBtn)
        col.addView(rg, lp())

        goBtn = button("تحميل", lime, card)
        goBtn.setOnClickListener { start() }
        col.addView(goBtn, lp())

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(lime)
            visibility = View.GONE
        }
        col.addView(bar, lp(14))

        status = label("جاري التجهيز...", 14f, pink)
        col.addView(status, lp(10))

        setContentView(root)
        handleIntent(intent)

        Thread {
            try {
                YoutubeDL.getInstance().init(application)
                FFmpeg.getInstance().init(application)
                ready = true
                runOnUiThread { status.text = "" }
            } catch (e: Throwable) {
                runOnUiThread { status.text = "فشل التجهيز: " + (e.message ?: "").take(160) }
            }
        }.start()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            val t = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            urlBox.setText(Regex("https?://\\S+").find(t)?.value ?: t)
        }
    }

    private fun paste() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val t = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this)?.toString() else null
        if (!t.isNullOrBlank()) urlBox.setText(t.trim()) else status.text = "الحافظة فاضية"
    }

    private fun start() {
        val url = urlBox.text.toString().trim()
        if (!url.startsWith("http")) { status.text = "الرابط غير صحيح"; return }
        if (!ready) { status.text = "استنى شوية، التطبيق بيتجهز"; return }
        if (busy) return
        busy = true
        goBtn.isEnabled = false
        bar.progress = 0
        bar.visibility = View.VISIBLE
        status.text = "جاري التحميل..."
        val audio = audioBtn.isChecked

        Thread {
            try {
                val tmp = File(cacheDir, "dl")
                tmp.deleteRecursively()
                tmp.mkdirs()
                val req = YoutubeDLRequest(url)
                req.addOption("--no-playlist")
                req.addOption("-o", tmp.absolutePath + "/%(title).80s.%(ext)s")
                if (audio) {
                    req.addOption("-x")
                    req.addOption("--audio-format", "mp3")
                } else {
                    req.addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/b")
                    req.addOption("--merge-output-format", "mp4")
                }
                YoutubeDL.getInstance().execute(req, "pluck") { p, _, _ ->
                    runOnUiThread { bar.progress = p.toInt().coerceIn(0, 100) }
                }
                val f = tmp.listFiles()?.maxByOrNull { it.length() } ?: throw Exception("الملف غير موجود")
                save(f)
                runOnUiThread { status.text = "✅ تم الحفظ في مجلد Download/Pluck" }
            } catch (e: Throwable) {
                runOnUiThread { status.text = "فشل التحميل: " + (e.message ?: "").take(160) }
            } finally {
                runOnUiThread { busy = false; goBtn.isEnabled = true; bar.visibility = View.GONE }
            }
        }.start()
    }

    private fun save(f: File) {
        val mime = when (f.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "webm" -> "video/webm"
            else -> "application/octet-stream"
        }
        val v = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, f.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Pluck")
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: throw Exception("تعذر إنشاء الملف")
        contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
        f.delete()
    }
}
