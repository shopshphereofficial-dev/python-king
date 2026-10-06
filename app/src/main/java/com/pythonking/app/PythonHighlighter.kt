package com.pythonking.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.widget.EditText
import androidx.core.content.ContextCompat

/** Lightweight Python syntax highlighter for an EditText. */
class PythonHighlighter(
    private val context: Context,
    private val edit: EditText,
    private val onChanged: () -> Unit
) : TextWatcher {

    private val handler = Handler(Looper.getMainLooper())
    private var applying = false

    private val cKeyword = ContextCompat.getColor(context, R.color.syn_keyword)
    private val cBuiltin = ContextCompat.getColor(context, R.color.syn_builtin)
    private val cString = ContextCompat.getColor(context, R.color.syn_string)
    private val cComment = ContextCompat.getColor(context, R.color.syn_comment)
    private val cNumber = ContextCompat.getColor(context, R.color.syn_number)
    private val cDecorator = ContextCompat.getColor(context, R.color.syn_decorator)

    private val reComment = Regex("#[^\n]*")
    private val reTriple = Regex("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''")
    private val reString = Regex("\"[^\"\n]*\"|'[^'\n]*'")
    private val reDecorator = Regex("(?m)^[ \t]*@[A-Za-z_][A-Za-z0-9_.]*")
    private val reKeyword = Regex(
        "\\b(def|class|return|if|elif|else|for|while|import|from|as|in|not|and|or|is|" +
            "None|True|False|try|except|finally|with|lambda|pass|break|continue|global|" +
            "nonlocal|yield|async|await|raise|assert|del|print)\\b"
    )
    private val reBuiltin = Regex(
        "\\b(len|range|enumerate|zip|map|filter|sorted|sum|min|max|abs|round|int|float|" +
            "str|list|dict|set|tuple|bool|type|isinstance|open|super|self|input|format|" +
            "repr|divmod|pow|any|all|chr|ord|hex|bin|id|iter|next|slice|object)\\b"
    )
    private val reNumber = Regex("\\b\\d+\\.?\\d*\\b")

    private val runnable = Runnable { highlight() }

    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

    override fun afterTextChanged(s: Editable?) {
        onChanged()
        if (applying) return
        handler.removeCallbacks(runnable)
        handler.postDelayed(runnable, 140)
    }

    fun highlightNow() {
        handler.removeCallbacks(runnable)
        highlight()
    }

    private fun highlight() {
        val sp = edit.text as? Spannable ?: return
        applying = true
        for (span in sp.getSpans(0, sp.length, ForegroundColorSpan::class.java)) {
            sp.removeSpan(span)
        }
        val n = sp.length
        if (n > 0) {
            val used = BooleanArray(n)
            for (m in reComment.findAll(sp)) paint(sp, used, m.range, cComment)
            for (m in reTriple.findAll(sp)) paint(sp, used, m.range, cString)
            for (m in reString.findAll(sp)) paint(sp, used, m.range, cString)
            for (m in reDecorator.findAll(sp)) paint(sp, used, m.range, cDecorator)
            for (m in reKeyword.findAll(sp)) paint(sp, used, m.range, cKeyword)
            for (m in reBuiltin.findAll(sp)) paint(sp, used, m.range, cBuiltin)
            for (m in reNumber.findAll(sp)) paint(sp, used, m.range, cNumber)
        }
        applying = false
        onChanged()
    }

    private fun paint(sp: Spannable, used: BooleanArray, range: IntRange, color: Int) {
        if (range.first < 0 || range.last >= used.size) return
        for (i in range) if (used[i]) return
        for (i in range) used[i] = true
        sp.setSpan(
            ForegroundColorSpan(color),
            range.first,
            range.last + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
}
