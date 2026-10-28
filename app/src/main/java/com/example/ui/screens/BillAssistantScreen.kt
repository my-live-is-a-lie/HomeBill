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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalClipboardManager
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.BillCategory
import com.example.ui.viewmodel.ChatConversation
import com.example.ui.viewmodel.ChatImportResult
import com.example.data.model.BillRecord
import com.example.data.remote.ChatChartBar
import com.example.data.remote.ChatStatHighlight
import com.example.domain.MonthlyAnalyticsSummary
import com.example.ui.viewmodel.BillChatMessage
import com.example.util.AssistantImageAttachment
import com.example.util.DateAndFormatUtils
import java.io.File

/** Quick questions offered when a bill image is attached; tapping one copies it to the input box. */
private val imageQuickQuestions =
  listOf(
    "ما القيمة الإجمالية لهذه الفاتورة وما تاريخها؟",
    "لخّص لي محتوى هذه الفاتورة وبنودها الرئيسية",
    "ما قراءة العداد السابقة والحالية وكمية الاستهلاك؟",
    "هل توجد رسوم أو غرامات أو ضرائب في هذه الفاتورة؟ اشرحها",
    "هل تبدو قيمة هذه الفاتورة مرتفعة مقارنة بفواتيري السابقة؟",
    "اشرح لي بنود هذه الفاتورة بلغة بسيطة",
    "هل في الفاتورة أي شيء غير واضح أو يحتاج إلى تدقيق؟",
  )

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
  onSetConversationModel: (String, String?) -> Unit = { _, _ -> },
  onSetConversationThinkingLevel: (String, String?) -> Unit = { _, _ -> },
  isTemporaryChat: Boolean = false,
  imageAttachment: AssistantImageAttachment? = null,
  onRemoveImageAttachment: () -> Unit = {},
) {
  var inputValue by
    rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
  val inputText = inputValue.text
  val inputFocusRequester = remember { FocusRequester() }
  val listState = rememberLazyListState()
  val clipboardManager = LocalClipboardManager.current
  val context = LocalContext.current
  val copiedToastText = stringResource(R.string.assistant_copied_toast)
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  var showModelMenu by remember { mutableStateOf(false) }
  var thinkingExpanded by remember { mutableStateOf(false) }
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

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet {
        AssistantHistoryDrawer(
          conversations = conversations,
          activeConversationId = activeConversation?.id,
          isTemporaryChat = isTemporaryChat,
          onSelect = { id ->
            onSelectConversation(id)
            scope.launch { drawerState.close() }
          },
          onCreate = {
            onCreateConversation()
            scope.launch { drawerState.close() }
          },
          onTemporary = {
            onTemporaryChat()
            scope.launch { drawerState.close() }
          },
          onRename = onRenameConversation,
          onTogglePin = onTogglePin,
          onDelete = onDeleteConversation,
          onClose = { scope.launch { drawerState.close() } },
        )
      }
    },
  ) {
    Scaffold(
      topBar = {
        TopAppBar(
          navigationIcon = {
            IconButton(
              onClick = { scope.launch { drawerState.open() } },
              modifier = Modifier.testTag("chat_drawer_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "القائمة وسجل المحادثات",
              )
            }
          },
          title = {
          Box {
            Column {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showModelMenu = true },
              ) {
                Text(
                  text = modelLabel(activeConversation?.modelId),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Icon(
                  imageVector = Icons.Default.KeyboardArrowDown,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Text(
                text =
                  if (isTemporaryChat) {
                    "دردشة مؤقتة · لا تُحفظ"
                  } else if (allBills.isNotEmpty() || categories.isNotEmpty()) {
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
            GeminiModelMenu(
              expanded = showModelMenu,
              currentModelId = activeConversation?.modelId,
              currentThinkingLevel = activeConversation?.thinkingLevel,
              onDismiss = { showModelMenu = false },
              onModelSelected = { modelId ->
                // Gemini's picker stays open after a model is picked, so the
                // thinking level below remains visible in the same menu.
                activeConversation?.let { onSetConversationModel(it.id, modelId) }
              },
              onThinkingLevelSelected = { level ->
                activeConversation?.let { onSetConversationThinkingLevel(it.id, level) }
              },
              thinkingExpanded = thinkingExpanded,
              onToggleThinking = { thinkingExpanded = !thinkingExpanded },
            )
          }
        },
        actions = {
          IconButton(
            onClick = onTemporaryChat,
            modifier = Modifier.testTag("temporary_chat_button"),
          ) {
            TempChatIcon()
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

      // Bill image sent from a bill: preview + quick questions that fill the input box.
      if (imageAttachment != null) {
        ImageAttachmentBar(
          attachment = imageAttachment,
          questions = imageQuickQuestions,
          onRemove = onRemoveImageAttachment,
          onQuestionClick = { question ->
            inputValue = TextFieldValue(text = question, selection = TextRange(question.length))
            inputFocusRequester.requestFocus()
          },
        )
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
            value = inputValue,
            onValueChange = { inputValue = it },
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
                    inputValue = TextFieldValue("")
                    onSendMessage(prompt)
                  }
                }
              ),
            modifier =
              Modifier.weight(1f)
                .focusRequester(inputFocusRequester)
                .testTag("chat_input_field"),
          )

          Spacer(modifier = Modifier.width(10.dp))

          val canSend = inputText.isNotBlank() && !isTyping
          FilledIconButton(
            onClick = {
              val prompt = inputText.trim()
              if (prompt.isNotEmpty() && !isTyping) {
                inputValue = TextFieldValue("")
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

  }
}

/**
 * Shown above the composer while a bill image is attached: a small preview with a remove button
 * and quick questions. Tapping a question copies it into the input box so the user can edit it
 * or send it as is. The image goes to Gemini with every message until it is removed.
 */
@Composable
private fun ImageAttachmentBar(
  attachment: AssistantImageAttachment,
  questions: List<String>,
  onRemove: () -> Unit,
  onQuestionClick: (String) -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().testTag("assistant_image_attachment_bar"),
  ) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
          model = File(attachment.filePath),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.assistant_image_attached_title),
            style = MaterialTheme.typography.labelLarge,
          )
          Text(
            text = attachment.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(
          onClick = onRemove,
          modifier = Modifier.testTag("remove_image_attachment_button"),
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.assistant_image_remove_desc),
          )
        }
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = stringResource(R.string.assistant_image_questions_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(questions) { question ->
          SuggestionChip(
            onClick = { onQuestionClick(question) },
            label = { Text(text = question, style = MaterialTheme.typography.labelMedium) },
          )
        }
      }
    }
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

/**
 * Gemini's temporary-chat mark: a pencil inside a dashed circle, as in the
 * Gemini app's top bar. The top-bar button now opens a temporary chat.
 */
@Composable
private fun TempChatIcon() {
  val circleColor = MaterialTheme.colorScheme.onSurface
  Box(
    modifier =
      Modifier.size(24.dp).drawBehind {
        drawCircle(
          color = circleColor,
          style =
            Stroke(
              width = 1.5.dp.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f),
            ),
        )
      },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Default.Edit,
      contentDescription = "دردشة مؤقتة",
      modifier = Modifier.size(15.dp),
    )
  }
}

private fun modelLabel(modelId: String?): String =
  when (modelId) {
    "gemini-3.8-flash" -> "Flash 3.8"
    "gemini-3.5-flash" -> "Flash 3.5"
    "gemini-flash-latest" -> "Flash الأحدث"
    "gemini-3.1-flash-lite-preview" -> "Flash-Lite 3.1"
    "gemini-3.1-pro-preview" -> "Pro 3.1"
    else -> "تلقائي"
  }

private fun thinkingLabel(level: String?): String =
  when (level) {
    "LOW" -> "منخفض"
    "MEDIUM" -> "متوسط"
    "HIGH" -> "مرتفع"
    else -> "تلقائي"
  }

/**
 * Gemini-style model picker: a compact dropdown anchored to the top bar,
 * exactly like the Gemini app — model rows with a check on the selected one,
 * then a "مستوى التفكير" row that expands in place. No full-screen dialog,
 * no long explanations.
 */
@Composable
private fun GeminiModelMenu(
  expanded: Boolean,
  currentModelId: String?,
  currentThinkingLevel: String?,
  onDismiss: () -> Unit,
  onModelSelected: (String?) -> Unit,
  onThinkingLevelSelected: (String?) -> Unit,
  thinkingExpanded: Boolean,
  onToggleThinking: () -> Unit,
) {
  val modelOptions =
    listOf(
      Triple(null, "تلقائي", "يختار الأفضل المتاح تلقائيًا"),
      Triple("gemini-3.8-flash", "Flash 3.8", "الأحدث والأسرع للاستخدام اليومي"),
      Triple("gemini-3.5-flash", "Flash 3.5", "سريع وموثوق"),
      Triple("gemini-flash-latest", "Flash الأحدث", "يتبع آخر إصدار Flash"),
      Triple("gemini-3.1-flash-lite-preview", "Flash-Lite 3.1", "الأخف على الحصة"),
      Triple("gemini-3.1-pro-preview", "Pro 3.1", "أعمق تحليلًا، لكنه أبطأ"),
    )
  val thinkingOptions =
    listOf(
      Triple(null, "تلقائي", "إعداد النموذج الافتراضي"),
      Triple("LOW", "منخفض", "سرعة وفعالية"),
      Triple("MEDIUM", "متوسط", "عمق معتدل"),
      Triple("HIGH", "مرتفع", "دقة عالية، أبطأ"),
    )

  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    modelOptions.forEach { (modelId, label, description) ->
      DropdownMenuItem(
        text = {
          Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
              text = description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        leadingIcon = {
          if (currentModelId == modelId) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null)
          }
        },
        onClick = { onModelSelected(modelId) },
      )
    }
    HorizontalDivider()
    DropdownMenuItem(
      text = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "مستوى التفكير",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
          )
          Text(
            text = thinkingLabel(currentThinkingLevel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      },
      trailingIcon = {
        Icon(
          imageVector =
            if (thinkingExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
          contentDescription = null,
        )
      },
      onClick = onToggleThinking,
    )
    if (thinkingExpanded) {
      thinkingOptions.forEach { (level, label, description) ->
        DropdownMenuItem(
          text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Spacer(modifier = Modifier.width(16.dp))
              Column {
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                Text(
                  text = description,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          },
          leadingIcon = {
            if (currentThinkingLevel == level) {
              Icon(imageVector = Icons.Default.Check, contentDescription = null)
            }
          },
          onClick = { onThinkingLevelSelected(level) },
        )
      }
    }
  }
}

/**
 * Gemini-style side drawer: "محادثة جديدة" and "دردشة مؤقتة" at the top,
 * then the saved conversations, then backup (export/import) at the bottom.
 * Per-conversation actions live in a 3-dot menu on each row, as in other
 * AI apps, instead of a row full of icons.
 */
@Composable
private fun AssistantHistoryDrawer(
  conversations: List<ChatConversation>,
  activeConversationId: String?,
  isTemporaryChat: Boolean,
  onSelect: (String) -> Unit,
  onCreate: () -> Unit,
  onTemporary: () -> Unit,
  onRename: (String, String) -> Unit,
  onTogglePin: (String) -> Unit,
  onDelete: (String) -> Unit,
  onClose: () -> Unit,
) {
  var renameTarget by remember { mutableStateOf<ChatConversation?>(null) }
  var renameText by remember { mutableStateOf("") }
  var deleteTarget by remember { mutableStateOf<ChatConversation?>(null) }
  var overflowId by remember { mutableStateOf<String?>(null) }
  var searchQuery by remember { mutableStateOf("") }
  var sortMode by remember { mutableStateOf(0) } // 0 = الأحدث، 1 = الأقدم، 2 = الاسم
  var showSortMenu by remember { mutableStateOf(false) }

  // Pinned conversations stay on top in every sort mode; the mode orders
  // within the pinned and unpinned groups.
  val displayedConversations =
    conversations
      .filter { searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) }
      .sortedWith(
        compareByDescending<ChatConversation> { it.pinned }
          .thenComparator { a, b ->
            when (sortMode) {
              1 -> a.updatedAtMillis.compareTo(b.updatedAtMillis)
              2 -> a.title.compareTo(b.title)
              else -> b.updatedAtMillis.compareTo(a.updatedAtMillis)
            }
          },
      )

  Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = stringResource(R.string.assistant_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = onClose) {
        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق")
      }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
      Icon(imageVector = Icons.Default.Add, contentDescription = null)
      Spacer(modifier = Modifier.width(6.dp))
      Text(text = stringResource(R.string.chat_history_new))
    }
    Spacer(modifier = Modifier.height(8.dp))
    NavigationDrawerItem(
      label = { Text(text = "دردشة مؤقتة") },
      selected = isTemporaryChat,
      onClick = onTemporary,
      icon = { Icon(imageVector = Icons.Default.Bolt, contentDescription = null) },
      badge = {
        Text(
          text = "لا تُحفظ",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
    )
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = "المحادثات",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f),
      )
      Box {
        IconButton(onClick = { showSortMenu = true }) {
          Icon(imageVector = Icons.Default.Sort, contentDescription = "فرز المحادثات")
        }
        DropdownMenu(
          expanded = showSortMenu,
          onDismissRequest = { showSortMenu = false },
        ) {
          listOf("الأحدث أولًا", "الأقدم أولًا", "الاسم (أ – ي)").forEachIndexed { index, label ->
            DropdownMenuItem(
              text = { Text(text = label) },
              leadingIcon = {
                if (sortMode == index) {
                  Icon(imageVector = Icons.Default.Check, contentDescription = null)
                }
              },
              onClick = {
                sortMode = index
                showSortMenu = false
              },
            )
          }
        }
      }
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text(text = "بحث في المحادثات") },
      leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { searchQuery = "" }) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "مسح البحث")
          }
        }
      },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    if (conversations.isEmpty()) {
      Text(
        text = stringResource(R.string.chat_history_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else if (displayedConversations.isEmpty()) {
      Text(
        text = "لا نتائج مطابقة للبحث",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        items(displayedConversations, key = { it.id }) { conversation ->
          Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
              NavigationDrawerItem(
                label = {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = conversation.title,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f),
                    )
                    if (conversation.pinned) {
                      Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "مثبتة",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                      )
                    }
                  }
                },
                selected = conversation.id == activeConversationId,
                onClick = { onSelect(conversation.id) },
                modifier = Modifier.weight(1f),
              )
              IconButton(onClick = { overflowId = conversation.id }) {
                Icon(
                  imageVector = Icons.Default.MoreVert,
                  contentDescription = "خيارات المحادثة",
                )
              }
            }
            DropdownMenu(
              expanded = overflowId == conversation.id,
              onDismissRequest = { overflowId = null },
            ) {
              DropdownMenuItem(
                text = {
                  Text(
                    text =
                      if (conversation.pinned) "إلغاء التثبيت" else "تثبيت",
                  )
                },
                leadingIcon = {
                  Icon(imageVector = Icons.Default.PushPin, contentDescription = null)
                },
                onClick = {
                  onTogglePin(conversation.id)
                  overflowId = null
                },
              )
              DropdownMenuItem(
                text = { Text(text = "إعادة تسمية") },
                leadingIcon = {
                  Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                },
                onClick = {
                  renameTarget = conversation
                  renameText = conversation.title
                  overflowId = null
                },
              )
              DropdownMenuItem(
                text = { Text(text = "حذف") },
                leadingIcon = {
                  Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                },
                onClick = {
                  deleteTarget = conversation
                  overflowId = null
                },
              )
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
