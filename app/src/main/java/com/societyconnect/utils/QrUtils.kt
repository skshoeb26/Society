package com.societyconnect.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private const val VISITOR_QR_PREFIX = "SC-VISITOR:"

fun visitorQrContent(societyId: String, visitorId: String) = "$VISITOR_QR_PREFIX$societyId:$visitorId"

// Returns Pair(societyId, visitorId), or null if the scanned content isn't a visitor QR code.
fun parseVisitorQrContent(content: String): Pair<String, String>? {
    if (!content.startsWith(VISITOR_QR_PREFIX)) return null
    val parts = content.removePrefix(VISITOR_QR_PREFIX).split(":")
    if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null
    return parts[0] to parts[1]
}

fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}
