package com.pythonking.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var etCode: EditText
    private lateinit var gutter: LineNumberView
    private lateinit var tvOutput: TextView
    private lateinit var btnRun: Button
    private lateinit var highlighter: PythonHighlighter

    private var running = false
    private var pendingAction: (() -> Unit)? = null

    private val openPy = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { loadScript(it) }
    }

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

    private val snippets = listOf(
        "Pillow frame loop" to """
            from PIL import Image, ImageDraw
            import math
            import kinganim

            W = H = 480
            N = 90
            for i in range(N):
                t = i / N
                img = Image.new("RGB", (W, H), (10, 12, 30))
                d = ImageDraw.Draw(img)
                d.ellipse([W / 2 - 40, H / 2 - 40, W / 2 + 40, H / 2 + 40], fill=(0, 200, 255))
                kinganim.show(img)

            print("frames:", kinganim.frame_count())
        """.trimIndent(),
        "numpy array frame" to """
            import numpy as np
            import kinganim

            W = H = 320
            N = 60
            x = np.linspace(-3, 3, W)
            y = np.linspace(-3, 3, H)
            X, Y = np.meshgrid(x, y)
            for i in range(N):
                t = i / N * 2 * np.pi
                r = np.sqrt(X ** 2 + Y ** 2)
                kinganim.show(np.sin(6 * r - 3 * t))

            print("frames:", kinganim.frame_count())
        """.trimIndent(),
        "matplotlib loop" to """
            import numpy as np
            import matplotlib
            matplotlib.use("Agg")
            import matplotlib.pyplot as plt
            import kinganim

            x = np.linspace(0, 4 * np.pi, 200)
            N = 60
            for i in range(N):
                fig = plt.figure(figsize=(4, 3), dpi=100)
                ax = fig.add_subplot(111)
                ax.plot(x, np.sin(x - i / 6.0), color="#00d0ff", linewidth=2)
                ax.set_ylim(-1.6, 1.6)
                fig.tight_layout()
                kinganim.show(fig)
                plt.close(fig)

            print("frames:", kinganim.frame_count())
        """.trimIndent(),
        "Pillow shapes" to """
            from PIL import Image, ImageDraw
            import kinganim

            img = Image.new("RGB", (480, 480), (18, 18, 24))
            d = ImageDraw.Draw(img)
            d.rectangle([60, 60, 200, 200], fill=(255, 120, 60))
            d.polygon([(300, 120), (420, 300), (180, 300)], fill=(60, 200, 255))
            d.line([40, 400, 440, 400], fill=(255, 255, 255), width=6)
            kinganim.show(img)
        """.trimIndent(),
        "Stop check inside a loop" to """
            import kinganim

            for i in range(500):
                # kinganim.show() also checks the Stop button for you
                kinganim.show(__import__("PIL").Image.new("RGB", (320, 320), (i % 255, 60, 90)))

            print("frames:", kinganim.frame_count())
        """.trimIndent()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        etCode = findViewById(R.id.etCode)
        gutter = findViewById(R.id.gutter)
        tvOutput = findViewById(R.id.tvOutput)
        btnRun = findViewById(R.id.btnRun)

        gutter.target = etCode
        highlighter = PythonHighlighter(this, etCode) { gutter.invalidate() }
        etCode.addTextChangedListener(highlighter)
        etCode.setOnScrollChangeListener { _, _, _, _, _ -> gutter.invalidate() }

        btnRun.setOnClickListener { toggleRun() }
        findViewById<Button>(R.id.btnAnimate).setOnClickListener { openStage() }
        findViewById<Button>(R.id.btnPaste).setOnClickListener { pasteFromClipboard() }
        findViewById<Button>(R.id.btnCopy).setOnClickListener { copyToClipboard() }
        findViewById<Button>(R.id.btnSelectAll).setOnClickListener { selectAll() }
        findViewById<Button>(R.id.btnExamples).setOnClickListener { showExamples() }
        findViewById<Button>(R.id.btnOpenPy).setOnClickListener {
            openPy.launch(arrayOf("text/x-python", "text/plain", "*/*"))
        }
        findViewById<Button>(R.id.btnSaveScript).setOnClickListener { saveToLibrary() }
        findViewById<Button>(R.id.btnClear).setOnClickListener { etCode.setText("") }

        applyFontSize()
        loadDraft()
    }

    override fun onPause() {
        super.onPause()
        saveDraft()
    }

    // ---------- menu ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_library -> {
                openLibrary(); true
            }
            R.id.action_snippets -> {
                showSnippets(); true
            }
            R.id.action_export_py -> {
                exportPyToDownloads(); true
            }
            R.id.action_packages -> {
                showPackages(); true
            }
            R.id.action_guide -> {
                showDialog(getString(R.string.guide_title), Guide.TEXT); true
            }
            R.id.action_theme -> {
                chooseTheme(); true
            }
            R.id.action_font -> {
                chooseFontSize(); true
            }
            R.id.action_about -> {
                showDialog(getString(R.string.about_title), getString(R.string.about_text)); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------- theme / font ----------

    private fun chooseTheme() {
        val items = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.theme)
            .setSingleChoiceItems(items, Prefs.theme(this)) { d, w ->
                Prefs.setTheme(this, w)
                AppCompatDelegate.setDefaultNightMode(
                    when (w) {
                        Prefs.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                        Prefs.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
                d.dismiss()
            }
            .show()
    }

    private fun chooseFontSize() {
        val sizes = intArrayOf(11, 13, 15, 17, 20)
        val labels = sizes.map { "$it sp" }.toTypedArray()
        var cur = 1
        for (i in sizes.indices) if (sizes[i] == Prefs.fontSize(this)) cur = i
        AlertDialog.Builder(this)
            .setTitle(R.string.font_size)
            .setSingleChoiceItems(labels, cur) { d, w ->
                Prefs.setFontSize(this, sizes[w])
                applyFontSize()
                d.dismiss()
            }
            .show()
    }

    private fun applyFontSize() {
        val sp = Prefs.fontSize(this)
        etCode.textSize = sp.toFloat()
        gutter.setTextSizePx(sp * resources.displayMetrics.scaledDensity)
        gutter.invalidate()
    }

    // ---------- draft ----------

    private fun draftFile() = File(filesDir, "draft.py")

    private fun saveDraft() {
        try {
            draftFile().writeText(etCode.text.toString())
        } catch (_: Exception) {
        }
    }

    private fun loadDraft() {
        try {
            val f = draftFile()
            if (f.exists() && etCode.text.isNullOrBlank()) {
                etCode.setText(f.readText())
                highlighter.highlightNow()
            }
        } catch (_: Exception) {
        }
    }

    // ---------- clipboard ----------

    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val text = clip.getItemAt(0).coerceToText(this).toString()
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val start = etCode.selectionStart
        val end = etCode.selectionEnd
        if (start >= 0 && end >= start) {
            etCode.text.replace(start, end, text)
        } else {
            etCode.append(text)
        }
    }

    private fun copyToClipboard() {
        val full = etCode.text.toString()
        if (full.isEmpty()) {
            Toast.makeText(this, R.string.nothing_to_copy, Toast.LENGTH_SHORT).show()
            return
        }
        val start = etCode.selectionStart
        val end = etCode.selectionEnd
        val text = if (start >= 0 && end > start) full.substring(start, end) else full
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Python code", text))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun selectAll() {
        etCode.requestFocus()
        etCode.selectAll()
    }

    // ---------- examples / snippets / library ----------

    private fun showExamples() {
        val files = try {
            assets.list("examples")?.sorted() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        if (files.isEmpty()) {
            Toast.makeText(this, R.string.no_examples, Toast.LENGTH_SHORT).show()
            return
        }
        val titles = files.map { it.removeSuffix(".py").replace('_', ' ') }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.examples)
            .setItems(titles) { _, w ->
                try {
                    val text = assets.open("examples/" + files[w]).bufferedReader().readText()
                    etCode.setText(text)
                    highlighter.highlightNow()
                } catch (e: Exception) {
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun showSnippets() {
        val names = snippets.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.snippets)
            .setItems(names) { _, w ->
                val code = snippets[w].second
                val start = etCode.selectionStart.coerceAtLeast(0)
                etCode.text.insert(start, code + "\n")
                highlighter.highlightNow()
            }
            .show()
    }

    private fun scriptsDir(): File {
        val d = File(filesDir, "scripts")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun saveToLibrary() {
        val code = etCode.text.toString()
        if (code.isBlank()) {
            Toast.makeText(this, R.string.enter_code_first, Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this)
        input.hint = getString(R.string.script_name)
        AlertDialog.Builder(this)
            .setTitle(R.string.save_script)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val raw = input.text.toString().trim().ifBlank { "script" }
                val safe = raw.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
                try {
                    File(scriptsDir(), "$safe.py").writeText(code)
                    Toast.makeText(
                        this, getString(R.string.script_saved_lib, safe), Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openLibrary() {
        val files = scriptsDir().listFiles { f -> f.name.endsWith(".py") }?.sortedBy { it.name }
            ?: emptyList()
        if (files.isEmpty()) {
            Toast.makeText(this, R.string.no_scripts, Toast.LENGTH_SHORT).show()
            return
        }
        val names = files.map { it.name.removeSuffix(".py") }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.my_scripts)
            .setItems(names) { _, w ->
                try {
                    etCode.setText(files[w].readText())
                    highlighter.highlightNow()
                } catch (e: Exception) {
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton(R.string.delete_script) { _, _ -> deleteScript(files) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteScript(files: List<File>) {
        val names = files.map { it.name.removeSuffix(".py") }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_script)
            .setItems(names) { _, w ->
                val ok = files[w].delete()
                Toast.makeText(
                    this, if (ok) R.string.deleted else R.string.save_failed, Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun loadScript(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            if (text.isNullOrBlank()) {
                Toast.makeText(this, R.string.could_not_read, Toast.LENGTH_SHORT).show()
                return
            }
            etCode.setText(text)
            highlighter.highlightNow()
        } catch (e: Exception) {
            Toast.makeText(
                this, getString(R.string.could_not_read) + ": " + e.message, Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun exportPyToDownloads() {
        val code = etCode.text.toString()
        if (code.isBlank()) {
            Toast.makeText(this, R.string.enter_code_first, Toast.LENGTH_SHORT).show()
            return
        }
        withStorage {
            val name = "script_" + System.currentTimeMillis() + ".py"
            val uri = FileSaver.save(this, name, "text/x-python") { out ->
                out.write(code.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(
                this, if (uri != null) R.string.script_saved else R.string.save_failed,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ---------- run ----------

    private fun ensurePython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(applicationContext))
        }
    }

    private fun toggleRun() {
        if (running) {
            requestStop()
        } else {
            runCode()
        }
    }

    private fun requestStop() {
        try {
            if (Python.isStarted()) {
                Python.getInstance().getModule("kinganim").callAttr("request_stop")
            }
        } catch (_: Throwable) {
        }
    }

    private fun runCode() {
        val code = etCode.text.toString()
        if (code.isBlank()) {
            Toast.makeText(this, R.string.enter_code_first, Toast.LENGTH_SHORT).show()
            return
        }
        running = true
        btnRun.setText(R.string.stop)
        tvOutput.text = getString(R.string.running)

        Thread {
            val out = try {
                ensurePython()
                val py = Python.getInstance()
                val king = py.getModule("kinganim")
                king.callAttr("set_tmp_dir", cacheDir.absolutePath)
                king.callAttr("reset_stop")
                king.callAttr("mark_thread")
                py.getModule("runner").callAttr("run_code", code).toString()
            } catch (t: Throwable) {
                getString(R.string.python_error) + ": " + (t.message ?: t.toString()) + "\n"
            }
            runOnUiThread {
                running = false
                btnRun.setText(R.string.run)
                tvOutput.text = if (out.isBlank()) getString(R.string.no_output) else out
            }
        }.start()
    }

    private fun openStage() {
        val code = etCode.text.toString()
        if (code.isBlank()) {
            Toast.makeText(this, R.string.enter_code_first, Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(this, StageActivity::class.java)
        i.putExtra(StageActivity.EXTRA_CODE, code)
        startActivity(i)
    }

    private fun showPackages() {
        tvOutput.text = getString(R.string.running)
        Thread {
            val out = try {
                ensurePython()
                Python.getInstance().getModule("pkinfo").callAttr("report").toString()
            } catch (t: Throwable) {
                "Error: " + (t.message ?: t.toString())
            }
            runOnUiThread { showDialog(getString(R.string.packages_title), out) }
        }.start()
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
}
