package com.pythonking.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

class StageActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CODE = "extra_code"
        private const val EXPORT_FPS = 20
    }

    private lateinit var imageView: ImageView
    private lateinit var fpsText: TextView
    private lateinit var statusText: TextView
    private lateinit var overlay: View

    private val main = Handler(Looper.getMainLooper())
    private var frameCount = 0
    private var fpsFrames = 0
    private var fpsSince = 0L
    private var running = false
    private var replaying = false
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

    /** Python calls onFrame() for every frame it produces. */
    inner class FrameBridge {
        @Suppress("unused")
        fun onFrame(png: ByteArray, width: Int, height: Int) {
            val bmp = try {
                BitmapFactory.decodeByteArray(png, 0, png.size)
            } catch (_: Throwable) {
                null
            } ?: return

            frameCount++
            fpsFrames++
            val now = System.currentTimeMillis()
            if (fpsSince == 0L) fpsSince = now
            var fps = -1.0
            if (now - fpsSince >= 500) {
                fps = fpsFrames * 1000.0 / (now - fpsSince)
                fpsSince = now
                fpsFrames = 0
            }
            val shownFps = fps
            val count = frameCount
            main.post {
                imageView.setImageBitmap(bmp)
                if (shownFps >= 0) fpsText.text = getString(R.string.fps_fmt, shownFps)
                statusText.text = getString(R.string.frame_fmt, count)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stage)

        imageView = findViewById(R.id.imageView)
        fpsText = findViewById(R.id.fpsText)
        statusText = findViewById(R.id.statusText)
        overlay = findViewById(R.id.overlay)

        enterImmersive()

        imageView.setOnClickListener {
            overlay.visibility =
                if (overlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.btnStop).setOnClickListener { finish() }
        findViewById<View>(R.id.btnReplay).setOnClickListener { replay() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
        runAnimation(code)
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun runAnimation(code: String) {
        if (running || code.isBlank()) return
        running = true
        frameCount = 0
        fpsFrames = 0
        fpsSince = 0L
        imageView.setImageDrawable(null)
        statusText.text = getString(R.string.running)

        Thread {
            try {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(applicationContext))
                }
                val py = Python.getInstance()
                val king = py.getModule("kinganim")
                king.callAttr("attach", FrameBridge())
                king.callAttr("clear")
                val out = py.getModule("runner").callAttr("run_code", code).toString()
                val n = king.callAttr("frame_count").toInt()
                main.post {
                    running = false
                    statusText.text = if (n > 0) {
                        getString(R.string.frames_ready, n)
                    } else {
                        getString(R.string.no_frames)
                    }
                    if (out.isNotBlank() && n == 0) {
                        Toast.makeText(this, out, Toast.LENGTH_LONG).show()
                    }
                    invalidateOptionsMenu()
                }
            } catch (t: Throwable) {
                val msg = t.message ?: t.toString()
                main.post {
                    running = false
                    statusText.text = getString(R.string.python_error)
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    invalidateOptionsMenu()
                }
            }
        }.start()
    }

    private fun replay() {
        if (replaying || running || frameCount <= 0) return
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

    // ---------- export ----------

    private fun exportMp4() = withStorage {
        val count = frameCount
        if (count <= 0) {
            Toast.makeText(this, R.string.no_frames, Toast.LENGTH_SHORT).show()
            return@withStorage
        }
        statusText.text = getString(R.string.exporting)

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
                    for (i in 0 until count) {
                        val bytes = king.callAttr("frame_bytes", i)
                            .toJava(ByteArray::class.java) as ByteArray
                        val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                        enc.addFrame(bm)
                        bm.recycle()
                    }
                    val done = enc.finish()
                    if (done && f.exists() && f.length() > 0) {
                        ok = FileSaver.save(
                            this, "animation_" + System.currentTimeMillis() + ".mp4", "video/mp4"
                        ) { out -> f.inputStream().use { it.copyTo(out) } }
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
        val count = frameCount
        if (count <= 0) {
            Toast.makeText(this, R.string.no_frames, Toast.LENGTH_SHORT).show()
            return@withStorage
        }
        statusText.text = getString(R.string.exporting)

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
            statusText.text = getString(R.string.frames_ready, frameCount)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.stage_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val on = frameCount > 0 && !running
        menu.findItem(R.id.action_save_mp4)?.isEnabled = on
        menu.findItem(R.id.action_save_gif)?.isEnabled = on
        menu.findItem(R.id.action_save_pdf)?.isEnabled = on
        menu.findItem(R.id.action_save_png)?.isEnabled = on
        menu.findItem(R.id.action_replay)?.isEnabled = on
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_replay -> {
                replay(); true
            }
            R.id.action_save_mp4 -> {
                exportMp4(); true
            }
            R.id.action_save_gif -> {
                exportViaPython("gif", "gif", "image/gif"); true
            }
            R.id.action_save_pdf -> {
                exportViaPython("pdf", "pdf", "application/pdf"); true
            }
            R.id.action_save_png -> {
                exportViaPython("png", "png", "image/png"); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
