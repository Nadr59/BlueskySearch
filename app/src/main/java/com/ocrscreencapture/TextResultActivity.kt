package com.ocrscreencapture

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
        setContent {
            OCRCaptureTheme {
                TextResultScreen(text) { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextResultScreen(text: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var edited by remember { mutableStateOf(text) }
    val scope = rememberCoroutineScope()
    val ai = remember { AiAssistant(ctx) }

    // ═══════════════ حالة المساعد الذكي ═══════════════
    var aiResponse by remember { mutableStateOf("") }
    var aiLoading by remember { mutableStateOf(false) }
    var aiAction by remember { mutableStateOf("") }
    var showAiSection by remember { mutableStateOf(false) }
    var showGeminiKeyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("النص المستخرج") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "إغلاق")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ═══════════════ حقل النص ═══════════════

            OutlinedTextField(
                value = edited,
                onValueChange = { edited = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 300.dp),
                label = { Text("النص المستخرج") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4CAF50),
                    focusedLabelColor = Color(0xFF4CAF50)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ═══════════════ أزرار العمليات الأساسية ═══════════════

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // نسخ
                Button(
                    onClick = {
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("OCR", edited))
                        Toast.makeText(ctx, "تم النسخ!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("نسخ")
                }

                // مشاركة
                Button(
                    onClick = {
                        ctx.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, edited)
                                },
                                "مشاركة"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("مشاركة")
                }

                // حفظ
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
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("حفظ")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ═══════════════ قسم المساعد الذكي ═══════════════

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // العنوان
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "مساعد ذكي",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // حالة API Key
                    if (!ai.hasApiKey()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFBF360C).copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "يتطلب Gemini API Key (مجاني)",
                                    color = Color(0xFFEF9A9A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "احصل من: aistudio.google.com/app/apikey",
                                    color = Color(0xFF999999),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // أزرار AI
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // شرح
                        AiButton(
                            text = "شرح",
                            icon = Icons.Default.Lightbulb,
                            color = Color(0xFFFFD700),
                            enabled = !aiLoading && ai.hasApiKey(),
                            modifier = Modifier.weight(1f)
                        ) {
                            aiAction = "شرح"
                            aiResponse = ""
                            showAiSection = true
                            scope.launch {
                                aiLoading = true
                                aiResponse = ai.explain(edited)
                                aiLoading = false
                            }
                        }

                        // ترجمة
                        AiButton(
                            text = "ترجمة",
                            icon = Icons.Default.Translate,
                            color = Color(0xFF00BCD4),
                            enabled = !aiLoading && ai.hasApiKey(),
                            modifier = Modifier.weight(1f)
                        ) {
                            aiAction = "ترجمة"
                            aiResponse = ""
                            showAiSection = true
                            scope.launch {
                                aiLoading = true
                                aiResponse = ai.translate(edited)
                                aiLoading = false
                            }
                        }

                        // توسع
                        AiButton(
                            text = "توسع",
                            icon = Icons.Default.Expand,
                            color = Color(0xFF9C27B0),
                            enabled = !aiLoading && ai.hasApiKey(),
                            modifier = Modifier.weight(1f)
                        ) {
                            aiAction = "توسع"
                            aiResponse = ""
                            showAiSection = true
                            scope.launch {
                                aiLoading = true
                                aiResponse = ai.expand(edited)
                                aiLoading = false
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // زر إعداد API Key
                    TextButton(
                        onClick = { showGeminiKeyDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Key, null, Modifier.size(14.dp), tint = Color(0xFF888888))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (ai.hasApiKey()) "تغيير Gemini API Key" else "إضافة Gemini API Key",
                            color = Color(0xFF888888),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ═══════════════ قسم نتيجة AI ═══════════════

            AnimatedVisibility(
                visible = showAiSection,
                enter = fadeIn() + slideInVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF16213E)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // عنوان النتيجة
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    aiAction,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50),
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.width(8.dp))
                                if (aiResponse.isNotBlank() && !aiLoading) {
                                    IconButton(
                                        onClick = {
                                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE)
                                                    as ClipboardManager)
                                                .setPrimaryClip(
                                                    ClipData.newPlainText("AI", aiResponse)
                                                )
                                            Toast.makeText(ctx, "تم النسخ!", Toast.LENGTH_SHORT)
                                                .show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            null,
                                            Modifier.size(16.dp),
                                            tint = Color(0xFF888888)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            ctx.startActivity(
                                                Intent.createChooser(
                                                    Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, aiResponse)
                                                    },
                                                    "مشاركة"
                                                )
                                            )
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            null,
                                            Modifier.size(16.dp),
                                            tint = Color(0xFF888888)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            if (aiLoading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color(0xFF4CAF50),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "جاري التفكير...",
                                        color = Color(0xFF888888),
                                        fontSize = 14.sp
                                    )
                                }
                            } else if (aiResponse.isNotBlank()) {
                                Text(
                                    aiResponse,
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ═══════════════ حوار Gemini API Key ═══════════════

    if (showGeminiKeyDialog) {
        var tempKey by remember { mutableStateOf(ai.getApiKey()) }
        AlertDialog(
            onDismissRequest = { showGeminiKeyDialog = false },
            title = { Text("Gemini API Key") },
            text = {
                Column {
                    Text(
                        "للاستخدام المجاني:\n\n" +
                        "1. اذهب إلى:\n" +
                        "   aistudio.google.com/app/apikey\n\n" +
                        "2. سجل الدخول بحساب Google\n\n" +
                        "3. اضغط Create API Key\n\n" +
                        "4. انسخ المفتاح والصقه هنا\n\n" +
                        "مجاني: 15 طلب/دقيقة",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        label = { Text("Gemini API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ai.setApiKey(tempKey)
                    showGeminiKeyDialog = false
                    Toast.makeText(ctx, "تم الحفظ!", Toast.LENGTH_SHORT).show()
                }) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { showGeminiKeyDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

// ═══════════════ زر AI مخصص ═══════════════

@Composable
fun AiButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.2f),
            contentColor = color,
            disabledContainerColor = Color(0xFF333333),
            disabledContentColor = Color(0xFF666666)
        )
    ) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 13.sp)
    }
}
