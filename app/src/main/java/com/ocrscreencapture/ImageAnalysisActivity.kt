 package com.ocrscreencapture

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ocrscreencapture.data.HistoryDatabase
import com.ocrscreencapture.data.ImageAnalysisItem
import com.ocrscreencapture.ui.theme.OCRCaptureTheme
import kotlinx.coroutines.launch
import java.net.URLEncoder

class ImageAnalysisActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OCRCaptureTheme { ImageAnalysisScreen { finish() } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageAnalysisScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ai = remember { AiAssistant(ctx) }

    var selectedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AiAssistant.AnalysisResult?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            result = null
            try {
                val stream = ctx.contentResolver.openInputStream(it)
                selectedBitmap = BitmapFactory.decodeStream(stream)
                stream?.close()
            } catch (e: Exception) {
                Toast.makeText(ctx, "خطأ في تحميل الصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تحليل الصور") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, "إعدادات")
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
            // ═══ معاينة الصورة ═══

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 320.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (selectedBitmap != null) {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "الصورة",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Image, null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "اختر صورة للتحليل",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ═══ أزرار ═══

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("من المعرض")
                }

                Button(
                    onClick = {
                        if (selectedBitmap != null && ai.hasAnyKey()) {
                            isAnalyzing = true
                            result = null
                            scope.launch {
                                val r = ai.analyzeImage(selectedBitmap!!)
                                result = r
                                isAnalyzing = false

                                try {
                                    HistoryDatabase.getDatabase(ctx).imageAnalysisDao().insert(
                                        ImageAnalysisItem(
                                            description = r.description,
                                            keywords = r.keywords.joinToString("، "),
                                            detectedText = r.detectedText,
                                            analysis = r.analysis,
                                            websites = r.websites.joinToString("\n") {
                                                "${it.first} | ${it.second}"
                                            },
                                            rawResponse = r.rawResponse
                                        )
                                    )
                                } catch (_: Exception) {}
                            }
                        } else if (selectedBitmap == null) {
                            Toast.makeText(ctx, "اختر صورة أولاً", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, "أضف API Key من الإعدادات", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedBitmap != null && !isAnalyzing && ai.hasAnyKey(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        disabledContainerColor = Color(0xFF333333)
                    )
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(if (isAnalyzing) "جاري..." else "تحليل")
                }
            }

            // تحذير API Key
            if (!ai.hasAnyKey()) {
                Spacer(Modifier.height(8.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFBF360C).copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFEF9A9A))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "يتطلب API Key — اضغط الإعدادات",
                            color = Color(0xFFEF9A9A),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // حالة التحميل
            if (isAnalyzing) {
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF4CAF50))
                        Spacer(Modifier.height(12.dp))
                        Text("جاري تحليل الصورة...", color = Color(0xFF888888))
                    }
                }
            }

            // ═══ نتائج التحليل ═══

            AnimatedVisibility(
                visible = result != null && !isAnalyzing,
                enter = fadeIn() + slideInVertically()
            ) {
                result?.let { r ->
                    Column {
                        Spacer(Modifier.height(16.dp))

                        // إذا فشل التحليل الهيكلي
                        if (r.rawResponse.isNotBlank() && r.description.isBlank()) {
                            ResultCard("نتيجة التحليل", Icons.Default.AutoAwesome, Color(0xFFFFD700), r.rawResponse)
                        } else {
                            // الوصف
                            if (r.description.isNotBlank()) {
                                ResultCard("الوصف", Icons.Default.Description, Color(0xFF4CAF50), r.description)
                                Spacer(Modifier.height(8.dp))
                            }

                            // التصنيف
                            if (r.classification.isNotBlank()) {
                                ResultCard("التصنيف", Icons.Default.Category, Color(0xFF00BCD4), r.classification)
                                Spacer(Modifier.height(8.dp))
                            }

                            // الكلمات المفتاحية
                            if (r.keywords.isNotEmpty()) {
                                Card(
                                    Modifier.fillMaxWidth(),
                                    RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Label, null, tint = Color(0xFFFFD700), Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("الكلمات المفتاحية", fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            r.keywords.take(6).forEach { kw ->
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        kw,
                                                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                        color = Color(0xFF81C784),
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }

                            // النص المكتشف
                            if (r.detectedText.isNotBlank() && r.detectedText != "لا يوجد نص") {
                                ResultCard("النص المكتشف", Icons.Default.TextSnippet, Color(0xFFFF9800), r.detectedText)
                                Spacer(Modifier.height(8.dp))
                            }

                            // التحليل
                            if (r.analysis.isNotBlank()) {
                                ResultCard("معلومات إضافية", Icons.Default.Insights, Color(0xFF9C27B0), r.analysis)
                                Spacer(Modifier.height(8.dp))
                            }

                            // المواقع + بحث
                            Card(
                                Modifier.fillMaxWidth(),
                                RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Language, null, tint = Color(0xFF00BCD4), Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("مواقع وبحث", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "فتح مباشر — أنت تقرر ما تتصفح",
                                        fontSize = 11.sp,
                                        color = Color(0xFF666666)
                                    )
                                    Spacer(Modifier.height(12.dp))

                                    r.websites.forEach { (name, url) ->
                                        WebsiteRow(name, url)
                                    }

                                    Spacer(Modifier.height(12.dp))
                                    HorizontalDivider(color = Color(0xFF333344))
                                    Spacer(Modifier.height(12.dp))

                                    Text("بحث مفتوح", fontWeight = FontWeight.Bold, color = Color(0xFF888888), fontSize = 13.sp)
                                    Spacer(Modifier.height(8.dp))

                                    if (r.description.isNotBlank()) {
                                        SearchRow("بـحـث: ${r.description.take(50)}...", r.description)
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    if (r.keywords.isNotEmpty()) {
                                        SearchRow("بحث: ${r.keywords.take(3).joinToString(" ")}", r.keywords.joinToString(" "))
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    if (r.detectedText.isNotBlank() && r.detectedText != "لا يوجد نص") {
                                        SearchRow("بحث: ${r.detectedText.take(50)}...", r.detectedText)
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    if (r.classification.isNotBlank()) {
                                        SearchRow("بحث: ${r.classification}", r.classification)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ═══ حوار الإعدادات ═══

    if (showSettingsDialog) {
        val tempKeys = remember {
            ai.providers.associate { it.id to mutableStateOf(ai.getKey(it.id)) }
        }
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("إعدادات AI")
                }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "أضف API Key من مزود واحد أو أكثر.\nالمزود الأول يُستخدم أولاً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    ai.providers.forEach { p ->
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(p.nameAr, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(p.freeNote, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = tempKeys[p.id]?.value ?: "",
                                    onValueChange = { tempKeys[p.id]?.value = it },
                                    label = { Text("${p.name} API Key") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF4CAF50),
                                        focusedLabelColor = Color(0xFF4CAF50)
                                    )
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ai.providers.forEach { p ->
                        ai.setKey(p.id, tempKeys[p.id]?.value ?: "")
                    }
                    showSettingsDialog = false
                    Toast.makeText(ctx, "تم الحفظ!", Toast.LENGTH_SHORT).show()
                }) { Text("حفظ الكل") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

// ═══════════════ مكونات مساعدة ═══════════════

@Composable
fun ResultCard(title: String, icon: ImageVector, color: Color, content: String) {
    Card(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Text(content, color = Color(0xFFE0E0E0), fontSize = 14.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
fun WebsiteRow(name: String, url: String) {
    val ctx = LocalContext.current
    Surface(
        onClick = {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                try {
                    ctx.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/search?q=${URLEncoder.encode(name, "UTF-8")}")
                        )
                    )
                } catch (_: Exception) {}
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0D1B2A)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.OpenInNew, null, tint = Color(0xFF00BCD4), Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    color = Color(0xFF00BCD4),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline
                )
                Text(url, color = Color(0xFF555555), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun SearchRow(label: String, query: String) {
    val ctx = LocalContext.current
    Surface(
        onClick = {
            try {
                ctx.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}")
                    )
                )
            } catch (_: Exception) {}
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1B2838)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = Color(0xFF888888), Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color(0xFF888888), fontSize = 13.sp, maxLines = 1)
        }
    }
}
