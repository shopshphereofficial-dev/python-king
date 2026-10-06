package com.pythonking.app

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Encodes a sequence of Bitmaps into an H.264 MP4 file.
 * No screen-capture permission is needed: frames are handed in directly.
 */
class Mp4Encoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val outFile: File,
    bitRate: Int = 8_000_000
) {

    companion object {
        private const val MIME = "video/avc"
    }

    private val codec: MediaCodec = MediaCodec.createEncoderByType(MIME)
    private val info = MediaCodec.BufferInfo()
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var colorFormat = 0
    private var frameIndex = 0

    init {
        val format = MediaFormat.createVideoFormat(MIME, width, height)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        colorFormat = pickColorFormat(codec.codecInfo)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    fun addFrame(bitmap: Bitmap) {
        val scaled = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else {
            bitmap
        }
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== bitmap) scaled.recycle()

        val ptsUs = frameIndex * 1_000_000L / fps
        val inIndex = codec.dequeueInputBuffer(20_000)
        if (inIndex >= 0) {
            val image = if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) {
                codec.getInputImage(inIndex)
            } else {
                null
            }
            if (image != null) {
                fillImage(image, pixels)
            } else {
                val buf = codec.getInputBuffer(inIndex)
                if (buf != null) {
                    buf.clear()
                    if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                        buf.put(argbToNv12(pixels))
                    } else {
                        buf.put(argbToI420(pixels))
                    }
                }
            }
            codec.queueInputBuffer(inIndex, 0, width * height * 3 / 2, ptsUs, 0)
        }
        drain(false)
        frameIndex++
    }

    fun finish(): Boolean {
        val inIndex = codec.dequeueInputBuffer(20_000)
        if (inIndex >= 0) {
            codec.queueInputBuffer(
                inIndex, 0, 0,
                frameIndex * 1_000_000L / fps,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
        }
        drain(true)
        try {
            codec.stop()
        } catch (_: Throwable) {
        }
        try {
            codec.release()
        } catch (_: Throwable) {
        }
        try {
            if (muxerStarted) muxer?.stop()
        } catch (_: Throwable) {
        }
        try {
            muxer?.release()
        } catch (_: Throwable) {
        }
        return muxerStarted
    }

    private fun drain(untilEos: Boolean) {
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(info, if (untilEos) 20_000 else 0)
            if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!untilEos) return
                continue
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    trackIndex = muxer?.addTrack(codec.outputFormat) ?: -1
                    muxer?.start()
                    muxerStarted = true
                }
            } else if (outIndex >= 0) {
                val outBuf = codec.getOutputBuffer(outIndex)
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                if (info.size > 0 && outBuf != null) {
                    if (!muxerStarted) {
                        trackIndex = muxer?.addTrack(codec.outputFormat) ?: -1
                        muxer?.start()
                        muxerStarted = true
                    }
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    muxer?.writeSampleData(trackIndex, outBuf, info)
                }
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                codec.releaseOutputBuffer(outIndex, false)
                if (untilEos && eos) return
            }
        }
    }

    private fun pickColorFormat(codecInfo: MediaCodecInfo): Int {
        return try {
            val supported = codecInfo.getCapabilitiesForType(MIME).colorFormats.toSet()
            val candidates = intArrayOf(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            )
            candidates.firstOrNull { supported.contains(it) }
                ?: MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        } catch (_: Throwable) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        }
    }

    private fun fillImage(image: Image, pixels: IntArray) {
        val w = image.width
        val h = image.height
        val planes = image.planes

        val yPlane = planes[0]
        val yBuf = yPlane.buffer
        val yRow = yPlane.rowStride
        val yPix = yPlane.pixelStride
        for (row in 0 until h) {
            var src = row * w
            var dst = row * yRow
            for (col in 0 until w) {
                yBuf.put(dst, luma(pixels[src]).toByte())
                src++
                dst += yPix
            }
        }

        val uPlane = planes[1]
        val vPlane = planes[2]
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val cw = w / 2
        val ch = h / 2
        for (row in 0 until ch) {
            val y0 = row * 2
            val y1 = y0 + 1
            for (col in 0 until cw) {
                val x0 = col * 2
                val x1 = x0 + 1
                val uv = chroma(pixels, w, y0, y1, x0, x1)
                uBuf.put(row * uPlane.rowStride + col * uPlane.pixelStride, uv[0].toByte())
                vBuf.put(row * vPlane.rowStride + col * vPlane.pixelStride, uv[1].toByte())
            }
        }
    }

    private fun argbToI420(pixels: IntArray): ByteArray {
        val ySize = width * height
        val out = ByteArray(ySize * 3 / 2)
        for (i in 0 until ySize) out[i] = luma(pixels[i]).toByte()
        var uIdx = ySize
        var vIdx = ySize + ySize / 4
        var row = 0
        while (row < height) {
            var col = 0
            while (col < width) {
                val uv = chroma(pixels, width, row, row + 1, col, col + 1)
                out[uIdx++] = uv[0].toByte()
                out[vIdx++] = uv[1].toByte()
                col += 2
            }
            row += 2
        }
        return out
    }

    private fun argbToNv12(pixels: IntArray): ByteArray {
        val ySize = width * height
        val out = ByteArray(ySize * 3 / 2)
        for (i in 0 until ySize) out[i] = luma(pixels[i]).toByte()
        var uvIdx = ySize
        var row = 0
        while (row < height) {
            var col = 0
            while (col < width) {
                val uv = chroma(pixels, width, row, row + 1, col, col + 1)
                out[uvIdx++] = uv[0].toByte()
                out[uvIdx++] = uv[1].toByte()
                col += 2
            }
            row += 2
        }
        return out
    }

    private fun luma(c: Int): Int {
        val r = (c shr 16) and 0xff
        val g = (c shr 8) and 0xff
        val b = c and 0xff
        return (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
    }

    private fun chroma(pixels: IntArray, w: Int, y0: Int, y1: Int, x0: Int, x1: Int): IntArray {
        val c00 = pixels[y0 * w + x0]
        val c01 = pixels[y0 * w + x1]
        val c10 = pixels[y1 * w + x0]
        val c11 = pixels[y1 * w + x1]
        val r = (((c00 shr 16) and 0xff) + ((c01 shr 16) and 0xff) +
                ((c10 shr 16) and 0xff) + ((c11 shr 16) and 0xff)) / 4
        val g = (((c00 shr 8) and 0xff) + ((c01 shr 8) and 0xff) +
                ((c10 shr 8) and 0xff) + ((c11 shr 8) and 0xff)) / 4
        val b = ((c00 and 0xff) + (c01 and 0xff) + (c10 and 0xff) + (c11 and 0xff)) / 4
        val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
        val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
        return intArrayOf(u, v)
    }
}
