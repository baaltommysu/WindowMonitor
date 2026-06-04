package com.baaltommysu.windowmonitor.mail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.baaltommysu.windowmonitor.storage.PhotoRepository
import com.baaltommysu.windowmonitor.storage.StoredPhoto
import com.baaltommysu.windowmonitor.util.DeviceStatus
import com.baaltommysu.windowmonitor.util.AppLogger
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class SmtpSender(private val context: Context) {
    fun sendCameraReport(config: SmtpConfig, photos: List<StoredPhoto>): String {
        require(config.isConfigured) { "SMTP is not configured" }
        require(photos.isNotEmpty()) { "No pending photos to send" }
        val snapshot = DeviceStatus.read(context)
        val subject = "Camera Report (${photos.size} photos)"
        val body = buildString {
            appendLine("Photo Count: ${photos.size}")
            photos.forEachIndexed { index, photo ->
                appendLine("Photo ${index + 1}: ${photo.name}, ${Instant.ofEpochMilli(photo.lastModifiedMillis)}")
            }
            appendLine()
            appendLine("Battery: ${snapshot.batteryPercent}%")
            appendLine("Storage Free: ${snapshot.storageFreeBytes} bytes")
        }
        return send(config, subject, body, photos)
    }

    fun sendHeartbeat(config: SmtpConfig, body: String): String {
        require(config.isConfigured) { "SMTP is not configured" }
        return send(config, "Heartbeat Mail", body, attachments = emptyList())
    }

    private fun send(config: SmtpConfig, subject: String, body: String, attachments: List<StoredPhoto>): String {
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
        attachments: List<StoredPhoto>
    ): String {
        Socket(config.host, config.port).use { socket ->
            socket.soTimeout = SocketTimeoutMillis
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            expect(reader, 220)
            command(writer, reader, "EHLO android.local", 250)
            command(writer, reader, "STARTTLS", 220)

            val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(socket, config.host, config.port, true) as SSLSocket
            sslSocket.use { secureSocket ->
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
        attachments: List<StoredPhoto>
    ): String {
        val socket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(config.host, config.port) as SSLSocket
        socket.use { secureSocket ->
            secureSocket.soTimeout = SocketTimeoutMillis
            secureSocket.startHandshake()
            val reader = BufferedReader(InputStreamReader(secureSocket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(secureSocket.getOutputStream(), StandardCharsets.UTF_8))
            expect(reader, 220)
            return sendAuthenticatedMessage(config, subject, body, attachments, reader, writer)
        }
    }

    private fun sendAuthenticatedMessage(
        config: SmtpConfig,
        subject: String,
        body: String,
        attachments: List<StoredPhoto>,
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
        attachments: List<StoredPhoto>
    ): String {
        val boundary = "wm-${UUID.randomUUID()}"
        return buildString {
            appendLine("From: ${config.from}")
            appendLine("To: ${config.to}")
            appendLine("Subject: $subject")
            appendLine("Date: ${DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.now().atOffset(ZoneOffset.UTC))}")
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
                appendLine("Content-Type: image/jpeg; name=\"${attachment.name}\"")
                appendLine("Content-Disposition: attachment; filename=\"${attachment.name}\"")
                appendLine("Content-Transfer-Encoding: base64")
                appendLine()
                val bytes = readCompressedAttachment(attachment)
                AppLogger.d(
                    Tag,
                    "prepared attachment=${attachment.name} original=${attachment.sizeBytes} compressed=${bytes.size}"
                )
                appendLine(Base64.encodeToString(bytes, Base64.NO_WRAP).chunked(76).joinToString("\r\n"))
            }
            appendLine("--$boundary--")
        }.replace("\n", "\r\n")
    }

    private fun readCompressedAttachment(photo: StoredPhoto): ByteArray {
        val repository = PhotoRepository(context)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = repository.openPhoto(photo)
            ?: throw IllegalStateException("Could not open photo attachment")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Could not read photo dimensions" }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = repository.openPhoto(photo)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            ?: throw IllegalStateException("Could not decode photo attachment")
        return bitmap.useCompressedJpeg()
    }

    private fun Bitmap.useCompressedJpeg(): ByteArray {
        val scaled = scaleToEmailSize()
        return try {
            ByteArrayOutputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, AttachmentJpegQuality, output)
                output.toByteArray()
            }
        } finally {
            if (scaled !== this) scaled.recycle()
            recycle()
        }
    }

    private fun Bitmap.scaleToEmailSize(): Bitmap {
        val longestSide = maxOf(width, height)
        if (longestSide <= AttachmentMaxSidePx) return this
        val scale = AttachmentMaxSidePx.toFloat() / longestSide.toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        var sampledWidth = width
        var sampledHeight = height
        while (sampledWidth / 2 >= AttachmentMaxSidePx && sampledHeight / 2 >= AttachmentMaxSidePx) {
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
        private const val SocketTimeoutMillis = 120_000
        private const val AttachmentMaxSidePx = 1280
        private const val AttachmentJpegQuality = 72
        private const val Tag = "SmtpSender"
    }
}
