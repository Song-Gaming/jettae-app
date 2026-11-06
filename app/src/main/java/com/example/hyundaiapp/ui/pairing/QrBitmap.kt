package com.example.hyundaiapp.ui.pairing

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrBitmap {
    /** text를 sizePx 정사각형 QR 이미지로 만든다. 계산이 있으니 메인 스레드 밖에서 부른다 */
    fun encode(text: String, sizePx: Int): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, mapOf(EncodeHintType.MARGIN to 1))
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height) { i -> if (matrix[i % width, i / width]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
