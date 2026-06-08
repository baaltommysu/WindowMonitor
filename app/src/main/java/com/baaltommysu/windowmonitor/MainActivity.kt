package com.baaltommysu.windowmonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.baaltommysu.windowmonitor.mail.MailQueue
import com.baaltommysu.windowmonitor.storage.PhotoRepository
import com.baaltommysu.windowmonitor.ui.theme.WindowMonitorTheme
import com.baaltommysu.windowmonitor.util.PreferenceStore
import com.baaltommysu.windowmonitor.worker.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DisplayTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

class MainActivity : ComponentActivity() {
    private lateinit var store: PreferenceStore

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshUiState()
    }

    private var uiState by mutableStateOf(AppUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PreferenceStore(this)
        migrateMailSettings()
        enableEdgeToEdge()
        refreshUiState()
        setContent {
            WindowMonitorTheme {
                WindowMonitorApp(
                    state = uiState,
                    onRequestPermissions = ::requestRequiredPermissions,
                    onToggleMonitoring = ::setMonitoringEnabled,
                    onCaptureNow = {
                        store.appendLog("手动拍照", "请求开始")
                        WorkScheduler.captureNow(this)
                        refreshUiState()
                    },
                    onSaveCaptureInterval = ::saveCaptureInterval,
                    onToggleMailDelivery = ::setMailDeliveryEnabled,
                    onSaveMailInterval = ::saveMailInterval,
                    onSendEmailNow = ::sendEmailNow,
                    onSaveMailSettings = ::saveMailSettings
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()
        if (uiState.monitoringEnabled && uiState.cameraPermissionGranted) {
            WorkScheduler.enablePeriodicCapture(this)
            WorkScheduler.enableCommandPolling(this)
        }
        if (store.mailDeliveryEnabled && isMailConfigured()) {
            WorkScheduler.enablePeriodicMail(this)
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun setMonitoringEnabled(enabled: Boolean) {
        store.monitoringEnabled = enabled
        if (enabled) {
            WorkScheduler.enablePeriodicCapture(this)
            WorkScheduler.enableCommandPolling(this)
            if (store.mailDeliveryEnabled && isMailConfigured()) {
                WorkScheduler.enablePeriodicMail(this)
            }
        } else {
            WorkScheduler.disablePeriodicCapture(this)
        }
        refreshUiState()
    }

    private fun setMailDeliveryEnabled(enabled: Boolean) {
        store.mailDeliveryEnabled = enabled
        if (enabled && isMailConfigured()) {
            WorkScheduler.enablePeriodicMail(this)
        } else {
            WorkScheduler.disablePeriodicMail(this)
        }
        refreshUiState()
    }

    private fun saveCaptureInterval(minutesText: String) {
        store.captureIntervalMinutes = minutesText.toIntOrNull() ?: 30
        if (store.monitoringEnabled) {
            WorkScheduler.enablePeriodicCapture(this)
        }
        refreshUiState()
    }

    private fun saveMailInterval(minutesText: String) {
        store.mailIntervalMinutes = minutesText.toIntOrNull() ?: 120
        if (store.mailDeliveryEnabled && isMailConfigured()) {
            WorkScheduler.enablePeriodicMail(this)
        }
        refreshUiState()
    }

    private fun sendEmailNow() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                MailQueue(this@MainActivity).flushPending("手动发送邮件")
            }
            refreshUiState()
        }
    }

    private fun refreshUiState() {
        val repository = PhotoRepository(this)
        uiState = AppUiState(
            cameraPermissionGranted = isGranted(Manifest.permission.CAMERA),
            notificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                isGranted(Manifest.permission.POST_NOTIFICATIONS),
            mediaPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                isGranted(Manifest.permission.READ_MEDIA_IMAGES),
            monitoringEnabled = store.monitoringEnabled,
            mailDeliveryEnabled = store.mailDeliveryEnabled,
            captureIntervalMinutes = store.captureIntervalMinutes.toString(),
            mailIntervalMinutes = store.mailIntervalMinutes.toString(),
            lastPhotoTime = formatTimeForDisplay(store.lastPhotoTime),
            lastSendTime = formatTimeForDisplay(store.lastSendTime),
            lastSuccessTime = formatTimeForDisplay(store.lastSuccessTime),
            lastFailureReason = store.lastFailureReason,
            lastFailureTime = formatTimeForDisplay(store.lastFailureTime),
            operationLog = store.operationLog,
            pendingPhotoCount = repository.listPendingPhotos().size,
            mailConfigured = store.smtpHost.isNotBlank() &&
                store.smtpPort > 0 &&
                store.smtpUsername.isNotBlank() &&
                store.smtpPassword.isNotBlank() &&
                store.mailFrom.isNotBlank() &&
                store.mailTo.isNotBlank(),
            mailSettings = MailSettings(
                smtpHost = store.smtpHost,
                smtpPort = store.smtpPort.toString(),
                smtpUsername = store.smtpUsername,
                smtpPassword = store.smtpPassword,
                mailFrom = store.mailFrom,
                mailTo = store.mailTo
            )
        )
    }

    private fun formatTimeForDisplay(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            DisplayTimeFormatter.format(Instant.parse(value).atZone(ZoneId.systemDefault()))
        }.getOrElse { value }
    }

    private fun saveMailSettings(settings: MailSettings) {
        store.smtpHost = normalizeSmtpHost(settings.smtpHost.trim())
        store.smtpPort = normalizeSmtpPort(store.smtpHost, settings.smtpPort.toIntOrNull() ?: 587)
        store.smtpUsername = settings.smtpUsername.trim()
        store.smtpPassword = settings.smtpPassword
        store.mailFrom = settings.mailFrom.trim().ifBlank { settings.smtpUsername.trim() }
        store.mailTo = settings.mailTo.trim()
        if (store.lastFailureReason == "SMTP is not configured" && settings.isConfigured) {
            store.lastFailureReason = ""
        }
        if (store.mailDeliveryEnabled && isMailConfigured()) {
            WorkScheduler.enablePeriodicMail(this)
        }
        refreshUiState()
    }

    private fun migrateMailSettings() {
        val normalizedHost = normalizeSmtpHost(store.smtpHost)
        if (normalizedHost != store.smtpHost) {
            store.smtpHost = normalizedHost
        }
        val normalizedPort = normalizeSmtpPort(store.smtpHost, store.smtpPort)
        if (normalizedPort != store.smtpPort) {
            store.smtpPort = normalizedPort
        }
    }

    private fun normalizeSmtpHost(host: String): String {
        return when {
            host.equals("smtp.sina.com.cn", ignoreCase = true) -> "smtp.sina.com"
            else -> host
        }
    }

    private fun normalizeSmtpPort(host: String, port: Int): Int {
        return if (
            (host.contains("sina.com", ignoreCase = true) || host.equals("smtp.163.com", ignoreCase = true)) &&
            port == 587
        ) {
            465
        } else {
            port
        }
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isMailConfigured(): Boolean {
        return store.smtpHost.isNotBlank() &&
            store.smtpPort > 0 &&
            store.smtpUsername.isNotBlank() &&
            store.smtpPassword.isNotBlank() &&
            store.mailFrom.isNotBlank() &&
            store.mailTo.isNotBlank()
    }
}

data class AppUiState(
    val cameraPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val mediaPermissionGranted: Boolean = false,
    val monitoringEnabled: Boolean = false,
    val mailDeliveryEnabled: Boolean = false,
    val captureIntervalMinutes: String = "30",
    val mailIntervalMinutes: String = "120",
    val lastPhotoTime: String = "",
    val lastSendTime: String = "",
    val lastSuccessTime: String = "",
    val lastFailureReason: String = "",
    val lastFailureTime: String = "",
    val operationLog: String = "",
    val pendingPhotoCount: Int = 0,
    val mailConfigured: Boolean = false,
    val mailSettings: MailSettings = MailSettings()
)

data class MailSettings(
    val smtpHost: String = "smtp.sina.com",
    val smtpPort: String = "465",
    val smtpUsername: String = "",
    val smtpPassword: String = "",
    val mailFrom: String = "",
    val mailTo: String = ""
) {
    val isConfigured: Boolean
        get() = smtpHost.isNotBlank() &&
            smtpPort.toIntOrNull() != null &&
            smtpUsername.isNotBlank() &&
            smtpPassword.isNotBlank() &&
            mailFrom.isNotBlank() &&
            mailTo.isNotBlank()
}

@Composable
fun WindowMonitorApp(
    state: AppUiState,
    onRequestPermissions: () -> Unit,
    onToggleMonitoring: (Boolean) -> Unit,
    onCaptureNow: () -> Unit,
    onSaveCaptureInterval: (String) -> Unit,
    onToggleMailDelivery: (Boolean) -> Unit,
    onSaveMailInterval: (String) -> Unit,
    onSendEmailNow: () -> Unit,
    onSaveMailSettings: (MailSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = Color(0xFF0B1220)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B1220), Color(0xFF101A2E), Color(0xFF0D1B1E))
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
	            Text(
	                text = "Window Monitor",
	                color = Color.White,
	                fontSize = 34.sp,
	                fontWeight = FontWeight.Bold
	            )
	            Text(
	                text = "Version ${BuildConfig.VERSION_NAME}",
	                color = Color(0xFF7DD3FC),
	                style = MaterialTheme.typography.titleMedium,
	                fontWeight = FontWeight.Bold
	            )
	            Text(
	                text = "Periodic camera capture, local cache, mail delivery, boot recovery.",
                color = Color(0xFFB8C7D9),
                fontSize = 17.sp,
                lineHeight = 24.sp
            )

            ControlCard(
                state = state,
                onRequestPermissions = onRequestPermissions,
                onToggleMonitoring = onToggleMonitoring,
                onCaptureNow = onCaptureNow,
                onSaveCaptureInterval = onSaveCaptureInterval
            )

            MailDeliveryCard(
                state = state,
                onToggleMailDelivery = onToggleMailDelivery,
                onSaveMailInterval = onSaveMailInterval,
                onSendEmailNow = onSendEmailNow
            )

            MailSettingsCard(
                initialSettings = state.mailSettings,
                onSave = onSaveMailSettings
            )

            StatusCard(state = state)
            OperationLogCard(logText = state.operationLog)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MailSettingsCard(
    initialSettings: MailSettings,
    onSave: (MailSettings) -> Unit
) {
    var smtpHost by remember(initialSettings) { mutableStateOf(initialSettings.smtpHost) }
    var smtpPort by remember(initialSettings) { mutableStateOf(initialSettings.smtpPort) }
    var smtpUsername by remember(initialSettings) { mutableStateOf(initialSettings.smtpUsername) }
    var smtpPassword by remember(initialSettings) { mutableStateOf(initialSettings.smtpPassword) }
    var mailFrom by remember(initialSettings) { mutableStateOf(initialSettings.mailFrom) }
    var mailTo by remember(initialSettings) { mutableStateOf(initialSettings.mailTo) }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Mail settings",
                color = Color(0xFF0F172A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = smtpHost,
                onValueChange = { smtpHost = it },
                label = { Text("SMTP host") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = smtpPort,
                onValueChange = { smtpPort = it.filter(Char::isDigit) },
                label = { Text("Port (465 SSL, 587 STARTTLS)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = smtpUsername,
                onValueChange = {
                    smtpUsername = it
                    if (mailFrom.isBlank()) mailFrom = it
                },
                label = { Text("SMTP username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = smtpPassword,
                onValueChange = { smtpPassword = it },
                label = { Text("App password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = mailFrom,
                onValueChange = { mailFrom = it },
                label = { Text("From") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = mailTo,
                onValueChange = { mailTo = it },
                label = { Text("To") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    onSave(
                        MailSettings(
                            smtpHost = smtpHost,
                            smtpPort = smtpPort,
                            smtpUsername = smtpUsername,
                            smtpPassword = smtpPassword,
                            mailFrom = mailFrom,
                            mailTo = mailTo
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Save mail settings")
            }
        }
    }
}

@Composable
private fun ControlCard(
    state: AppUiState,
    onRequestPermissions: () -> Unit,
    onToggleMonitoring: (Boolean) -> Unit,
    onCaptureNow: () -> Unit,
    onSaveCaptureInterval: (String) -> Unit
) {
    var captureIntervalMinutes by remember(state.captureIntervalMinutes) {
        mutableStateOf(state.captureIntervalMinutes)
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Periodic capture",
                        color = Color(0xFF0F172A),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
	                    Text(
                        text = "Runs every ${state.captureIntervalMinutes} minutes while the foreground service is active.",
                        color = Color(0xFF475569),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = state.monitoringEnabled,
                    enabled = state.cameraPermissionGranted,
                    onCheckedChange = onToggleMonitoring
                )
            }

            OutlinedTextField(
                value = captureIntervalMinutes,
                onValueChange = { captureIntervalMinutes = it.filter(Char::isDigit) },
                label = { Text("Capture interval minutes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRequestPermissions) {
                    Text(text = "Grant access")
                }
                OutlinedButton(
                    onClick = onCaptureNow,
                    enabled = state.cameraPermissionGranted
                ) {
                    Text(text = "Capture now")
                }
            }

            Button(
                onClick = { onSaveCaptureInterval(captureIntervalMinutes) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Save capture interval")
            }
        }
    }
}

@Composable
private fun MailDeliveryCard(
    state: AppUiState,
    onToggleMailDelivery: (Boolean) -> Unit,
    onSaveMailInterval: (String) -> Unit,
    onSendEmailNow: () -> Unit
) {
    var mailIntervalMinutes by remember(state.mailIntervalMinutes) {
        mutableStateOf(state.mailIntervalMinutes)
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Periodic mail",
                        color = Color(0xFF0F172A),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sends the latest 6 photos every ${state.mailIntervalMinutes} minutes.",
                        color = Color(0xFF475569),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = state.mailDeliveryEnabled,
                    onCheckedChange = onToggleMailDelivery
                )
            }

            OutlinedTextField(
                value = mailIntervalMinutes,
                onValueChange = { mailIntervalMinutes = it.filter(Char::isDigit) },
                label = { Text("Mail interval minutes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { onSaveMailInterval(mailIntervalMinutes) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Save mail interval")
            }

            OutlinedButton(
                onClick = onSendEmailNow,
                enabled = state.mailConfigured && state.pendingPhotoCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Send email")
            }
        }
    }
}

@Composable
private fun StatusCard(state: AppUiState) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Status",
                color = Color(0xFF0F172A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            StatusRow("Camera", if (state.cameraPermissionGranted) "Granted" else "Required")
            StatusRow("Notifications", if (state.notificationPermissionGranted) "Granted" else "Required")
            StatusRow("Photos", if (state.mediaPermissionGranted) "Granted" else "Required")
            StatusRow("Mail", if (state.mailConfigured) "Configured" else "Required")
            StatusRow("Pending photos", state.pendingPhotoCount.toString())
            StatusRow("Last photo", state.lastPhotoTime)
            StatusRow("Last send", state.lastSendTime)
            StatusRow("Last success", state.lastSuccessTime)
            StatusRow("Last failure", state.failureLog)
        }
    }
}

private val AppUiState.failureLog: String
    get() = if (lastFailureReason.isBlank()) {
        ""
    } else {
        "${lastFailureTime.ifBlank { "Unknown time" }} $lastFailureReason"
    }

@Composable
private fun OperationLogCard(logText: String) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Operation log",
                color = Color(0xFF0F172A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = logText,
                color = Color(0xFF0F172A),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = label, color = Color(0xFF64748B))
        Text(
            text = value,
            color = Color(0xFF0F172A),
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WindowMonitorAppPreview() {
    WindowMonitorTheme {
	        WindowMonitorApp(
	            state = AppUiState(cameraPermissionGranted = true, notificationPermissionGranted = true),
	            onRequestPermissions = {},
	            onToggleMonitoring = {},
	            onCaptureNow = {},
	            onSaveCaptureInterval = {},
		            onToggleMailDelivery = {},
		            onSaveMailInterval = {},
		            onSendEmailNow = {},
		            onSaveMailSettings = {}
		        )
    }
}
