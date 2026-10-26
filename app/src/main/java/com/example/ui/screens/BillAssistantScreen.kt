package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BillCategory
import com.example.ui.viewmodel.ChatConversation
import com.example.ui.viewmodel.ChatImportResult
import com.example.data.model.BillRecord
import com.example.data.remote.ChatChartBar
import com.example.data.remote.ChatStatHighlight
import com.example.domain.MonthlyAnalyticsSummary
import com.example.ui.viewmodel.BillChatMessage
import com.example.util.DateAndFormatUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BillAssistantScreen(
  messages: List<BillChatMessage>,
  isTyping: Boolean,
  categories: List<BillCategory>,
  allBills: List<BillRecord>,
  analyticsSummary: MonthlyAnalyticsSummary,
  onSendMessage: (String) -> Unit,
  onRetryLastMessage: () -> Unit,
  onTemporaryChat: () -> Unit = {},
  onOpenSettings: () -> Unit = {},
  conversations: List<ChatConversation> = emptyList(),
  activeConversation: ChatConversation? = null,
  onSelectConversation: (String) -> Unit = {},
  onCreateConversation: () -> Unit = {},
  onRenameConversation: (String, String) -> Unit = { _, _ -> },
  onTogglePin: (String) -> Unit = {},
  onDeleteConversation: (String) -> Unit = {},
  onExportAllConversations: () -> String = { "" },
  onImportConversations: (String) -> ChatImportResult = { ChatImportResult(0, 0) },
  onSetConversationModel: (String, String?) -> Unit = { _, _ -> },
  onSetConversationThinkingLevel: (String, String?) -> Unit = { _, _ -> },
) {
  var inputText by rememberSaveable { mutableStateOf("") }
  val listState = rememberLazyListState()
  val clipboardManager = LocalClipboardManager.current
  val context = LocalContext.current
  val copiedToastText = stringResource(R.string.assistant_copied_toast)
  var showHistory by remember { mutableStateOf(false) }
  var showModelSettings by remember { mutableStateOf(false) }
  var pendingExportJson by remember { mutableStateOf<String?>(null) }
  val exportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
      val json = pendingExportJson
      pendingExportJson = null
      if (uri != null && json != null) {
        runCatching {
          context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(json.toByteArray(Charsets.UTF_8))
          }
        }
      }
    }
  val importLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      val json = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
          stream.readBytes().toString(Charsets.UTF_8)
        }
      }.getOrNull()
      if (json.isNullOrBlank()) {
        Toast.makeText(context, "لم يتم استيراد أي محادثة", Toast.LENGTH_SHORT).show()
        return@rememberLauncherForActivityResult
      }
      val result = onImportConversations(json)
      val message =
        if (result.imported == 0 && result.skipped == 0) {
          "لم يتم استيراد أي محادثة — تأكد من أن الملف نسخة احتياطية صحيحة"
        } else {
          "تم استيراد ${result.imported} محادثة، وتخطي ${result.skipped} مكررة"
        }
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

  LaunchedEffect(messages.size, isTyping) {
    val totalItems = messages.size + if (isTyping) 1 else 0
    if (totalItems > 0) {
      listState.animateScrollToItem(totalItems - 1)
    }
  }

  val quickActions =
    listOf(
      Triple(
        Icons.Default.BarChart,
        stringResource(R.string.assistant_quick_stats),
        "اعمل لي إحصائية شاملة ومفصلة لفواتيري المسجلة حسب الأشهر والأقسام مع رسم بياني",
      ),
      Triple(
        Icons.Default.WarningAmber,
        stringResource(R.string.assistant_quick_spikes),
        "حلل فواتيري وأخبرني بأسباب أي ارتفاع أو تفاوت في القيمة المالية أو الاستهلاك مقارنة بالفواتير السابقة",
      ),
      Triple(
        Icons.Default.TipsAndUpdates,
        stringResource(R.string.assistant_quick_saving),
        "بناءً على بيانات فواتيري، قدم لي خطة عملية ونصائح مخصصة لترشيد الاستهلاك وخفض التكاليف الشهرية",
      ),
      Triple(
        Icons.AutoMirrored.Filled.HelpOutline,
        stringResource(R.string.assistant_quick_help),
        "اشرح لي كيف أستفيد من جميع مزايا تطبيق فواتيري ومسح الفاتورة واستخراج جدول الـ Sheet وتصدير CSV",
      ),
    )

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier =
                Modifier.size(38.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = activeConversation?.title ?: stringResource(R.string.assistant_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text =
                  if (allBills.isNotEmpty() || categories.isNotEmpty()) {
                    stringResource(
                      R.string.assistant_live_context_badge,
                      allBills.size,
                      categories.size,
                    )
                  } else {
                    stringResource(R.string.assistant_empty_context_badge)
                  },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        },
        actions = {
          IconButton(
            onClick = { showHistory = true },
            modifier = Modifier.testTag("chat_history_button"),
          ) {
            Icon(
              imageVector = Icons.Default.History,
              contentDescription = "سجل المحادثات",
            )
          }
          IconButton(
            onClick = { showModelSettings = true },
            modifier = Modifier.testTag("chat_model_settings_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Tune,
              contentDescription = "إعدادات النموذج والتفكير",
            )
          }
          IconButton(
            onClick = onTemporaryChat,
            modifier = Modifier.testTag("temporary_chat_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = "دردشة مؤقتة جديدة",
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          ),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .imePadding(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Quick Prompt Toolbar
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
      ) {
        items(quickActions) { (icon, label, fullPrompt) ->
          AssistChip(
            onClick = { if (!isTyping) onSendMessage(fullPrompt) },
            enabled = !isTyping,
            leadingIcon = {
              Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
            label = { Text(label) },
          )
        }
      }

      // Conversation Messages List
      LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
          Modifier.weight(1f)
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .testTag("assistant_messages_list"),
      ) {
        itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
          val isLatestAssistant = !msg.isUser && index == messages.lastIndex && !isTyping
          if (msg.isUser) {
            UserChatBubble(
              text = msg.text,
              timestampMillis = msg.timestampMillis,
              onCopyClick = {
                clipboardManager.setText(AnnotatedString(msg.text))
                Toast.makeText(context, copiedToastText, Toast.LENGTH_SHORT).show()
              },
            )
          } else {
            AssistantChatCard(
              message = msg,
              primaryCurrency = analyticsSummary.primaryCurrency,
              showSuggestions = isLatestAssistant,
              onSuggestionClick = { suggestion ->
                if (!isTyping) onSendMessage(suggestion)
              },
              onRetryClick = {
                if (!isTyping) onRetryLastMessage()
              },
              onCopyClick = {
                clipboardManager.setText(AnnotatedString(msg.text))
                Toast.makeText(context, copiedToastText, Toast.LENGTH_SHORT).show()
              },
              onOpenSettings = onOpenSettings,
              timestampMillis = msg.timestampMillis,
            )
          }
        }

        if (isTyping) {
          item(key = "typing_indicator") {
            TypingIndicatorCard()
          }
        }
      }

      // Composer Bar
      Surface(
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.widthIn(max = 760.dp)
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
          OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = {
              Text(
                text = stringResource(R.string.assistant_input_placeholder),
                style = MaterialTheme.typography.bodyMedium,
              )
            },
            maxLines = 4,
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions =
              KeyboardActions(
                onSend = {
                  val prompt = inputText.trim()
                  if (prompt.isNotEmpty() && !isTyping) {
                    inputText = ""
                    onSendMessage(prompt)
                  }
                }
              ),
            modifier = Modifier.weight(1f).testTag("chat_input_field"),
          )

          Spacer(modifier = Modifier.width(10.dp))

          val canSend = inputText.isNotBlank() && !isTyping
          FilledIconButton(
            onClick = {
              val prompt = inputText.trim()
              if (prompt.isNotEmpty() && !isTyping) {
                inputText = ""
                onSendMessage(prompt)
              }
            },
            enabled = canSend,
            colors =
              IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            modifier = Modifier.size(50.dp).testTag("chat_send_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = stringResource(R.string.assistant_send_desc),
            )
          }
        }
      }
    }
  }

  if (showModelSettings) {
    val currentConversation = activeConversation
    ChatModelSettingsDialog(
      currentModelId = currentConversation?.modelId,
      currentThinkingLevel = currentConversation?.thinkingLevel,
      onModelSelected = { modelId ->
        currentConversation?.let { onSetConversationModel(it.id, modelId) }
      },
      onThinkingLevelSelected = { level ->
        currentConversation?.let { onSetConversationThinkingLevel(it.id, level) }
      },
      onDismiss = { showModelSettings = false },
    )
  }

  if (showHistory) {
    ChatHistoryDialog(
      conversations = conversations,
      activeConversationId = activeConversation?.id,
      onDismiss = { showHistory = false },
      onSelect = { id ->
        onSelectConversation(id)
        showHistory = false
      },
      onCreate = {
        onCreateConversation()
        showHistory = false
      },
      onRename = onRenameConversation,
      onTogglePin = onTogglePin,
      onDelete = onDeleteConversation,
      onExportAll = {
        pendingExportJson = onExportAllConversations()
        exportLauncher.launch("homebill-chats-backup.json")
      },
      onImport = { importLauncher.launch("application/json") },
    )
  }
}


private fun formatRelativeTime(timestampMillis: Long): String {
  val diffMinutes = ((System.currentTimeMillis() - timestampMillis) / 60_000L).coerceAtLeast(0L)
  return when {
    diffMinutes < 1L -> "الآن"
    diffMinutes == 1L -> "منذ دقيقة"
    diffMinutes == 2L -> "منذ دقيقتين"
    diffMinutes < 11L -> "منذ $diffMinutes دقائق"
    diffMinutes < 60L -> "منذ $diffMinutes دقيقة"
    diffMinutes < 120L -> "منذ ساعة"
    diffMinutes < 1440L -> "منذ ${diffMinutes / 60L} ساعة"
    else -> "منذ ${diffMinutes / 1440L} يوم"
  }
}

@Composable
private fun ChatModelSettingsDialog(
  currentModelId: String?,
  currentThinkingLevel: String?,
  onModelSelected: (String?) -> Unit,
  onThinkingLevelSelected: (String?) -> Unit,
  onDismiss: () -> Unit,
) {
  val modelOptions =
    listOf(
      Triple(null, "تلقائي", "يختار التطبيق أفضل نموذج متاح تلقائيًا"),
      Triple("gemini-3.8-flash", "Flash 3.8", "الأحدث — متوازن وسريع للاستخدام اليومي"),
      Triple("gemini-3.5-flash", "Flash 3.5", "سريع وموثوق"),
      Triple("gemini-flash-latest", "Flash الأحدث", "يتبع آخر إصدار Flash تلقائيًا"),
      Triple("gemini-3.1-flash-lite-preview", "Flash-Lite 3.1", "الأسرع والأخف على الحصة"),
      Triple("gemini-3.1-pro-preview", "Pro 3.1", "أعمق تحليلًا، لكنه أبطأ وحدوده أقل"),
    )
  val thinkingOptions =
    listOf(
      Triple(null, "تلقائي", "إعداد النموذج الافتراضي"),
      Triple("LOW", "منخفض", "سرعة وفعالية"),
      Triple("MEDIUM", "متوسط", "عمق معتدل"),
      Triple("HIGH", "مرتفع", "دقة عالية، أبطأ وأكثر استهلاكًا"),
    )

  Dialog(onDismissRequest = onDismiss) {
    Card(
      shape = MaterialTheme.shapes.extraLarge,
      modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        item {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "النموذج ومستوى التفكير",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
              Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق")
            }
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "ينطبق الاختيار على هذه المحادثة فقط، وعلى الرسائل القادمة، وليس على المسح.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "النموذج",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
        }
        items(modelOptions) { (modelId, label, description) ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onModelSelected(modelId) },
          ) {
            RadioButton(
              selected = currentModelId == modelId,
              onClick = { onModelSelected(modelId) },
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(text = label, style = MaterialTheme.typography.bodyLarge)
              Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        item {
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "مستوى التفكير",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
        }
        items(thinkingOptions) { (level, label, description) ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onThinkingLevelSelected(level) },
          ) {
            RadioButton(
              selected = currentThinkingLevel == level,
              onClick = { onThinkingLevelSelected(level) },
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(text = label, style = MaterialTheme.typography.bodyLarge)
              Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        item {
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "مستوى التفكير يغيّر عدد رموز التفكير، فيؤثر على سرعة الرد واستهلاك الحصة، لا على سعر الرمز نفسه. إذا رفض نموذجٌ مستوى معينًا، سيظهر خطأ — أعد الضبط إلى تلقائي.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "الموديلات المقفلة في تطبيق Gemini (مثل 4 Argon) هي باقات اشتراك داخل التطبيق، وليست نماذج متاحة عبر مفتاح API، لذلك لا تظهر هنا.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(text = "تم")
          }
        }
      }
    }
  }
}


@Composable
private fun ChatHistoryDialog(
  conversations: List<ChatConversation>,
  activeConversationId: String?,
  onDismiss: () -> Unit,
  onSelect: (String) -> Unit,
  onCreate: () -> Unit,
  onRename: (String, String) -> Unit,
  onTogglePin: (String) -> Unit,
  onDelete: (String) -> Unit,
  onExportAll: () -> Unit,
  onImport: () -> Unit,
) {
  var renameTarget by remember { mutableStateOf<ChatConversation?>(null) }
  var renameText by remember { mutableStateOf("") }
  var deleteTarget by remember { mutableStateOf<ChatConversation?>(null) }

  Dialog(onDismissRequest = onDismiss) {
    Card(
      shape = MaterialTheme.shapes.extraLarge,
      modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
    ) {
      Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = stringResource(R.string.chat_history_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق")
          }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = onCreate, modifier = Modifier.weight(1f)) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(R.string.chat_history_new))
          }
          OutlinedButton(onClick = onExportAll) {
            Text(text = stringResource(R.string.chat_history_export))
          }
          OutlinedButton(onClick = onImport) {
            Text(text = stringResource(R.string.chat_history_import))
          }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (conversations.isEmpty()) {
          Text(
            text = stringResource(R.string.chat_history_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(conversations, key = { it.id }) { conversation ->
              val isActive = conversation.id == activeConversationId
              val preview =
                conversation.messages.lastOrNull { it.isUser }?.text?.take(60)
                  ?: stringResource(R.string.chat_history_new)
              Card(
                colors =
                  if (isActive) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                  } else {
                    CardDefaults.cardColors()
                  },
                onClick = { onSelect(conversation.id) },
                modifier = Modifier.fillMaxWidth().testTag("chat_history_item_${conversation.id}"),
              ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.pinned) {
                      Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "مثبتة",
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                      text = conversation.title,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.weight(1f),
                    )
                    Text(
                      text = formatRelativeTime(conversation.updatedAtMillis),
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { onTogglePin(conversation.id) }) {
                      Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = if (conversation.pinned) "إلغاء التثبيت" else "تثبيت",
                        tint =
                          if (conversation.pinned) {
                            MaterialTheme.colorScheme.primary
                          } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                          },
                        modifier = Modifier.size(20.dp),
                      )
                    }
                    IconButton(
                      onClick = {
                        renameTarget = conversation
                        renameText = conversation.title
                      },
                    ) {
                      Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "إعادة تسمية",
                        modifier = Modifier.size(20.dp),
                      )
                    }
                    IconButton(onClick = { deleteTarget = conversation }) {
                      Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف",
                        modifier = Modifier.size(20.dp),
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  val renameConversation = renameTarget
  if (renameConversation != null) {
    AlertDialog(
      onDismissRequest = { renameTarget = null },
      title = { Text(text = stringResource(R.string.chat_history_rename)) },
      text = {
        OutlinedTextField(
          value = renameText,
          onValueChange = { renameText = it },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            onRename(renameConversation.id, renameText)
            renameTarget = null
          },
        ) {
          Text(text = stringResource(R.string.action_save))
        }
      },
      dismissButton = {
        TextButton(onClick = { renameTarget = null }) {
          Text(text = stringResource(R.string.action_cancel))
        }
      },
    )
  }

  val deleteConversation = deleteTarget
  if (deleteConversation != null) {
    AlertDialog(
      onDismissRequest = { deleteTarget = null },
      title = { Text(text = stringResource(R.string.chat_history_delete_title)) },
      text = { Text(text = stringResource(R.string.chat_history_delete_body)) },
      confirmButton = {
        TextButton(
          onClick = {
            onDelete(deleteConversation.id)
            deleteTarget = null
          },
        ) {
          Text(text = stringResource(R.string.action_delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { deleteTarget = null }) {
          Text(text = stringResource(R.string.action_cancel))
        }
      },
    )
  }
}


@Composable
private fun UserChatBubble(
  text: String,
  timestampMillis: Long,
  onCopyClick: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.Start,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Start,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape =
          RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 6.dp,
            bottomStart = 20.dp,
            bottomEnd = 20.dp,
          ),
        modifier = Modifier.widthIn(max = 520.dp),
      ) {
        Text(
          text = text,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
      }
      IconButton(onClick = onCopyClick, modifier = Modifier.size(36.dp)) {
        Icon(
          imageVector = Icons.Default.ContentCopy,
          contentDescription = "نسخ الرسالة",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(17.dp),
        )
      }
    }
    Text(
      text = formatRelativeTime(timestampMillis),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantChatCard(
  message: BillChatMessage,
  primaryCurrency: String,
  showSuggestions: Boolean,
  onSuggestionClick: (String) -> Unit,
  onRetryClick: () -> Unit,
  onCopyClick: () -> Unit,
  onOpenSettings: () -> Unit = {},
  timestampMillis: Long,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.End,
  ) {
    Card(
      colors =
        CardDefaults.cardColors(
          containerColor =
            if (message.isError) {
              MaterialTheme.colorScheme.errorContainer
            } else {
              MaterialTheme.colorScheme.surface
            }
        ),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
      shape =
        RoundedCornerShape(
          topStart = 6.dp,
          topEnd = 22.dp,
          bottomStart = 22.dp,
          bottomEnd = 22.dp,
        ),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              tint =
                if (message.isError) {
                  MaterialTheme.colorScheme.error
                } else {
                  MaterialTheme.colorScheme.primary
                },
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.assistant_title),
              style = MaterialTheme.typography.labelLarge,
              color =
                if (message.isError) {
                  MaterialTheme.colorScheme.onErrorContainer
                } else {
                  MaterialTheme.colorScheme.primary
                },
            )
          }

          if (!message.isError) {
            IconButton(
              onClick = onCopyClick,
              modifier = Modifier.size(36.dp),
            ) {
              Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.assistant_copy_desc),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = message.text,
          style = MaterialTheme.typography.bodyLarge,
          color =
            if (message.isError) {
              MaterialTheme.colorScheme.onErrorContainer
            } else {
              MaterialTheme.colorScheme.onSurface
            },
        )

        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = formatRelativeTime(timestampMillis),
          style = MaterialTheme.typography.labelSmall,
          color =
            if (message.isError) {
              MaterialTheme.colorScheme.onErrorContainer
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        if (message.isError) {
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedButton(
            onClick = onRetryClick,
            colors =
              ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onErrorContainer
              ),
            modifier = Modifier.height(48.dp).testTag("retry_chat_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.assistant_retry_button))
          }
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedButton(
            onClick = onOpenSettings,
            colors =
              ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onErrorContainer
              ),
            modifier = Modifier.height(48.dp).testTag("open_settings_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("الذهاب إلى الإعدادات")
          }
        }

        if (message.highlights.isNotEmpty()) {
          Spacer(modifier = Modifier.height(14.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            message.highlights.forEach { stat ->
              StatHighlightChipCard(stat = stat)
            }
          }
        }

        if (message.chartBars.isNotEmpty()) {
          Spacer(modifier = Modifier.height(14.dp))
          HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
          Spacer(modifier = Modifier.height(12.dp))
          ChatInlineBarChartCard(
            title =
              message.chartTitle.ifBlank { stringResource(R.string.analytics_screen_title) },
            bars = message.chartBars,
            fallbackUnit = primaryCurrency,
          )
        }
      }
    }

    if (showSuggestions && message.followUpSuggestions.isNotEmpty()) {
      Spacer(modifier = Modifier.height(8.dp))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        message.followUpSuggestions.forEach { suggestion ->
          SuggestionChip(
            onClick = { onSuggestionClick(suggestion) },
            label = {
              Text(
                text = suggestion,
                style = MaterialTheme.typography.labelMedium,
              )
            },
          )
        }
      }
    }
  }
}

@Composable
private fun StatHighlightChipCard(stat: ChatStatHighlight) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
      Text(
        text = stat.label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = stat.value,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (stat.trend.isNotBlank()) {
          Text(
            text = stat.trend,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    }
  }
}

@Composable
private fun ChatInlineBarChartCard(
  title: String,
  bars: List<ChatChartBar>,
  fallbackUnit: String,
) {
  val maxVal = (bars.maxOfOrNull { it.value } ?: 1.0).coerceAtLeast(0.01)

  Column(
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Default.BarChart,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
    }

    bars.forEach { bar ->
      val ratio = (bar.value / maxVal).toFloat().coerceIn(0.04f, 1f)
      val displayUnit = bar.unit.ifBlank { fallbackUnit }
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = bar.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "${DateAndFormatUtils.formatNumber(bar.value)} $displayUnit".trim(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(10.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth(ratio)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
          )
        }
      }
    }
  }
}

@Composable
private fun TypingIndicatorCard() {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.large,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
      CircularProgressIndicator(
        modifier = Modifier.size(20.dp),
        strokeWidth = 2.5.dp,
        color = MaterialTheme.colorScheme.primary,
      )
      Spacer(modifier = Modifier.width(12.dp))
      Text(
        text = stringResource(R.string.assistant_typing),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
