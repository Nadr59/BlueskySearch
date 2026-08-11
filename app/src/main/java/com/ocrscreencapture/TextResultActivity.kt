package com.ocrscreencapture

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ocrscreencapture.data.HistoryDatabase
import com.ocrscreencapture.data.HistoryItem
import com.ocrscreencapture.ui.theme.OCRCaptureTheme
import kotlinx.coroutines.launch

class TextResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra("extracted_text") ?: ""
        setContent { OCRCaptureTheme { TextResultScreen(text) { finish() } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextResultScreen(text: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var edited by remember { mutableStateOf(text) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("النص المستخرج") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "إغلاق") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            // حقل النص القابل للتعديل
            OutlinedTextField(
                value = edited, onValueChange = { edited = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = { Text("النص المستخرج") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4CAF50),
                    focusedLabelColor = Color(0xFF4CAF50))
            )

            Spacer(Modifier.height(16.dp))

            // أزرار: نسخ + مشاركة
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("OCR", edited))
                        Toast.makeText(ctx, "تم النسخ!", Toast.LENGTH_SHORT).show()
                    },
                    Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("نسخ")
                }
                Button(
                    onClick = {
                        ctx.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, edited)
                            }, "مشاركة"))
                    },
                    Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(4.dp)); Text("مشاركة")
                }
            }

            Spacer(Modifier.height(8.dp))

            // زر الحفظ في السجل
            Button(
                onClick = {
                    scope.launch {
                        try {
                            HistoryDatabase.getDatabase(ctx).historyDao()
                                .insert(HistoryItem(text = edited))
                            Toast.makeText(ctx, "تم الحفظ!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
            ) {
                Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp))
                Text("حفظ في السجل", fontSize = 16.sp)
            }
        }
    }
}
