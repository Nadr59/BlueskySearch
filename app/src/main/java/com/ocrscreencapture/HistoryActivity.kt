package com.ocrscreencapture

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ocrscreencapture.data.HistoryDatabase
import com.ocrscreencapture.data.HistoryItem
import com.ocrscreencapture.ui.theme.OCRCaptureTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OCRCaptureTheme { HistoryScreen { finish() } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { HistoryDatabase.getDatabase(ctx) }
    val items by db.historyDao().getAllHistory().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("السجل") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع") } },
                actions = {
                    if (items.isNotEmpty()) IconButton(onClick = { scope.launch { db.historyDao().deleteAll() } }) {
                        Icon(Icons.Default.DeleteSweep, "مسح الكل")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(.4f))
                    Spacer(Modifier.height(16.dp))
                    Text("لا يوجد سجل بعد", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.4f))
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    HistoryCard(item,
                        onCopy = {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("OCR", item.text))
                            Toast.makeText(ctx, "تم النسخ!", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = { scope.launch { db.historyDao().delete(item) } }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryCard(item: HistoryItem, onCopy: () -> Unit, onDelete: () -> Unit) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val date = remember(item.timestamp) { fmt.format(Date(item.timestamp)) }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text(date, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.6f))
            Spacer(Modifier.height(4.dp))
            Text(item.text, style = MaterialTheme.typography.bodyMedium,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onCopy, Modifier.size(36.dp)) {
                    Icon(Icons.Default.ContentCopy, "نسخ", Modifier.size(18.dp))
                }
                IconButton(onDelete, Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "حذف", Modifier.size(18.dp), tint = Color(0xFFF44336))
                }
            }
        }
    }
}
