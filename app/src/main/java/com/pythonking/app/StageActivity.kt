package com.pythonking.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.File

class StageActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CODE = "extra_code"
        private const val BASE_FPS = 20
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var image: ZoomImageView
    private lateinit var statusText: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var frameLabel: TextView
    private lateinit var btnPlay: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var btnSpeed: MaterialButton
    private lateinit var btnReplay: MaterialButton
    private lateinit var btnExport: MaterialButton
    private lateinit var btnLog: MaterialButton

    private val main = Handler(Looper.getMainLooper())
    private var frameCount = 0
    private var fpsFrames = 0
    private var fpsSince = 0L
    private var lastFps = 0.0
    private var running = false
    private var busy = false
    private var lastOutput = ""
    private var lastUri: Uri? = null
    private var lastMime: String? = null
    private var pendingAction: (() -> Unit)? = null

    @Volatile private var playing = false
    @Volatile private var speed = 1f

    private val requestStorage =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val action = pendingAction
            pendingAction = null
            if (granted && action != null) {
                action.invoke()
            } else if (!granted) {
                Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show()
            }
        }

    /** Python calls this for every frame it produces. */
    inner class FrameBridge {
        @Suppress("unused")
        fun onFrame(png: ByteArray, width: Int, height: Int) {
            deliver(png)
        }

        @Suppress("unused")
        fun onFrameFile(path: String, width: Int, height: Int) {
            try {
                val f = File(path)
                val bytes = f.readBytes()
                f.delete()
                deliver(bytes)
            } catch (_: Throwable) {
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stage)

        toolbar = findViewById(R.id.toolbar)
        image = findViewById(R.id.imageView)
        statusText = findViewById(R.id.statusText)
        seekBar = findViewById(R.id.seekBar)
        frameLabel = findViewById(R.id.frameLabel)
        btnPlay = findViewById(R.id.btnPlay)
        btnStop = findViewById(R.id.btnStop)
        btnSpeed = findViewById(R.id.btnSpeed)
        btnReplay = findViewById(R.id.btnReplay)
        btnExport = findViewById(R.id.btnExport)
        btnLog = findViewById(R.id.btnLog)

        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        btnPlay.setOnClickListener { togglePlay() }
        btnStop.setOnClickListener { stopScript() }
        btnSpeed.setOnClickListener { cycleSpeed() }
        btnReplay.setOnClickListener { replayFromStart() }
        btnExport.setOnClickListener { showExportDialog() }
        btnLog.setOnClickListener { showLog() }
        findViewById<View>(R.id.btnZoomIn).setOnClickListener { image.zoomIn() }
        findViewById<View>(R.id.btnZoomOut).setOnClickListener { image.zoomOut() }
        findViewById<View>(R.id.btnZoomFit).setOnClickListener { image.resetZoom() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    playing = false
                    btnPlay.setText(R.string.play)
                    frameLabel.text = getString(R.string.frame_of, progress + 1, frameCount)
                    showFrame(progress)
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                playing = false
                finish()
            }
        })

        updateToolbar()
        updateButtons()
        runAnimation(intent.getStringExtra(EXTRA_CODE).orEmpty())
    }

    override fun onDestroy() {
        playing = false
        super.onDestroy()
    }

    // ---------- frame delivery ----------

    private fun deliver(png: ByteArray) {
        val bmp = try {
            BitmapFactory.decodeByteArray(png, 0, png.size)
        } catch (_: Throwable) {
            null
        } ?: return

        frameCount++
        fpsFrames++
        val now = System.currentTimeMillis()
        if (fpsSince == 0L) fpsSince = now
        if (now - fpsSince >= 500) {
            lastFps = fpsFrames * 1000.0 / (now - fpsSince)
            fpsSince = now
            fpsFrames = 0
        }
        val count = frameCount
        main.post {
            image.setImageBitmap(bmp)
            statusText.visibility = View.GONE
            seekBar.max = (count - 1).coerceAtLeast(0)
            frameLabel.text = getString(R.string.frame_of, count, count)
            updateToolbar()
        }
    }

    private fun updateToolbar() {
        toolbar.subtitle = if (frameCount > 0) {
            getString(R.string.toolbar_stats, lastFps, frameCount)
        } else {
            getString(R.string.waiting_frames)
        }
    }

    private fun updateButtons() {
        val has = frameCount > 0
        val idle = !running && !busy
        btnExport.isEnabled = has && idle
        btnReplay.isEnabled = has && idle
        btnPlay.isEnabled = has && !running
        btnSpeed.isEnabled = has && !running
        btnStop.isEnabled = running
        btnExport.alpha = if (btnExport.isEnabled) 1f else 0.5f
        btnReplay.alpha = if (btnReplay.isEnabled) 1f else 0.5f
    }

    // ---------- python ----------

    private fun ensurePython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(applicationContext))
        }
    }

    private fun king() = Python.getInstance().getModule("kinganim")

    private fun runAnimation(code: String) {
        if (running) return
        if (code.isBlank()) {
            statusText.text = getString(R.string.enter_code_first)
            statusText.visibility = View.VISIBLE
            return
        }
        playing = false
        running = true
        frameCount = 0
        fpsFrames = 0
        fpsSince = 0L
        lastFps = 0.0
        lastOutput = ""
        image.setImageDrawable(null)
        image.resetZoom()
        statusText.text = getString(R.string.running_animation)
        statusText.visibility = View.VISIBLE
        seekBar.max = 0
        frameLabel.text = ""
        updateToolbar()
        updateButtons()

        Thread {
            var output = ""
            var error: String? = null
            try {
                ensurePython()
                val py = Python.getInstance()
                val k = py.getModule("kinganim")
                k.callAttr("set_tmp_dir", cacheDir.absolutePath)
                k.callAttr("reset_stop")
                k.callAttr("mark_thread")
                k.callAttr("attach", FrameBridge())
                k.callAttr("clear")
                output = py.getModule("runner").callAttr("run_code", code).toString()
                val problem = k.callAttr("bridge_error").toString()
                if (problem.isNotBlank()) output += "\n[display problem] " + problem + "\n"
            } catch (t: Throwable) {
                error = t.message ?: t.toString()
            }
            val out = output
            val err = error
            main.post {
                running = false
                lastOutput = if (err != null) "Exception: $err\n\n$out" else out
                if (err != null) {
                    statusText.text = getString(R.string.script_failed_tap_log)
                    statusText.visibility = View.VISIBLE
                } else if (frameCount == 0) {
                    statusText.text = getString(R.string.no_frames_tap_log)
                    statusText.visibility = View.VISIBLE
                    if (out.isNotBlank()) showLog()
                } else {
                    statusText.visibility = View.GONE
                }
                updateToolbar()
                updateButtons()
            }
        }.start()
    }

    private fun stopScript() {
        try {
            if (Python.isStarted()) king().callAttr("request_stop")
        } catch (_: Throwable) {
        }
        playing = false
        btnPlay.setText(R.string.play)
        Toast.makeText(this, R.string.stopping, Toast.LENGTH_SHORT).show()
    }

    // ---------- playback ----------

    private fun togglePlay() {
        if (playing) {
            playing = false
            btnPlay.setText(R.string.play)
            return
        }
        if (frameCount <= 0 || running) return
        playing = true
        btnPlay.setText(R.string.pause)
        Thread {
            var i = seekBar.progress.coerceIn(0, (frameCount - 1).coerceAtLeast(0))
            while (playing) {
                showFrame(i)
                val at = i
                main.post {
                    seekBar.progress = at
                    frameLabel.text = getString(R.string.frame_of, at + 1, frameCount)
                }
                i++
                if (i >= frameCount) {
                    if (!Prefs.loop(this)) break
                    i = 0
                }
                val delay = (1000.0 / (BASE_FPS * speed)).toLong().coerceAtLeast(8L)
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    break
                }
            }
            playing = false
            main.post { btnPlay.setText(R.string.play) }
        }.start()
    }

    private fun replayFromStart() {
        if (frameCount <= 0) return
        playing = false
        btnPlay.setText(R.string.play)
        seekBar.progress = 0
        showFrame(0)
        frameLabel.text = getString(R.string.frame_of, 1, frameCount)
    }

    private fun cycleSpeed() {
        speed = when (speed) {
            0.5f -> 1f
            1f -> 2f
            2f -> 4f
            else -> 0.5f
        }
        btnSpeed.text = getString(R.string.speed_fmt, speed)
    }

    private fun showFrame(index: Int) {
        try {
            val bytes = king().callAttr("frame_bytes", index)
                .toJava(ByteArray::class.java) as ByteArray
            val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            main.post {
                image.setImageBitmap(bm)
                statusText.visibility = View.GONE
            }
        } catch (_: Throwable) {
        }
    }

    // ---------- log ----------

    private fun showLog() {
        val text = if (lastOutput.isBlank()) getString(R.string.no_output) else lastOutput
        AlertDialog.Builder(this)
            .setTitle(R.string.log_title)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------- export ----------

    private fun showExportDialog() {
        if (frameCount <= 0) {
            Toast.makeText(this, R.string.no_frames, Toast.LENGTH_SHORT).show()
            return
        }
        val items = arrayOf(
            getString(R.string.exp_mp4),
            getString(R.string.exp_gif),
            getString(R.string.exp_webp),
            getString(R.string.exp_apng),
            getString(R.string.exp_pdf),
            getString(R.string.exp_png),
            getString(R.string.exp_sprite),
            getString(R.string.exp_zip)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.export_title, frameCount))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> chooseFps()
                    1 -> exportViaPython("gif", "gif", "image/gif")
                    2 -> exportViaPython("webp", "webp", "image/webp")
                    3 -> exportViaPython("apng", "png", "image/png")
                    4 -> exportViaPython("pdf", "pdf", "application/pdf")
                    5 -> exportViaPython("png", "png", "image/png")
                    6 -> exportViaPython("sprite", "png", "image/png")
                    7 -> exportViaPython("zip", "zip", "application/zip")
                }
            }
            .show()
    }

    private fun chooseFps() {
        val fps = intArrayOf(15, 24, 30, 60)
        val labels = fps.map { "$it fps" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_fps)
            .setItems(labels) { _, w -> chooseResolution(fps[w]) }
            .show()
    }

    private fun chooseResolution(fps: Int) {
        val widths = intArrayOf(0, 1080, 720, 480)
        val labels = arrayOf(
            getString(R.string.res_source),
            "1080p",
            "720p",
            "480p"
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_resolution)
            .setItems(labels) { _, w -> exportMp4(fps, widths[w]) }
            .show()
    }

    private fun exportMp4(fps: Int, maxWidth: Int) = withStorage {
        if (frameCount <= 0 || busy) return@withStorage
        busy = true
        updateButtons()
        toolbar.subtitle = getString(R.string.exporting)

        Thread {
            var uri: Uri? = null
            var tmp: File? = null
            try {
                val k = king()
                val first = k.callAttr("frame_bytes", 0)
                    .toJava(ByteArray::class.java) as ByteArray
                val b0 = BitmapFactory.decodeByteArray(first, 0, first.size)
                if (b0 != null) {
                    var w = b0.width
                    var h = b0.height
                    b0.recycle()
                    val cap = if (maxWidth > 0) maxWidth else 1080
                    if (w > cap) {
                        h = (h.toLong() * cap / w).toInt()
                        w = cap
                    }
                    w = (w / 2) * 2
                    h = (h / 2) * 2
                    val f = File(cacheDir, "anim_" + System.currentTimeMillis() + ".mp4")
                    tmp = f
                    val enc = Mp4Encoder(w, h, fps, f)
                    try {
                        for (i in 0 until frameCount) {
                            val bytes = k.callAttr("frame_bytes", i)
                                .toJava(ByteArray::class.java) as ByteArray
                            val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                            enc.addFrame(bm)
                            bm.recycle()
                        }
                    } finally {
                        if (enc.finish() && f.exists() && f.length() > 0) {
                            uri = FileSaver.save(
                                this,
                                "animation_" + System.currentTimeMillis() + ".mp4",
                                "video/mp4"
                            ) { out -> f.inputStream().use { it.copyTo(out) } }
                        }
                    }
                }
            } catch (_: Throwable) {
                uri = null
            }
            tmp?.delete()
            finishExport(uri, "video/mp4")
        }.start()
    }

    private fun exportViaPython(kind: String, ext: String, mime: String) = withStorage {
        if (frameCount <= 0 || busy) return@withStorage
        busy = true
        updateButtons()
        toolbar.subtitle = getString(R.string.exporting)

        Thread {
            var uri: Uri? = null
            var tmp: File? = null
            try {
                val k = king()
                val f = File(cacheDir, "export_" + System.currentTimeMillis() + "." + ext)
                tmp = f
                val done = k.callAttr("export", f.absolutePath, kind, BASE_FPS, -1).toBoolean()
                if (done && f.exists() && f.length() > 0) {
                    uri = FileSaver.save(
                        this, "animation_" + System.currentTimeMillis() + "." + ext, mime
                    ) { out -> f.inputStream().use { it.copyTo(out) } }
                }
            } catch (_: Throwable) {
                uri = null
            }
            tmp?.delete()
            finishExport(uri, mime)
        }.start()
    }

    private fun finishExport(uri: Uri?, mime: String) {
        lastUri = uri
        lastMime = mime
        main.post {
            busy = false
            updateButtons()
            updateToolbar()
            if (uri == null) {
                Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle(R.string.saved_title)
                    .setMessage(R.string.saved_to_downloads)
                    .setPositiveButton(R.string.share) { _, _ -> shareLast() }
                    .setNegativeButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun shareLast() {
        val uri = lastUri ?: return
        try {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = lastMime ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, getString(R.string.share)))
        } catch (_: Exception) {
        }
    }

    // ---------- storage ----------

    private fun withStorage(action: () -> Unit) {
        if (hasStoragePermission()) {
            action()
        } else {
            pendingAction = action
            requestStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun hasStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ---------- menu ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.stage_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_loop)?.isChecked = Prefs.loop(this)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset_zoom -> {
                image.resetZoom(); true
            }
            R.id.action_loop -> {
                item.isChecked = !item.isChecked
                Prefs.setLoop(this, item.isChecked)
                true
            }
            R.id.action_stop_script -> {
                stopScript(); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
