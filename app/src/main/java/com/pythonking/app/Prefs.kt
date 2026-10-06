package com.pythonking.app

import android.content.Context

object Prefs {
    private const val FILE = "pythonking"
    private const val KEY_THEME = "theme"
    private const val KEY_FONT = "font_size"
    private const val KEY_LOOP = "loop"

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun theme(c: Context): Int = sp(c).getInt(KEY_THEME, THEME_SYSTEM)
    fun setTheme(c: Context, v: Int) = sp(c).edit().putInt(KEY_THEME, v).apply()

    fun fontSize(c: Context): Int = sp(c).getInt(KEY_FONT, 13)
    fun setFontSize(c: Context, v: Int) = sp(c).edit().putInt(KEY_FONT, v).apply()

    fun loop(c: Context): Boolean = sp(c).getBoolean(KEY_LOOP, true)
    fun setLoop(c: Context, v: Boolean) = sp(c).edit().putBoolean(KEY_LOOP, v).apply()
}
