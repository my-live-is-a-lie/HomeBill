package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BillCategory
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
  onClearChat: () -> Unit,
) {
  var inputText by rememberSaveable { mutableStateOf("") }
  val listState = rememberLazyListState()
  val clipboardManager = LocalClipboardManager.current
  val context = LocalContext.current
  val copiedToastText = stringResource(R.string.assistant_copied_toast)

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
                text = stringResource(R.string.assistant_title),
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
            onClick = onClearChat,
            modifier = Modifier.testTag("clear_chat_button"),
          ) {
            Icon(
              imageVector = Icons.Default.DeleteSweep,
              contentDescription = stringResource(R.string.assistant_clear_chat),
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
            UserChatBubble(text = msg.text)
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
}

@Composable
private fun UserChatBubble(text: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Start,
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
