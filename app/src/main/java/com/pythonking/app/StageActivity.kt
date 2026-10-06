package com.pythonking.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
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
        private const val EXPORT_FPS = 20
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var imageView: ImageView
    private lateinit var statusText: TextView
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
    private var replaying = false
    private var lastOutput = ""
    private var pendingAction: (() -> Unit)? = null

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
        imageView = findViewById(R.id.imageView)
        statusText = findViewById(R.id.statusText)
        btnReplay = findViewById(R.id.btnReplay)
        btnExport = findViewById(R.id.btnExport)
        btnLog = findViewById(R.id.btnLog)

        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        btnReplay.setOnClickListener { replay() }
        btnExport.setOnClickListener { showExportDialog() }
        btnLog.setOnClickListener { showLog() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        updateToolbar()
        val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
        runAnimation(code)
    }

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
        main.post {
            imageView.setImageBitmap(bmp)
            statusText.visibility = View.GONE
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

    private fun runAnimation(code: String) {
        if (running) return
        if (code.isBlank()) {
            statusText.text = getString(R.string.enter_code_first)
            statusText.visibility = View.VISIBLE
            return
        }
        running = true
        busy = true
        frameCount = 0
        fpsFrames = 0
        fpsSince = 0L
        lastFps = 0.0
        imageView.setImageDrawable(null)
        statusText.text = getString(R.string.running_animation)
        statusText.visibility = View.VISIBLE
        updateButtons()

        Thread {
            var output = ""
            var error: String? = null
            try {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(applicationContext))
                }
                val py = Python.getInstance()
                val king = py.getModule("kinganim")
                king.callAttr("set_tmp_dir", cacheDir.absolutePath)
                king.callAttr("attach", FrameBridge())
                king.callAttr("clear")
                output = py.getModule("runner").callAttr("run_code", code).toString()
                val bridgeProblem = king.callAttr("bridge_error").toString()
                if (bridgeProblem.isNotBlank()) {
                    output += "\n[display problem] " + bridgeProblem + "\n"
                }
            } catch (t: Throwable) {
                error = t.message ?: t.toString()
            }
            val out = output
            val err = error
            main.post {
                running = false
                busy = false
                lastOutput = out
                if (err != null) {
                    lastOutput = "Exception: " + err + "\n\n" + out
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

    private fun updateButtons() {
        val canExport = frameCount > 0 && !running && !busy
        btnExport.isEnabled = canExport
        btnReplay.isEnabled = canExport
        btnExport.alpha = if (canExport) 1f else 0.5f
        btnReplay.alpha = if (canExport) 1f else 0.5f
    }

    private fun replay() {
        if (replaying || running || busy || frameCount <= 0) return
        replaying = true
        val total = frameCount
        Thread {
            try {
                val king = Python.getInstance().getModule("kinganim")
                for (i in 0 until total) {
                    val bytes = king.callAttr("frame_bytes", i)
                        .toJava(ByteArray::class.java) as ByteArray
                    val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                    main.post { imageView.setImageBitmap(bm) }
                    Thread.sleep((1000L / EXPORT_FPS))
                }
            } catch (_: Throwable) {
            }
            replaying = false
        }.start()
    }

    private fun showLog() {
        val text = if (lastOutput.isBlank()) getString(R.string.no_output) else lastOutput
        AlertDialog.Builder(this)
            .setTitle(R.string.log_title)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showExportDialog() {
        if (frameCount <= 0) {
            Toast.makeText(this, R.string.no_frames, Toast.LENGTH_SHORT).show()
            return
        }
        val items = arrayOf(
            getString(R.string.exp_mp4),
            getString(R.string.exp_gif),
            getString(R.string.exp_pdf),
            getString(R.string.exp_png)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.export_title, frameCount))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> exportMp4()
                    1 -> exportViaPython("gif", "gif", "image/gif")
                    2 -> exportViaPython("pdf", "pdf", "application/pdf")
                    else -> exportViaPython("png", "png", "image/png")
                }
            }
            .show()
    }

    private fun exportMp4() = withStorage {
        if (frameCount <= 0 || busy) return@withStorage
        busy = true
        updateButtons()
        toolbar.subtitle = getString(R.string.exporting)

        Thread {
            var ok = false
            var tmp: File? = null
            try {
                val king = Python.getInstance().getModule("kinganim")
                val first = king.callAttr("frame_bytes", 0)
                    .toJava(ByteArray::class.java) as ByteArray
                val b0 = BitmapFactory.decodeByteArray(first, 0, first.size)
                if (b0 != null) {
                    val w = (b0.width / 2) * 2
                    val h = (b0.height / 2) * 2
                    b0.recycle()
                    val f = File(cacheDir, "anim_" + System.currentTimeMillis() + ".mp4")
                    tmp = f
                    val enc = Mp4Encoder(w, h, EXPORT_FPS, f)
                    try {
                        for (i in 0 until frameCount) {
                            val bytes = king.callAttr("frame_bytes", i)
                                .toJava(ByteArray::class.java) as ByteArray
                            val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                            enc.addFrame(bm)
                            bm.recycle()
                        }
                    } finally {
                        if (enc.finish() && f.exists() && f.length() > 0) {
                            ok = FileSaver.save(
                                this,
                                "animation_" + System.currentTimeMillis() + ".mp4",
                                "video/mp4"
                            ) { out -> f.inputStream().use { it.copyTo(out) } }
                        }
                    }
                }
            } catch (_: Throwable) {
                ok = false
            }
            tmp?.delete()
            finishExport(ok)
        }.start()
    }

    private fun exportViaPython(kind: String, ext: String, mime: String) = withStorage {
        if (frameCount <= 0 || busy) return@withStorage
        busy = true
        updateButtons()
        toolbar.subtitle = getString(R.string.exporting)

        Thread {
            var ok = false
            var tmp: File? = null
            try {
                val king = Python.getInstance().getModule("kinganim")
                val f = File(cacheDir, "export_" + System.currentTimeMillis() + "." + ext)
                tmp = f
                val done = king.callAttr("export", f.absolutePath, kind, EXPORT_FPS, -1).toBoolean()
                if (done && f.exists() && f.length() > 0) {
                    ok = FileSaver.save(
                        this, "animation_" + System.currentTimeMillis() + "." + ext, mime
                    ) { out -> f.inputStream().use { it.copyTo(out) } }
                }
            } catch (_: Throwable) {
                ok = false
            }
            tmp?.delete()
            finishExport(ok)
        }.start()
    }

    private fun finishExport(ok: Boolean) {
        main.post {
            busy = false
            updateButtons()
            updateToolbar()
            Toast.makeText(
                this,
                if (ok) R.string.saved_to_downloads else R.string.export_failed,
                Toast.LENGTH_LONG
            ).show()
        }
    }

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
}
