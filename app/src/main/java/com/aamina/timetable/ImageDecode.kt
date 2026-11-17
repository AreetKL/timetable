package com.aamina.timetable

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File

/**
 * 全格式图片解码。
 *
 * BitmapFactory 解不了 GIF（动图）、部分 HEIC/AVIF 文件，直接返回 null——
 * 这正是"图片解码失败"的根源。系统级 ImageDecoder（API 28+）能解平台支持的
 * 一切格式（GIF 取首帧、HEIC、AVIF、WebP），并自动处理 EXIF 旋转，
 * 失败时再退回 BitmapFactory 采样解码。
 */
object Img {

    /** 解码并压到最长边 ≤ [longest]；全部失败返回 null。 */
    fun decode(context: Context, uri: Uri, longest: Int = 2304): Bitmap? {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                val source = if (uri.scheme == "file") {
                    ImageDecoder.createSource(File(uri.path!!))
                } else {
                    ImageDecoder.createSource(context.contentResolver, uri)
                }
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val size = maxOf(info.size.width, info.size.height)
                    if (size > longest) {
                        val ratio = longest.toFloat() / size
                        decoder.setTargetSize(
                            (info.size.width * ratio).toInt().coerceAtLeast(1),
                            (info.size.height * ratio).toInt().coerceAtLeast(1)
                        )
                    }
                }
            } catch (e: Exception) {
                // 落到 BitmapFactory 兜底
            }
        }
        // 兜底：BitmapFactory 采样解码
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0) return null
            var sample = 1
            val size = maxOf(bounds.outWidth, bounds.outHeight)
            while (size / (sample * 2) >= longest / 2) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 读文件头的魔数，判断真实格式（扩展名不可信）。 */
    fun magicOf(data: ByteArray): String = when {
        data.size >= 4 && data[0] == 0x89.toByte() && data[1] == 'P'.code.toByte() &&
            data[2] == 'N'.code.toByte() && data[3] == 'G'.code.toByte() -> "PNG"
        data.size >= 3 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() &&
            data[2] == 0xFF.toByte() -> "JPEG"
        data.size >= 12 && data[0] == 'R'.code.toByte() && data[8] == 'W'.code.toByte() &&
            data[9] == 'E'.code.toByte() && data[10] == 'B'.code.toByte() &&
            data[11] == 'P'.code.toByte() -> "WebP"
        data.size >= 6 && data[0] == 'G'.code.toByte() && data[1] == 'I'.code.toByte() &&
            data[2] == 'F'.code.toByte() -> "GIF"
        data.size >= 12 && String(data.sliceArray(4..11), Charsets.US_ASCII)
            .startsWith("ftyp") -> "HEIF/AVIF"
        else -> "未知格式"
    }
}
