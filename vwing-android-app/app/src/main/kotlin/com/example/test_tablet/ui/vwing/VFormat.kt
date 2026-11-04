package com.example.test_tablet.ui.vwing

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val vi = DecimalFormatSymbols(Locale("vi", "VN"))

fun fmtNum(n: Double, digits: Int = 2): String =
    DecimalFormat("#,##0" + if (digits > 0) "." + "#".repeat(digits) else "", vi).format(n)

fun fmtInt(n: Int): String = DecimalFormat("#,##0", vi).format(n)

fun fmtVnd(n: Double): String = DecimalFormat("#,##0", vi).format(Math.round(n)) + " ₫"

fun fmtKg(n: Double): String = fmtNum(n, if (n < 100) 1 else 0) + " kg"

/** Đọc số người dùng nhập (chấp nhận dấu phẩy thập phân). */
fun parseNum(s: String): Double? = s.trim().replace(" ", "").replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
