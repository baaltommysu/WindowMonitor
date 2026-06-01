package com.baaltommysu.windowmonitor.mail

import android.content.Context
import android.util.Base64
import com.baaltommysu.windowmonitor.util.DeviceStatus
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class SmtpSender(private val context: Context) {
    fun sendCameraReport(config: SmtpConfig, photo: File) {
        require(config.isConfigured) { "SMTP is not configured" }
        val snapshot = DeviceStatus.read(context)
        val subject = "Camera Report"
        val body = buildString {
            appendLine("Capture Time: ${Instant.ofEpochMilli(photo.lastModified())}")
            appendLine("Battery: ${snapshot.batteryPercent}%")
            appendLine("Storage Free: ${snapshot.storageFreeBytes} bytes")
        }
        send(config, subject, body, photo)
    }

    fun sendHeartbeat(config: SmtpConfig, body: String) {
        require(config.isConfigured) { "SMTP is not configured" }
        send(config, "Heartbeat Mail", body, attachment = null)
    }

    private fun send(config: SmtpConfig, subject: String, body: String, attachment: File?) {
        Socket(config.host, config.port).use { socket ->
            var reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            var writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))

            expect(reader, 220)
            command(writer, reader, "EHLO android.local", 250)
            command(writer, reader, "STARTTLS", 220)

            val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(socket, config.host, config.port, true) as SSLSocket
            sslSocket.use { secureSocket ->
                secureSocket.startHandshake()
                reader = BufferedReader(InputStreamReader(secureSocket.getInputStream(), StandardCharsets.UTF_8))
                writer = BufferedWriter(OutputStreamWriter(secureSocket.getOutputStream(), StandardCharsets.UTF_8))

                command(writer, reader, "EHLO android.local", 250)
                command(writer, reader, "AUTH LOGIN", 334)
                command(writer, reader, config.username.toBase64(), 334)
                command(writer, reader, config.password.toBase64(), 235)
                command(writer, reader, "MAIL FROM:<${config.from}>", 250)
                command(writer, reader, "RCPT TO:<${config.to}>", 250)
                command(writer, reader, "DATA", 354)
                writer.write(buildMessage(config, subject, body, attachment))
                writer.write("\r\n.\r\n")
                writer.flush()
                expect(reader, 250)
                command(writer, reader, "QUIT", 221)
            }
        }
    }

    private fun buildMessage(
        config: SmtpConfig,
        subject: String,
        body: String,
        attachment: File?
    ): String {
        val boundary = "wm-${UUID.randomUUID()}"
        return buildString {
            appendLine("From: ${config.from}")
            appendLine("To: ${config.to}")
            appendLine("Subject: $subject")
            appendLine("MIME-Version: 1.0")
            appendLine("Content-Type: multipart/mixed; boundary=\"$boundary\"")
            appendLine()
            appendLine("--$boundary")
            appendLine("Content-Type: text/plain; charset=UTF-8")
            appendLine("Content-Transfer-Encoding: 8bit")
            appendLine()
            appendLine(body)
            if (attachment != null) {
                appendLine("--$boundary")
                appendLine("Content-Type: image/jpeg; name=\"${attachment.name}\"")
                appendLine("Content-Disposition: attachment; filename=\"${attachment.name}\"")
                appendLine("Content-Transfer-Encoding: base64")
                appendLine()
                appendLine(Base64.encodeToString(attachment.readBytes(), Base64.NO_WRAP).chunked(76).joinToString("\r\n"))
            }
            appendLine("--$boundary--")
        }.replace("\n", "\r\n")
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

    private fun expect(reader: BufferedReader, expectedCode: Int) {
        val lines = readResponse(reader)
        val code = lines.firstOrNull()?.take(3)?.toIntOrNull()
        check(code == expectedCode) { "SMTP expected $expectedCode but got ${lines.joinToString(" | ")}" }
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
}
