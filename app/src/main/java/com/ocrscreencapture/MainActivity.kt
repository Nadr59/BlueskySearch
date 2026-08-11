package com.ocrscreencapture

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ocrscreencapture.ui.theme.OCRCaptureTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OCRCaptureTheme { MainScreen() } }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(false) }
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val projManager = remember {
        context.getSystemService(Activity.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    // --- MediaProjection Launcher ---
    val projLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val svc = Intent(context, FloatingWindowService::class.java).apply {
                putExtra(FloatingWindowService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(FloatingWindowService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(svc) else context.startService(svc)
            isRunning = true
        } else {
            Toast.makeText(context, "تم رفع إذن التقاط الشاشة", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Overlay Permission Launcher ---
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlay = Settings.canDrawOverlays(context)
        if (hasOverlay) projLauncher.launch(projManager.createScreenCaptureIntent())
    }

    // --- Notification Permission (Android 13+) ---
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // ================ الواجهة ================
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.TextSnippet, null, Modifier.size(80.dp), tint = Color(0xFF4CAF50))
            Spacer(Modifier.height(24.dp))
            Text("OCR Screen Capture", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text("استخراج النصوص من أي منطقة على الشاشة",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(48.dp))

            // زر بدء/إيقاف الخدمة
            Button(
                onClick = {
                    if (isRunning) {
                        context.startService(
                            Intent(context, FloatingWindowService::class.java)
                                .apply { action = FloatingWindowService.ACTION_STOP })
                        isRunning = false
                    } else if (!hasOverlay) {
                        overlayLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")))
                    } else {
                        try { projLauncher.launch(projManager.createScreenCaptureIntent()) }
                        catch (e: Exception) {
                            Toast.makeText(context, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(0xFFF44336) else Color(0xFF4CAF50))
            ) {
                Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isRunning) "إيقاف الخدمة" else "بدء الخدمة", fontSize = 18.sp)
            }

            Spacer(Modifier.height(16.dp))

            // زر السجل
            OutlinedButton(
                onClick = { context.startActivity(Intent(context, HistoryActivity::class.java)) },
                Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.History, null); Spacer(Modifier.width(8.dp))
                Text("السجل", fontSize = 18.sp)
            }

            Spacer(Modifier.height(32.dp))

            // تعليمات الاستخدام
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(.5f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("كيفية الاستخدام:", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        """1. اضغط "بدء الخدمة" واسمح بالأذونات
                        |2. اضغط الزر العائم الأخضر على الشاشة
                        |3. حدد المنطقة المراد استخراج النص منها
                        |4. اضغط "استخراج" للحصول على النص""".trimMargin(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
