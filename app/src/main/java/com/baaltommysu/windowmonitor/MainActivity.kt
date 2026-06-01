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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.baaltommysu.windowmonitor.storage.PhotoRepository
import com.baaltommysu.windowmonitor.ui.theme.WindowMonitorTheme
import com.baaltommysu.windowmonitor.util.PreferenceStore
import com.baaltommysu.windowmonitor.worker.WorkScheduler

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
        enableEdgeToEdge()
        refreshUiState()
        setContent {
            WindowMonitorTheme {
                WindowMonitorApp(
                    state = uiState,
                    onRequestPermissions = ::requestRequiredPermissions,
                    onToggleMonitoring = ::setMonitoringEnabled,
                    onCaptureNow = {
                        WorkScheduler.captureNow(this)
                        refreshUiState()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()
    }

    private fun requestRequiredPermissions() {
        val permissions = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun setMonitoringEnabled(enabled: Boolean) {
        store.monitoringEnabled = enabled
        if (enabled) {
            WorkScheduler.enablePeriodicCapture(this)
            WorkScheduler.enableHeartbeat(this)
            WorkScheduler.enableCommandPolling(this)
        } else {
            WorkScheduler.disablePeriodicCapture(this)
        }
        refreshUiState()
    }

    private fun refreshUiState() {
        val repository = PhotoRepository(this)
        uiState = AppUiState(
            cameraPermissionGranted = isGranted(Manifest.permission.CAMERA),
            notificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                isGranted(Manifest.permission.POST_NOTIFICATIONS),
            monitoringEnabled = store.monitoringEnabled,
            lastPhotoTime = store.lastPhotoTime,
            lastSendTime = store.lastSendTime,
            lastSuccessTime = store.lastSuccessTime,
            lastFailureReason = store.lastFailureReason,
            pendingPhotoCount = repository.listPendingPhotos().size
        )
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}

data class AppUiState(
    val cameraPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val monitoringEnabled: Boolean = false,
    val lastPhotoTime: String = "",
    val lastSendTime: String = "",
    val lastSuccessTime: String = "",
    val lastFailureReason: String = "",
    val pendingPhotoCount: Int = 0
)

@Composable
fun WindowMonitorApp(
    state: AppUiState,
    onRequestPermissions: () -> Unit,
    onToggleMonitoring: (Boolean) -> Unit,
    onCaptureNow: () -> Unit,
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
                text = "Periodic camera capture, local cache, mail delivery, boot recovery.",
                color = Color(0xFFB8C7D9),
                fontSize = 17.sp,
                lineHeight = 24.sp
            )

            ControlCard(
                state = state,
                onRequestPermissions = onRequestPermissions,
                onToggleMonitoring = onToggleMonitoring,
                onCaptureNow = onCaptureNow
            )

            StatusCard(state = state)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ControlCard(
    state: AppUiState,
    onRequestPermissions: () -> Unit,
    onToggleMonitoring: (Boolean) -> Unit,
    onCaptureNow: () -> Unit
) {
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
                        text = "Runs roughly every 30 minutes using WorkManager.",
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
            StatusRow("Pending photos", state.pendingPhotoCount.toString())
            StatusRow("Last photo", state.lastPhotoTime.ifBlank { "-" })
            StatusRow("Last send", state.lastSendTime.ifBlank { "-" })
            StatusRow("Last success", state.lastSuccessTime.ifBlank { "-" })
            StatusRow("Last failure", state.lastFailureReason.ifBlank { "-" })
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFF64748B))
        Text(
            text = value,
            color = Color(0xFF0F172A),
            fontWeight = FontWeight.Medium
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
            onCaptureNow = {}
        )
    }
}
