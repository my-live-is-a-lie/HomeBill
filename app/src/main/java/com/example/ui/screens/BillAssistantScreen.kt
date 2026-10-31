package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalClipboardManager
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.BillCategory
import com.example.ui.components.AssistantIcons
import com.example.ui.components.FullscreenImageViewer
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

  // The quick-prompt bar at the top collapses while the user scrolls down through the chat
  // (finger moving up) and comes back when they scroll up (finger moving down).
  var quickBarVisible by remember { mutableStateOf(true) }
  val quickBarScrollConnection =
    remember(listState) {
      object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
          if (available.y < -4f && listState.canScrollForward) {
            quickBarVisible = false
          } else if (available.y > 4f) {
            quickBarVisible = true
          }
          return Offset.Zero
        }
      }
    }
  // A fresh or different conversation starts with the bar visible.
  LaunchedEffect(activeConversation?.id) { quickBarVisible = true }

  // The "jump to latest" arrow is shown whenever there is more chat below the visible part.
  val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }
  val showScrollToBottom = messages.isNotEmpty() && canScrollForward

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
      // Gemini-style drawer: flat, full-height, no rounded edge, same background as the app.
      ModalDrawerSheet(
        drawerShape = RectangleShape,
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerTonalElevation = 0.dp,
      ) {
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
                imageVector = AssistantIcons.Sidebar,
                contentDescription = "القائمة وسجل المحادثات",
              )
            }
          },
          title = {
          Box {
            Column {
              // Model name + thinking level in one capsule, like other AI chat apps.
              ModelThinkingCapsule(
                modelText = modelLabel(activeConversation?.modelId),
                thinkingText = activeConversation?.thinkingLevel?.let { thinkingLabel(it) },
                onClick = { showModelMenu = true },
              )
              Spacer(modifier = Modifier.height(2.dp))
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
          if (isTemporaryChat) {
            // Inside a temporary chat the button becomes an X that leaves it (and discards it),
            // exactly like the Gemini app.
            IconButton(
              onClick = onCreateConversation,
              modifier = Modifier.testTag("exit_temporary_chat_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "إغلاق الدردشة المؤقتة",
              )
            }
          } else {
            IconButton(
              onClick = onTemporaryChat,
              modifier = Modifier.testTag("temporary_chat_button"),
            ) {
              TempChatIcon()
            }
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
      // A fresh temporary chat shows the "محادثة عابرة؟" page (like Gemini) instead of the
      // welcome card and quick prompts. It switches to the normal list on the first message.
      val showTemporaryIntro = isTemporaryChat && !isTyping && messages.none { it.isUser }

      if (showTemporaryIntro) {
        TemporaryChatIntro(
          modifier =
            Modifier.weight(1f)
              .widthIn(max = 760.dp)
              .fillMaxWidth()
              .testTag("temporary_chat_intro"),
        )
      } else {
        // Quick Prompt Toolbar (collapses while scrolling down through the chat)
        AnimatedVisibility(
          visible = quickBarVisible,
          enter = expandVertically() + fadeIn(),
          exit = shrinkVertically() + fadeOut(),
        ) {
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
        }

        // Conversation Messages List, with the "jump to latest" arrow floating over its bottom edge
        Box(modifier = Modifier.weight(1f).widthIn(max = 760.dp).fillMaxWidth()) {
          LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
              Modifier.fillMaxSize()
                .nestedScroll(quickBarScrollConnection)
                .testTag("assistant_messages_list"),
          ) {
            itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
              val isLatestAssistant = !msg.isUser && index == messages.lastIndex && !isTyping
              if (msg.isUser) {
                UserChatBubble(
                  text = msg.text,
                  imagePath = msg.imagePath,
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

          // Fully qualified on purpose: inside this Box, the short name resolves to the
          // ColumnScope overload of the enclosing Column, which Kotlin rejects here (the Box's
          // BoxScope is a layout DSL marker). The fully qualified call picks the plain overload.
          androidx.compose.animation.AnimatedVisibility(
            visible = showScrollToBottom,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
          ) {
            ScrollToBottomButton(
              onClick = {
                scope.launch {
                  val totalItems = messages.size + if (isTyping) 1 else 0
                  if (totalItems > 0) {
                    listState.animateScrollToItem(totalItems - 1)
                    // A long last message can extend below the screen; continue to its end.
                    if (listState.canScrollForward) listState.animateScrollBy(100_000f)
                  }
                }
              },
            )
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

      // Composer Bar: a floating capsule (pill) like other AI chat apps.
      val canSend = inputText.isNotBlank() && !isTyping
      val submitInput: () -> Unit = {
        val prompt = inputText.trim()
        if (prompt.isNotEmpty() && !isTyping) {
          inputValue = TextFieldValue("")
          onSendMessage(prompt)
        }
      }
      Box(
        modifier =
          Modifier.widthIn(max = 760.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
      ) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(30.dp),
          tonalElevation = 2.dp,
          shadowElevation = 2.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
          ) {
            BasicTextField(
              value = inputValue,
              onValueChange = { inputValue = it },
              maxLines = 5,
              textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                  color = MaterialTheme.colorScheme.onSurface
                ),
              cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
              keyboardActions = KeyboardActions(onSend = { submitInput() }),
              decorationBox = { innerTextField ->
                Box(
                  contentAlignment = Alignment.CenterStart,
                  modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                ) {
                  if (inputText.isEmpty()) {
                    Text(
                      text = stringResource(R.string.assistant_input_hint),
                      style = MaterialTheme.typography.bodyLarge,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  innerTextField()
                }
              },
              modifier =
                Modifier.weight(1f)
                  .focusRequester(inputFocusRequester)
                  .testTag("chat_input_field"),
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Dim while there is nothing to send; filled with the accent color once the user
            // has typed something, so it is clear the button is usable.
            // ArrowForward is auto-mirrored: it points left in the RTL (Arabic) layout.
            FilledIconButton(
              onClick = submitInput,
              enabled = canSend,
              colors =
                IconButtonDefaults.filledIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                  disabledContainerColor =
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                  disabledContentColor =
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                ),
              modifier = Modifier.size(44.dp).testTag("chat_send_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.assistant_send_desc),
                modifier = Modifier.size(24.dp),
              )
            }
          }
        }
      }
    }
  }

  }
}

/** Small round arrow that jumps to the latest message; shown when there is more chat below. */
@Composable
private fun ScrollToBottomButton(onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor = MaterialTheme.colorScheme.onSurface,
    tonalElevation = 3.dp,
    shadowElevation = 6.dp,
    modifier = Modifier.size(40.dp).testTag("scroll_to_bottom_button"),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = Icons.Default.ArrowDownward,
        contentDescription = "النزول إلى آخر الدردشة",
        modifier = Modifier.size(22.dp),
      )
    }
  }
}

/**
 * The page shown when a temporary chat has just been opened, like the "محادثة عابرة؟" page in
 * Gemini: the dashed-circle pencil, a large title and a short explanation, centered on screen.
 */
@Composable
private fun TemporaryChatIntro(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.padding(horizontal = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    TempChatIcon(size = 40.dp)
    Spacer(modifier = Modifier.height(20.dp))
    Text(
      text = "محادثة عابرة؟",
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = "لا تظهر المحادثات المؤقتة في سجل المحادثات، ولا تُحفظ على جهازك، وتُحذف بمجرد مغادرتها.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

/**
 * Model name and thinking level in a single capsule that opens the model menu when tapped.
 * The thinking level is shown in a muted color before the model name (as "Low Sonnet 5.5" in
 * other AI apps); when the level is automatic only the model name is shown.
 */
@Composable
private fun ModelThinkingCapsule(
  modelText: String,
  thinkingText: String?,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.testTag("model_thinking_capsule"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
    ) {
      if (thinkingText != null) {
        Text(
          text = thinkingText,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
      }
      Text(
        text = modelText,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Icon(
        imageVector = Icons.Default.KeyboardArrowDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
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
 * [size] scales the whole mark (24.dp in the top bar, larger on the temporary chat page).
 */
@Composable
private fun TempChatIcon(size: Dp = 24.dp) {
  val circleColor = MaterialTheme.colorScheme.onSurface
  val scale = size.value / 24f
  Box(
    modifier =
      Modifier.size(size).drawBehind {
        drawCircle(
          color = circleColor,
          style =
            Stroke(
              width = 1.5.dp.toPx() * scale,
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * scale, 4f * scale), 0f),
            ),
        )
      },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Default.Edit,
      contentDescription = "دردشة مؤقتة",
      modifier = Modifier.size(size * 0.625f),
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
 * Gemini-style side drawer, flat and minimal:
 *  - top row: search and close icons (search turns the row into a search field);
 *  - a "محادثة جديدة" pill;
 *  - a "الكل" header with a filter icon that opens the sort menu;
 *  - the conversations as plain text rows. Long-press a row for pin / rename / delete.
 *
 * [isTemporaryChat] and [onTemporary] are kept in the signature so callers do not change;
 * the temporary chat is opened from the button in the top bar.
 */
@OptIn(ExperimentalFoundationApi::class)
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
  var searchActive by remember { mutableStateOf(false) }
  val searchFocusRequester = remember { FocusRequester() }
  var sortMode by remember { mutableStateOf(0) } // 0 = الأحدث، 1 = الأقدم، 2 = الاسم
  var showSortMenu by remember { mutableStateOf(false) }

  LaunchedEffect(searchActive) {
    if (searchActive) searchFocusRequester.requestFocus()
  }

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

  Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
    // Top row: search + close, or the search field when search is active.
    if (searchActive) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(start = 16.dp, end = 4.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.width(10.dp))
          BasicTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            singleLine = true,
            textStyle =
              MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
              ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
              Box(contentAlignment = Alignment.CenterStart) {
                if (searchQuery.isEmpty()) {
                  Text(
                    text = "بحث في المحادثات",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                innerTextField()
              }
            },
            modifier =
              Modifier.weight(1f)
                .focusRequester(searchFocusRequester)
                .testTag("conversation_search_field"),
          )
          IconButton(
            onClick = {
              searchQuery = ""
              searchActive = false
            },
          ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق البحث")
          }
        }
      }
    } else {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        IconButton(
          onClick = { searchActive = true },
          modifier = Modifier.testTag("conversation_search_button"),
        ) {
          Icon(imageVector = Icons.Default.Search, contentDescription = "بحث في المحادثات")
        }
        IconButton(onClick = onClose) {
          Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق")
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // New conversation pill
    Surface(
      onClick = onCreate,
      color = MaterialTheme.colorScheme.surfaceVariant,
      shape = RoundedCornerShape(28.dp),
      modifier = Modifier.fillMaxWidth().height(56.dp).testTag("new_chat_button"),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 20.dp),
      ) {
        Icon(imageVector = Icons.Default.Edit, contentDescription = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = stringResource(R.string.chat_history_new),
          style = MaterialTheme.typography.titleMedium,
        )
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Section header: "الكل" with the sort/filter menu
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = "الكل",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
      )
      Box {
        IconButton(onClick = { showSortMenu = true }) {
          Icon(
            imageVector = AssistantIcons.Filter,
            contentDescription = "فرز المحادثات",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
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

    Spacer(modifier = Modifier.height(4.dp))

    if (conversations.isEmpty()) {
      Text(
        text = stringResource(R.string.chat_history_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp),
      )
    } else if (displayedConversations.isEmpty()) {
      Text(
        text = "لا نتائج مطابقة للبحث",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp),
      )
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        items(displayedConversations, key = { it.id }) { conversation ->
          val isSelected = conversation.id == activeConversationId
          Box {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(16.dp))
                  .background(
                    if (isSelected) {
                      MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    } else {
                      androidx.compose.ui.graphics.Color.Transparent
                    }
                  )
                  .combinedClickable(
                    onClick = { onSelect(conversation.id) },
                    onLongClick = { overflowId = conversation.id },
                  )
                  .padding(horizontal = 12.dp, vertical = 14.dp)
                  .testTag("conversation_item_${conversation.id}"),
            ) {
              Text(
                text = conversation.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              if (conversation.pinned) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                  imageVector = Icons.Default.PushPin,
                  contentDescription = "مثبتة",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(16.dp),
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


/**
 * A user message. A bill image sent with it is shown on its own above the bubble, so the
 * colored bubble surrounds only the text. The timestamp and copy button sit below.
 */
@Composable
private fun UserChatBubble(
  text: String,
  imagePath: String?,
  timestampMillis: Long,
  onCopyClick: () -> Unit,
) {
  val imageFile =
    remember(imagePath) { imagePath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L } }
  var showFullImage by remember { mutableStateOf(false) }
  if (showFullImage && imageFile != null) {
    FullscreenImageViewer(imageFile = imageFile, onDismiss = { showFullImage = false })
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.Start,
  ) {
    if (imageFile != null) {
      AsyncImage(
        model = imageFile,
        contentDescription = stringResource(R.string.assistant_image_attached_title),
        contentScale = ContentScale.Fit,
        modifier =
          Modifier.widthIn(max = 240.dp)
            .heightIn(max = 280.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { showFullImage = true }
            .testTag("chat_message_image"),
      )
      if (text.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
      }
    }
    if (text.isNotBlank()) {
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
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(start = 4.dp),
    ) {
      Text(
        text = formatRelativeTime(timestampMillis),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      IconButton(onClick = onCopyClick, modifier = Modifier.size(32.dp)) {
        Icon(
          imageVector = Icons.Default.ContentCopy,
          contentDescription = "نسخ الرسالة",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp),
        )
      }
    }
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

        // Footer: timestamp and the copy button at the bottom of the message.
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
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
          if (!message.isError) {
            IconButton(
              onClick = onCopyClick,
              modifier = Modifier.size(32.dp),
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
      }
    }

    if (showSuggestions && message.followUpSuggestions.isNotEmpty()) {
      Spacer(modifier = Modifier.height(4.dp))
      // Compact suggestions: tight vertical gaps so more of the conversation stays visible.
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        message.followUpSuggestions.forEach { suggestion ->
          CompactSuggestionChip(
            text = suggestion,
            onClick = { onSuggestionClick(suggestion) },
          )
        }
      }
    }
  }
}

/**
 * Outlined suggestion pill with a compact height. Material's SuggestionChip reserves a 48dp
 * touch target around a 32dp chip, which adds big empty gaps between stacked chips; this one
 * is sized by its content (about 32dp) so the suggestions take less vertical space.
 */
@Composable
private fun CompactSuggestionChip(
  text: String,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = androidx.compose.ui.graphics.Color.Transparent,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
    )
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
