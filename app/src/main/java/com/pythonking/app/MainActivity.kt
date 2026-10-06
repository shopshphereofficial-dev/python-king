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
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : AppCompatActivity() {

    private lateinit var etCode: EditText
    private lateinit var tvOutput: TextView
    private lateinit var btnRun: Button
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        etCode = findViewById(R.id.etCode)
        tvOutput = findViewById(R.id.tvOutput)
        btnRun = findViewById(R.id.btnRun)

        btnRun.setOnClickListener { runCode() }
        findViewById<Button>(R.id.btnAnimate).setOnClickListener { openStage() }
        findViewById<Button>(R.id.btnPaste).setOnClickListener { pasteFromClipboard() }
        findViewById<Button>(R.id.btnCopy).setOnClickListener { copyToClipboard() }
        findViewById<Button>(R.id.btnSelectAll).setOnClickListener { selectAll() }
        findViewById<Button>(R.id.btnSample).setOnClickListener { showSamples() }
        findViewById<Button>(R.id.btnClear).setOnClickListener { etCode.setText("") }
        findViewById<Button>(R.id.btnOpenPy).setOnClickListener {
            openPy.launch(arrayOf("text/x-python", "text/plain", "*/*"))
        }
        findViewById<Button>(R.id.btnSaveScript).setOnClickListener { saveScript() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_guide -> {
                showDialog(getString(R.string.guide_title), Guide.TEXT)
                true
            }
            R.id.action_about -> {
                showDialog(getString(R.string.about_title), getString(R.string.about_text))
                true
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

    // ---------- scripts ----------

    private fun showSamples() {
        val names = Samples.ALL.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_sample)
            .setItems(names) { _, which ->
                etCode.setText(Samples.ALL[which].second)
            }
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
        } catch (e: Exception) {
            Toast.makeText(
                this, getString(R.string.could_not_read) + ": " + e.message, Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveScript() {
        val code = etCode.text.toString()
        if (code.isBlank()) {
            Toast.makeText(this, R.string.enter_code_first, Toast.LENGTH_SHORT).show()
            return
        }
        withStorage {
            val name = "script_" + System.currentTimeMillis() + ".py"
            val ok = FileSaver.save(this, name, "text/x-python") { out ->
                out.write(code.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(
                this,
                if (ok) R.string.script_saved else R.string.save_failed,
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

    private fun runCode() {
        val code = etCode.text.toString()
        if (code.isBlank()) {
            Toast.makeText(this, R.string.enter_code_first, Toast.LENGTH_SHORT).show()
            return
        }
        btnRun.isEnabled = false
        tvOutput.text = getString(R.string.running)

        Thread {
            val out = try {
                ensurePython()
                Python.getInstance().getModule("runner").callAttr("run_code", code).toString()
            } catch (t: Throwable) {
                getString(R.string.python_error) + ": " + (t.message ?: t.toString()) + "\n"
            }
            runOnUiThread {
                tvOutput.text = if (out.isBlank()) getString(R.string.no_output) else out
                btnRun.isEnabled = true
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
