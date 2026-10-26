package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.remote.ChatChartBar
import com.example.data.remote.ChatStatHighlight
import com.example.data.remote.GeminiBillChatService
import com.example.domain.MonthlyAnalyticsSummary
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BillChatMessage(
  val id: String = UUID.randomUUID().toString(),
  val isUser: Boolean,
  val text: String,
  val highlights: List<ChatStatHighlight> = emptyList(),
  val chartTitle: String = "",
  val chartBars: List<ChatChartBar> = emptyList(),
  val followUpSuggestions: List<String> = emptyList(),
  val isError: Boolean = false,
)

class BillChatViewModel(
  private val chatService: GeminiBillChatService = GeminiBillChatService(),
) : ViewModel() {

  private val _messages = MutableStateFlow(listOf(createWelcomeMessage()))
  val messages: StateFlow<List<BillChatMessage>> = _messages.asStateFlow()

  private val _isTyping = MutableStateFlow(false)
  val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

  private var lastConsumedInitialPrompt: String? = null

  fun consumeInitialPromptIfNeeded(
    prompt: String?,
    categories: List<BillCategory>,
    allBills: List<BillRecord>,
    analyticsSummary: MonthlyAnalyticsSummary,
  ) {
    val clean = prompt?.trim()
    if (clean.isNullOrEmpty() || clean == lastConsumedInitialPrompt) return
    lastConsumedInitialPrompt = clean
    sendMessage(clean, categories, allBills, analyticsSummary)
  }

  fun sendMessage(
    prompt: String,
    categories: List<BillCategory>,
    allBills: List<BillRecord>,
    analyticsSummary: MonthlyAnalyticsSummary,
  ) {
    val trimmed = prompt.trim()
    if (trimmed.isEmpty() || _isTyping.value) return

    val cleanedMessages = _messages.value.filterNot { it.isError }
    val priorHistory =
      cleanedMessages
        .drop(1) // Skip static local welcome card to keep turn sequence strictly user -> model
        .map { it.isUser to it.text }

    val userMessage = BillChatMessage(isUser = true, text = trimmed)
    _messages.value = cleanedMessages + userMessage
    executeTurn(
      priorHistory = priorHistory,
      userPrompt = trimmed,
      categories = categories,
      allBills = allBills,
      analyticsSummary = analyticsSummary,
    )
  }

  fun retryLastUserMessage(
    categories: List<BillCategory>,
    allBills: List<BillRecord>,
    analyticsSummary: MonthlyAnalyticsSummary,
  ) {
    if (_isTyping.value) return
    val withoutErrors = _messages.value.filterNot { it.isError }
    val lastUserIndex = withoutErrors.indexOfLast { it.isUser }
    if (lastUserIndex < 0) return

    val lastUserMsg = withoutErrors[lastUserIndex]
    val historyBeforeLastUser =
      withoutErrors
        .subList(0, lastUserIndex)
        .drop(1)
        .map { it.isUser to it.text }

    _messages.value = withoutErrors.subList(0, lastUserIndex + 1)
    executeTurn(
      priorHistory = historyBeforeLastUser,
      userPrompt = lastUserMsg.text,
      categories = categories,
      allBills = allBills,
      analyticsSummary = analyticsSummary,
    )
  }

  private fun executeTurn(
    priorHistory: List<Pair<Boolean, String>>,
    userPrompt: String,
    categories: List<BillCategory>,
    allBills: List<BillRecord>,
    analyticsSummary: MonthlyAnalyticsSummary,
  ) {
    _isTyping.value = true
    viewModelScope.launch {
      val result =
        chatService.sendChatTurn(
          conversationHistory = priorHistory,
          userPrompt = userPrompt,
          categories = categories,
          allBills = allBills,
          analyticsSummary = analyticsSummary,
        )

      _isTyping.value = false

      result
        .onSuccess { dto ->
          val aiMsg =
            BillChatMessage(
              isUser = false,
              text = dto.replyText,
              highlights = dto.highlights,
              chartTitle = dto.chartTitle,
              chartBars = dto.chartBars,
              followUpSuggestions = dto.followUpSuggestions,
            )
          _messages.value = _messages.value + aiMsg
        }
        .onFailure { err ->
          val errorMsg =
            BillChatMessage(
              isUser = false,
              text =
                err.localizedMessage
                  ?: "تعذر الاتصال بالمساعد الذكي حالياً، يرجى المحاولة مرة أخرى.",
              isError = true,
            )
          _messages.value = _messages.value + errorMsg
        }
    }
  }

  fun clearConversation() {
    lastConsumedInitialPrompt = null
    _isTyping.value = false
    _messages.value = listOf(createWelcomeMessage())
  }

  companion object {
    private fun createWelcomeMessage(): BillChatMessage =
      BillChatMessage(
        isUser = false,
        text =
          "مرحباً بك! أنا مساعد «فواتيري» الذكي المدعوم بـ Gemini.\n\n" +
            "يمكنك التحدث معي حول:\n" +
            "• إعداد إحصائيات ومقارنات فورية لفواتير الغاز والكهرباء والماء والإنترنت.\n" +
            "• تحليل أسباب ارتفاع الفاتورة أو الاستهلاك ومقارنة بنود الـ Sheet.\n" +
            "• اقتراح خطوات عملية لترشيد الاستهلاك وخفض التكاليف الشهرية.\n" +
            "• شرح طريقة استخدام المسح الضوئي بالكاميرا وتصدير جداول CSV.",
        followUpSuggestions =
          listOf(
            "اعمل لي إحصائية شاملة لفواتيري ومصاريف الأشهر",
            "هل يوجد تفاوت أو ارتفاع غير معتاد في فواتيري؟",
            "كيف أصوّر فاتورة وأستخرج منها جدول Sheet؟",
          ),
      )
  }
}
