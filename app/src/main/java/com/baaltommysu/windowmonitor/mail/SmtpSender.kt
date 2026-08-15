package com.baaltommysu.windowmonitor.mail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Base64
import com.baaltommysu.windowmonitor.storage.PhotoRepository
import com.baaltommysu.windowmonitor.storage.StoredPhoto
import com.baaltommysu.windowmonitor.util.AppLogger
import com.baaltommysu.windowmonitor.util.BeijingTime
import com.baaltommysu.windowmonitor.util.DeviceStatus
import com.baaltommysu.windowmonitor.util.StorageFormatter
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class SmtpSender(private val context: Context) {
    fun sendCameraReport(config: SmtpConfig, photos: List<StoredPhoto>): String {
        require(config.isConfigured) { "SMTP is not configured" }
        require(photos.isNotEmpty()) { "No pending photos to send" }
        val snapshot = DeviceStatus.read(context)
        val attachment = buildCombinedAttachment(photos)
        val subject = "Camera Report (${photos.size} photos)"
        val body = buildString {
            appendLine("Photo Count: ${photos.size}")
            appendLine("Attachment: ${attachment.name}")
            photos.forEachIndexed { index, photo ->
                appendLine("Photo ${index + 1}: ${photo.name}, captured ${BeijingTime.format(Instant.ofEpochMilli(photo.lastModifiedMillis))} (Beijing time)")
            }
            appendLine()
            appendLine("Battery: ${snapshot.batteryPercent}%")
            appendLine("Storage Free: ${StorageFormatter.formatFreeSpace(snapshot.storageFreeBytes, snapshot.storageTotalBytes)}")
        }
        return send(config, subject, body, listOf(attachment))
    }

    private fun send(
        config: SmtpConfig,
        subject: String,
        body: String,
        attachments: List<EmailAttachment>
    ): String {
        AppLogger.d(Tag, "connecting smtp=${config.host}:${config.port} to=${config.to} attachments=${attachments.size}")
        return if (config.port == ImplicitTlsPort) {
            sendOverImplicitTls(config, subject, body, attachments)
        } else {
            sendOverStartTls(config, subject, body, attachments)
        }
    }

    private fun sendOverStartTls(
        config: SmtpConfig,
        subject: String,
        body: String,
        attachments: List<EmailAttachment>
    ): String {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(config.host, config.port), ConnectTimeoutMillis)
            socket.soTimeout = ReadTimeoutMillis
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            expect(reader, 220)
            command(writer, reader, "EHLO android.local", 250)
            command(writer, reader, "STARTTLS", 220)

            val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(socket, config.host, config.port, true) as SSLSocket
            sslSocket.use { secureSocket ->
                secureSocket.soTimeout = ReadTimeoutMillis
                secureSocket.startHandshake()
                return sendAuthenticatedMessage(
                    config = config,
                    subject = subject,
                    body = body,
                    attachments = attachments,
                    reader = BufferedReader(InputStreamReader(secureSocket.getInputStream(), StandardCharsets.UTF_8)),
                    writer = BufferedWriter(OutputStreamWriter(secureSocket.getOutputStream(), StandardCharsets.UTF_8))
                )
            }
        }
    }

    private fun sendOverImplicitTls(
        config: SmtpConfig,
        subject: String,
        body: String,
        attachments: List<EmailAttachment>
    ): String {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(config.host, config.port), ConnectTimeoutMillis)
            socket.soTimeout = ReadTimeoutMillis
            val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(socket, config.host, config.port, true) as SSLSocket
            sslSocket.use { secureSocket ->
                secureSocket.soTimeout = ReadTimeoutMillis
                secureSocket.startHandshake()
                val reader = BufferedReader(InputStreamReader(secureSocket.getInputStream(), StandardCharsets.UTF_8))
                val writer = BufferedWriter(OutputStreamWriter(secureSocket.getOutputStream(), StandardCharsets.UTF_8))
                expect(reader, 220)
                return sendAuthenticatedMessage(config, subject, body, attachments, reader, writer)
            }
        }
    }

    private fun sendAuthenticatedMessage(
        config: SmtpConfig,
        subject: String,
        body: String,
        attachments: List<EmailAttachment>,
        reader: BufferedReader,
        writer: BufferedWriter
    ): String {
        command(writer, reader, "EHLO android.local", 250)
        command(writer, reader, "AUTH LOGIN", 334)
        command(writer, reader, config.username.toBase64(), 334)
        command(writer, reader, config.password.toBase64(), 235)
        command(writer, reader, "MAIL FROM:<${config.from}>", 250)
        command(writer, reader, "RCPT TO:<${config.to}>", 250)
        command(writer, reader, "DATA", 354)
        writer.write(buildMessage(config, subject, body, attachments))
        writer.write("\r\n.\r\n")
        writer.flush()
        val acceptedResponse = expect(reader, 250)
        command(writer, reader, "QUIT", 221)
        AppLogger.d(Tag, "smtp accepted message response=${acceptedResponse.joinToString(" | ")}")
        return acceptedResponse.joinToString(" | ")
    }

    private fun buildMessage(
        config: SmtpConfig,
        subject: String,
        body: String,
        attachments: List<EmailAttachment>
    ): String {
        val boundary = "wm-${UUID.randomUUID()}"
        return buildString {
            appendLine("From: ${config.from}")
            appendLine("To: ${config.to}")
            appendLine("Subject: $subject")
            appendLine("Date: ${BeijingTime.formatRfc1123(Instant.now())}")
            appendLine("Message-ID: <${UUID.randomUUID()}@windowmonitor.local>")
            appendLine("MIME-Version: 1.0")
            appendLine("Content-Type: multipart/mixed; boundary=\"$boundary\"")
            appendLine()
            appendLine("--$boundary")
            appendLine("Content-Type: text/plain; charset=UTF-8")
            appendLine("Content-Transfer-Encoding: 8bit")
            appendLine()
            appendLine(body)
            attachments.forEach { attachment ->
                appendLine("--$boundary")
                appendLine("Content-Type: ${attachment.contentType}; name=\"${attachment.name}\"")
                appendLine("Content-Disposition: attachment; filename=\"${attachment.name}\"")
                appendLine("Content-Transfer-Encoding: base64")
                appendLine()
                AppLogger.d(Tag, "prepared attachment=${attachment.name} bytes=${attachment.bytes.size}")
                appendLine(Base64.encodeToString(attachment.bytes, Base64.NO_WRAP).chunked(76).joinToString("\r\n"))
            }
            appendLine("--$boundary--")
        }.replace("\n", "\r\n")
    }

    private fun buildCombinedAttachment(photos: List<StoredPhoto>): EmailAttachment {
        val batch = composePhotoBatch(photos)
        return try {
            val name = "windowmonitor_${BeijingTime.formatFileName(Instant.now())}_${photos.size}_photos.jpg"
            val bytes = ByteArrayOutputStream().use { output ->
                batch.compress(Bitmap.CompressFormat.JPEG, BatchJpegQuality, output)
                output.toByteArray()
            }
            AppLogger.d(Tag, "prepared combined photo batch photos=${photos.size} bytes=${bytes.size}")
            EmailAttachment(
                name = name,
                contentType = "image/jpeg",
                bytes = bytes,
            )
        } finally {
            batch.recycle()
        }
    }

    private fun composePhotoBatch(photos: List<StoredPhoto>): Bitmap {
        val grid = PhotoBatchLayout.gridFor(photos.size)
        val bitmaps = photos.map(::readScaledPhotoBitmap)
        return try {
            val batch = Bitmap.createBitmap(
                grid.columns * BatchCellWidthPx,
                grid.rows * BatchCellHeightPx,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(batch).apply {
                drawColor(Color.rgb(14, 18, 28))
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            bitmaps.forEachIndexed { index, bitmap ->
                val column = index % grid.columns
                val row = index / grid.columns
                val cellLeft = column * BatchCellWidthPx
                val cellTop = row * BatchCellHeightPx
                val scale = minOf(
                    BatchImageMaxWidthPx.toFloat() / bitmap.width.toFloat(),
                    BatchImageMaxHeightPx.toFloat() / bitmap.height.toFloat(),
                    1f
                )
                val width = bitmap.width * scale
                val height = bitmap.height * scale
                val left = cellLeft + (BatchCellWidthPx - width) / 2f
                val top = cellTop + (BatchCellHeightPx - height) / 2f
                canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), paint)
            }
            batch
        } finally {
            bitmaps.forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    private fun readScaledPhotoBitmap(photo: StoredPhoto): Bitmap {
        val repository = PhotoRepository(context)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = repository.openPhoto(photo)
            ?: throw IllegalStateException("Could not open photo attachment")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Could not read photo dimensions" }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, AttachmentMaxSidePx)
        }
        val bitmap = repository.openPhoto(photo)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            ?: throw IllegalStateException("Could not decode photo attachment")
        return bitmap.scaleToMaxSide(AttachmentMaxSidePx)
    }

    private fun Bitmap.scaleToMaxSide(maxSidePx: Int): Bitmap {
        val longestSide = maxOf(width, height)
        if (longestSide <= maxSidePx) return this
        val scale = maxSidePx.toFloat() / longestSide.toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        recycle()
        return scaled
    }

    private fun calculateSampleSize(width: Int, height: Int, targetSidePx: Int): Int {
        var sampleSize = 1
        var sampledWidth = width
        var sampledHeight = height
        while (sampledWidth / 2 >= targetSidePx && sampledHeight / 2 >= targetSidePx) {
            sampleSize *= 2
            sampledWidth /= 2
            sampledHeight /= 2
        }
        return sampleSize
    }

    private fun command(
        writer: BufferedWriter,
        reader: BufferedReader,
        line: String,
        expectedCode: Int
    ) {
        writer.write("$line\r\n")
        writer.flush()
        expect(reader, expectedCode)
    }

    private fun expect(reader: BufferedReader, expectedCode: Int): List<String> {
        val lines = readResponse(reader)
        val code = lines.firstOrNull()?.take(3)?.toIntOrNull()
        check(code == expectedCode) { "SMTP expected $expectedCode but got ${lines.joinToString(" | ")}" }
        return lines
    }

    private fun readResponse(reader: BufferedReader): List<String> {
        val lines = mutableListOf<String>()
        while (true) {
            val line = reader.readLine() ?: break
            lines += line
            if (line.length < 4 || line[3] != '-') break
        }
        return lines
    }

    private fun String.toBase64(): String {
        return Base64.encodeToString(toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
    }

    companion object {
        private const val ImplicitTlsPort = 465
        private const val ConnectTimeoutMillis = 20_000
        private const val ReadTimeoutMillis = 60_000
        private const val AttachmentMaxSidePx = 1024
        private const val BatchCellWidthPx = 1024
        private const val BatchCellHeightPx = 768
        private const val BatchImageMaxWidthPx = 984
        private const val BatchImageMaxHeightPx = 728
        private const val BatchJpegQuality = 76
        private const val Tag = "SmtpSender"
    }
}

private class EmailAttachment(
    val name: String,
    val contentType: String,
    val bytes: ByteArray,
)
