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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ocrscreencapture.data.HistoryDatabase
import com.ocrscreencapture.data.ImageAnalysisItem
import com.ocrscreencapture.ui.theme.OCRCaptureTheme
import kotlinx.coroutines.launch
import java.net.URLEncoder

// ═══════════════ Activity ═══════════════

class ImageAnalysisActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        } catch (_: Exception) { null }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            } catch (_: Exception) { null }
        }
    }
}

// ═══════════════ الشاشة الرئيسية ═══════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImageAnalysisScreen(
    initialBitmap: Bitmap? = null,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ai = remember { AiAssistant(ctx) }
    val allMethods = remember { AnalysisMethod.getAllMethods() }

    var selectedBitmap by remember { mutableStateOf(initialBitmap) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResults by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isAdvancedMode by remember { mutableStateOf(false) }
    var selectedQuickMethod by remember { mutableStateOf<AnalysisMethod?>(null) }
    val selectedMethods = remember { mutableStateListOf<AnalysisMethod>() }

    // تحليل تلقائي عند استقبال صورة مشاركة
    LaunchedEffect(initialBitmap) {
        if (initialBitmap != null && ai.hasAnyKey()) {
            val defaultMethod = allMethods.first { it.id == "general" }
            selectedQuickMethod = defaultMethod
            isAnalyzing = true
            val results = ai.analyzeWithMethods(initialBitmap, listOf(defaultMethod))
            analysisResults = results
            isAnalyzing = false
            saveToHistory(ctx, results, listOf(defaultMethod))
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            analysisResults = emptyMap()
            try {
                ctx.contentResolver.openInputStream(it)?.use { stream ->
                    selectedBitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
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
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "إعدادات")
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
                    .heightIn(min = 150.dp, max = 280.dp),
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
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "اختر صورة أو شاركها من لقطة الشاشة",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // زر اختيار من المعرض
            Button(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("اختر صورة من المعرض")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ═══ اختيار نوع التحليل ═══

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // العنوان + مفتاح الوضع المتقدم
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "نوع التحليل",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Text("متقدم", fontSize = 12.sp, color = Color(0xFF888888))
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = isAdvancedMode,
                            onCheckedChange = {
                                isAdvancedMode = it
                                if (!it) selectedMethods.clear()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF4CAF50),
                                checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ═══ الوضع السريع ═══
                    if (!isAdvancedMode) {
                        Text(
                            "اختر نوع واحد:",
                            fontSize = 12.sp,
                            color = Color(0xFF888888)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allMethods.forEach { method ->
                                val isSelected = selectedQuickMethod?.id == method.id
                                val icon = getMethodIcon(method.id)
                                val color = getMethodColor(method.id)

                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedQuickMethod = if (isSelected) null else method
                                    },
                                    label = {
                                        Text(method.nameAr, fontSize = 12.sp)
                                    },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = color.copy(alpha = 0.3f),
                                        selectedLabelColor = color,
                                        selectedLeadingIconColor = color,
                                        containerColor = Color(0xFF0D1B2A),
                                        labelColor = Color(0xFFAAAAAA),
                                        iconColor = Color(0xFF666666)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) color.copy(alpha = 0.5f) else Color(0xFF333344)
                                    )
                                )
                            }
                        }
                    }

                    // ═══ الوضع المتقدم ═══
                    if (isAdvancedMode) {
                        Text(
                            "اختر أكثر من منهج:",
                            fontSize = 12.sp,
                            color = Color(0xFF888888)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        allMethods.forEach { method ->
                            val isChecked = selectedMethods.any { it.id == method.id }
                            val icon = getMethodIcon(method.id)
                            val color = getMethodColor(method.id)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isChecked) {
                                            selectedMethods.removeAll { it.id == method.id }
                                        } else {
                                            selectedMethods.add(method)
                                        }
                                    }
                                    .background(
                                        if (isChecked) color.copy(alpha = 0.1f)
                                        else Color.Transparent
                                    )
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedMethods.add(method)
                                        else selectedMethods.removeAll { it.id == method.id }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = color,
                                        uncheckedColor = Color(0xFF555555)
                                    )
                                )
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isChecked) color else Color(0xFF666666)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        method.nameAr,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isChecked) Color.White else Color(0xFFAAAAAA)
                                    )
                                    Text(
                                        method.descriptionAr,
                                        fontSize = 11.sp,
                                        color = if (isChecked) color.copy(alpha = 0.7f) else Color(0xFF555555),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // تحذير API Key
            if (!ai.hasAnyKey()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFBF360C).copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF9A9A))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("يتطلب API Key — اضغط الإعدادات", color = Color(0xFFEF9A9A), fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ═══ زر التحليل ═══

            val methodsToAnalyze = if (isAdvancedMode) selectedMethods.toList()
            else listOfNotNull(selectedQuickMethod)

            Button(
                onClick = {
                    if (selectedBitmap != null && methodsToAnalyze.isNotEmpty() && ai.hasAnyKey()) {
                        isAnalyzing = true
                        analysisResults = emptyMap()
                        scope.launch {
                            val results = ai.analyzeWithMethods(selectedBitmap!!, methodsToAnalyze)
                            analysisResults = results
                            isAnalyzing = false
                            saveToHistory(ctx, results, methodsToAnalyze)
                        }
                    } else if (selectedBitmap == null) {
                        Toast.makeText(ctx, "اختر صورة أولاً", Toast.LENGTH_SHORT).show()
                    } else if (methodsToAnalyze.isEmpty()) {
                        Toast.makeText(ctx, "اختر نوع التحليل", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "أضف API Key من الإعدادات", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = selectedBitmap != null && methodsToAnalyze.isNotEmpty() && !isAnalyzing && ai.hasAnyKey(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    disabledContainerColor = Color(0xFF333333)
                )
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جاري التحليل...", fontSize = 16.sp)
                } else {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "تحليل" + if (methodsToAnalyze.size > 1) " (${methodsToAnalyze.size} مناهج)" else "",
                        fontSize = 16.sp
                    )
                }
            }

            // حالة التحميل
            if (isAnalyzing) {
                Spacer(modifier = Modifier.height(20.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("جاري تحليل الصورة...", color = Color(0xFF888888))
                        if (methodsToAnalyze.size > 1) {
                            Text(
                                "${methodsToAnalyze.size} مناهج — قد يستغرق وقتاً أطول",
                                color = Color(0xFF666666),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // ═══ نتائج التحليل ═══

            AnimatedVisibility(
                visible = analysisResults.isNotEmpty() && !isAnalyzing,
                enter = fadeIn() + slideInVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "النتائج",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    analysisResults.forEach { (methodId, content) ->
                        val method = allMethods.find { it.id == methodId }
                        val icon = getMethodIcon(methodId)
                        val color = getMethodColor(methodId)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = color
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        method?.nameAr ?: methodId,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = color
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    content,
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }

                    // بحث Google مفتوح
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
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
                                Text("بحث مفتوح", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            // بحث بالكلمات المفتاحية من النتائج
                            val firstResult = analysisResults.values.firstOrNull() ?: ""
                            val searchTerms = extractSearchTerms(firstResult)

                            searchTerms.forEach { term ->
                                SearchRow(label = "بحث: $term", query = term)
                                Spacer(modifier = Modifier.height(4.dp))
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
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إعدادات AI")
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "أضف API Key من مزود واحد أو أكثر.\nالمزود الأول يُستخدم أولاً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ai.providers.forEach { p ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(p.nameAr, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(p.freeNote, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
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
                    ai.providers.forEach { p -> ai.setKey(p.id, tempKeys[p.id]?.value ?: "") }
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

// ═══════════════ مساعدات الأيقونات والألوان ═══════════════

fun getMethodIcon(methodId: String): ImageVector {
    return when (methodId) {
        "general" -> Icons.Default.AutoAwesome
        "technical" -> Icons.Default.Memory
        "artistic" -> Icons.Default.Palette
        "photography" -> Icons.Default.CameraAlt
        "philosophical" -> Icons.Default.Lightbulb
        "historical" -> Icons.Default.AccountBalance
        "psychological" -> Icons.Default.Favorite
        "media" -> Icons.Default.Campaign
        "uiux" -> Icons.Default.PhoneAndroid
        "product" -> Icons.Default.ShoppingCart
        "forensic" -> Icons.Default.FindInPage
        "ocr" -> Icons.Default.TextSnippet
        "websearch" -> Icons.Default.Language
        else -> Icons.Default.AutoAwesome
    }
}

fun getMethodColor(methodId: String): Color {
    return when (methodId) {
        "general" -> Color(0xFF4CAF50)
        "technical" -> Color(0xFFFF9800)
        "artistic" -> Color(0xFFE91E63)
        "photography" -> Color(0xFF2196F3)
        "philosophical" -> Color(0xFF9C27B0)
        "historical" -> Color(0xFF795548)
        "psychological" -> Color(0xFFF44336)
        "media" -> Color(0xFF00BCD4)
        "uiux" -> Color(0xFF3F51B5)
        "product" -> Color(0xFFFF5722)
        "forensic" -> Color(0xFF607D8B)
        "ocr" -> Color(0xFF8BC34A)
        "websearch" -> Color(0xFF009688)
        else -> Color(0xFF4CAF50)
    }
}

fun extractSearchTerms(text: String): List<String> {
    val terms = mutableListOf<String>()
    val lines = text.split("\n").filter { it.isNotBlank() }
    // استخراج أول 4 سطور مفيدة كمصطلحات بحث
    for (line in lines.take(8)) {
        val clean = line.trim()
            .removePrefix("-").removePrefix("•").removePrefix("*")
            .removePrefix("1.").removePrefix("2.").removePrefix("3.")
            .removePrefix("4.").removePrefix("5.")
            .trim()
        if (clean.length in 5..80 && !clean.startsWith("═") && !clean.startsWith("[")) {
            terms.add(clean)
        }
        if (terms.size >= 4) break
    }
    return terms
}

suspend fun saveToHistory(
    ctx: android.content.Context,
    results: Map<String, String>,
    methods: List<AnalysisMethod>
) {
    try {
        val combinedText = results.entries.joinToString("\n\n") { (id, content) ->
            val method = methods.find { it.id == id }
            "═══ ${method?.nameAr ?: id} ═══\n$content"
        }
        val keywords = methods.joinToString("، ") { it.nameAr }
        HistoryDatabase.getDatabase(ctx).imageAnalysisDao().insert(
            ImageAnalysisItem(
                description = "تحليل: $keywords",
                keywords = keywords,
                detectedText = "",
                analysis = combinedText.take(5000),
                websites = "",
                rawResponse = combinedText
            )
        )
    } catch (_: Exception) {}
}

// ═══════════════ مكونات مساعدة ═══════════════

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
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF888888))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = Color(0xFF888888), fontSize = 13.sp, maxLines = 1)
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
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${URLEncoder.encode(name, "UTF-8")}")))
                } catch (_: Exception) {}
            }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0D1B2A)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF00BCD4))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = Color(0xFF00BCD4), fontSize = 14.sp, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)
                Text(url, color = Color(0xFF555555), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
