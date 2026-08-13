package com.ocrscreencapture

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class ImageAnalysisActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SHARED_IMAGE_URI = "shared_image_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ فهم الصورة المشتركة من قوائم المشاركة
        val sharedBitmap = extractSharedImage(intent)

        setContent {
            OCRCaptureTheme {
                ImageAnalysisScreen(
                    initialBitmap = sharedBitmap,
                    onBack = { finish() }
                )
            }
        }
    }

    // ✅ استقبال الصورة من مشاركة النظام
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun extractSharedImage(intent: Intent?): Bitmap? {
        intent ?: return null

        return try {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    val uri: Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    uri?.let { loadBitmapFromUri(it) }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            try {
                // محاولة بديلة
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            } catch (e2: Exception) {
                null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageAnalysisScreen(
    initialBitmap: Bitmap? = null,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ai = remember { AiAssistant(ctx) }

    var selectedBitmap by remember { mutableStateOf(initialBitmap) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AiAssistant.AnalysisResult?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // ✅ تحليل تلقائي عند استقبال صورة مشاركة
    LaunchedEffect(initialBitmap) {
        if (initialBitmap != null && ai.hasAnyKey()) {
            isAnalyzing = true
            val r = ai.analyzeImage(initialBitmap)
            result = r
            isAnalyzing = false

            // حفظ في السجل
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
    }

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
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "إعدادات"
                        )
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
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
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "اختر صورة للتحليل",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ═══ أزرار ═══

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
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
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isAnalyzing) "جاري..." else "تحليل")
                }
            }

            // تحذير API Key
            if (!ai.hasAnyKey()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFBF360C).copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFEF9A9A)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "يتطلب API Key — اضغط الإعدادات",
                            color = Color(0xFFEF9A9A),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // حالة التحميل
            if (isAnalyzing) {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.height(12.dp))
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
                        Spacer(modifier = Modifier.height(16.dp))

                        if (r.rawResponse.isNotBlank() && r.description.isBlank()) {
                            ResultCard(
                                title = "نتيجة التحليل",
                                icon = Icons.Default.AutoAwesome,
                                color = Color(0xFFFFD700),
                                content = r.rawResponse
                            )
                        } else {
                            if (r.description.isNotBlank()) {
                                ResultCard(
                                    title = "الوصف",
                                    icon = Icons.Default.Description,
                                    color = Color(0xFF4CAF50),
                                    content = r.description
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (r.classification.isNotBlank()) {
                                ResultCard(
                                    title = "التصنيف",
                                    icon = Icons.Default.Category,
                                    color = Color(0xFF00BCD4),
                                    content = r.classification
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (r.keywords.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF1A1A2E)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Label,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = Color(0xFFFFD700)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "الكلمات المفتاحية",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            r.keywords.take(6).forEach { kw ->
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = kw,
                                                        modifier = Modifier.padding(
                                                            horizontal = 10.dp,
                                                            vertical = 4.dp
                                                        ),
                                                        color = Color(0xFF81C784),
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (r.detectedText.isNotBlank() && r.detectedText != "لا يوجد نص") {
                                ResultCard(
                                    title = "النص المكتشف",
                                    icon = Icons.Default.TextSnippet,
                                    color = Color(0xFFFF9800),
                                    content = r.detectedText
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (r.analysis.isNotBlank()) {
                                ResultCard(
                                    title = "معلومات إضافية",
                                    icon = Icons.Default.Insights,
                                    color = Color(0xFF9C27B0),
                                    content = r.analysis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // ═══ المواقع + بحث ═══

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF16213E)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = Color(0xFF00BCD4)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "مواقع وبحث",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "فتح مباشر — أنت تقرر ما تتصفح",
                                        fontSize = 11.sp,
                                        color = Color(0xFF666666)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    r.websites.forEach { (name, url) ->
                                        WebsiteRow(name = name, url = url)
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = Color(0xFF333344))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = "بحث مفتوح",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF888888),
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (r.description.isNotBlank()) {
                                        SearchRow(
                                            label = "بحث: ${r.description.take(50)}...",
                                            query = r.description
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    if (r.keywords.isNotEmpty()) {
                                        SearchRow(
                                            label = "بحث: ${r.keywords.take(3).joinToString(" ")}",
                                            query = r.keywords.joinToString(" ")
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    if (r.detectedText.isNotBlank() && r.detectedText != "لا يوجد نص") {
                                        SearchRow(
                                            label = "بحث: ${r.detectedText.take(50)}...",
                                            query = r.detectedText
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    if (r.classification.isNotBlank()) {
                                        SearchRow(
                                            label = "بحث: ${r.classification}",
                                            query = r.classification
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إعدادات AI")
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "أضف API Key من مزود واحد أو أكثر.\nالمزود الأول يُستخدم أولاً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ai.providers.forEach { p ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = p.nameAr,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = p.freeNote,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = tempKeys[p.id]?.value ?: "",
                                    onValueChange = { tempKeys[p.id]?.value = it },
                                    label = { Text(text = "${p.name} API Key") },
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
                }) {
                    Text("حفظ الكل")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

// ═══════════════ مكونات مساعدة ═══════════════

@Composable
fun ResultCard(
    title: String,
    icon: ImageVector,
    color: Color,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = color
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                color = Color(0xFFE0E0E0),
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
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
                            Uri.parse(
                                "https://www.google.com/search?q=${
                                    URLEncoder.encode(name, "UTF-8")
                                }"
                            )
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
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF00BCD4)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = Color(0xFF00BCD4),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline
                )
                Text(
                    text = url,
                    color = Color(0xFF555555),
                    fontSize = 11.sp,
                    maxLines = 1
                )
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
                        Uri.parse(
                            "https://www.google.com/search?q=${
                                URLEncoder.encode(query, "UTF-8")
                            }"
                        )
                    )
                )
            } catch (_: Exception) {}
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1B2838)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color(0xFF888888)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = Color(0xFF888888),
                fontSize = 13.sp,
                maxLines = 1
            )
        }
    }
}
