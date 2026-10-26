package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.remote.ChatChartBar
import com.example.data.remote.ChatStatHighlight
import com.example.data.remote.GeminiBillChatService
import com.example.domain.MonthlyAnalyticsSummary
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BillChatMessage(
  val id: String = UUID.randomUUID().toString(),
  val timestampMillis: Long = System.currentTimeMillis(),
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

  // Messages of the active conversation, kept for the existing screen wiring.
  private val _messages = MutableStateFlow(listOf(createWelcomeMessage()))
  val messages: StateFlow<List<BillChatMessage>> = _messages.asStateFlow()

  private val _isTyping = MutableStateFlow(false)
  val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

  private val _conversations = MutableStateFlow<List<ChatConversation>>(emptyList())
  val conversations: StateFlow<List<ChatConversation>> = _conversations.asStateFlow()

  private val _activeConversation = MutableStateFlow<ChatConversation?>(null)
  val activeConversation: StateFlow<ChatConversation?> = _activeConversation.asStateFlow()

  // A temporary chat lives only in memory: it never enters _conversations,
  // is never written to disk, never exported, and is discarded as soon as the
  // user switches to a saved conversation or starts another temporary chat.
  private var temporaryConversation: ChatConversation? = null
  private val _isTemporaryChat = MutableStateFlow(false)
  val isTemporaryChat: StateFlow<Boolean> = _isTemporaryChat.asStateFlow()

  private var historyLoaded = false
  private var lastConsumedInitialPrompt: String? = null

  /**
   * Loads local history once (called from MainActivity after sign-in, when the
   * user id is known). Before this call the ViewModel behaves exactly like the
   * old single-conversation version, so nothing breaks if it is never called.
   * Previous versions never saved anything, so the first run after this change
   * legitimately starts with one empty conversation.
   */
  fun loadHistory(context: Context, userId: String) {
    if (historyLoaded) return
    historyLoaded = true
    ChatHistoryStore.init(context, userId)
    val saved = ChatHistoryStore.load()
    if (saved.isEmpty()) {
      val fresh = newConversation()
      publish(listOf(fresh), fresh.id)
    } else {
      val sorted = sortConversations(saved)
      val active = sorted.firstOrNull { it.messages.isNotEmpty() } ?: sorted.first()
      publish(sorted, active.id)
    }
  }

  fun createConversation() {
    discardTemporaryChat()
    val fresh = newConversation()
    lastConsumedInitialPrompt = null
    _isTyping.value = false
    publish(_conversations.value + fresh, fresh.id)
    persist()
  }

  /**
   * Starts a fresh temporary chat and switches to it. Any previous temporary
   * chat is discarded, unsaved, by design. The top-bar button that used to
   * clear the active conversation now calls this instead.
   */
  fun enterTemporaryChat() {
    val now = System.currentTimeMillis()
    val temp = ChatConversation(
      id = UUID.randomUUID().toString(),
      title = "دردشة مؤقتة",
      createdAtMillis = now,
      updatedAtMillis = now,
      pinned = false,
      modelId = null,
      thinkingLevel = null,
      messages = listOf(createWelcomeMessage()),
    )
    temporaryConversation = temp
    _isTemporaryChat.value = true
    lastConsumedInitialPrompt = null
    _isTyping.value = false
    _activeConversation.value = temp
    _messages.value = temp.messages
  }

  private fun discardTemporaryChat() {
    temporaryConversation = null
    _isTemporaryChat.value = false
  }

  fun selectConversation(conversationId: String) {
    if (!_isTemporaryChat.value && _activeConversation.value?.id == conversationId) return
    // Leaving a temporary chat discards it, unsaved.
    discardTemporaryChat()
    lastConsumedInitialPrompt = null
    _isTyping.value = false
    publish(_conversations.value, conversationId)
  }

  fun renameConversation(conversationId: String, newTitle: String) {
    val clean = newTitle.trim()
    if (clean.isBlank()) return
    val updated = _conversations.value.map { conversation ->
      if (conversation.id == conversationId) conversation.copy(title = clean) else conversation
    }
    publish(updated, _activeConversation.value?.id)
    persist()
  }

  fun togglePin(conversationId: String) {
    val updated = _conversations.value.map { conversation ->
      if (conversation.id == conversationId) conversation.copy(pinned = !conversation.pinned) else conversation
    }
    publish(updated, _activeConversation.value?.id)
    persist()
  }

  fun setConversationModel(conversationId: String, modelId: String?) {
    updateConversationField(conversationId) { it.copy(modelId = modelId) }
  }

  fun setConversationThinkingLevel(conversationId: String, thinkingLevel: String?) {
    updateConversationField(conversationId) { it.copy(thinkingLevel = thinkingLevel) }
  }

  private fun updateConversationField(
    conversationId: String,
    transform: (ChatConversation) -> ChatConversation,
  ) {
    val temp = temporaryConversation
    if (_isTemporaryChat.value && temp != null && temp.id == conversationId) {
      val updatedTemp = transform(temp)
      temporaryConversation = updatedTemp
      _activeConversation.value = updatedTemp
      return
    }
    val updated = _conversations.value.map { conversation ->
      if (conversation.id == conversationId) transform(conversation) else conversation
    }
    publish(updated, _activeConversation.value?.id)
    persist()
  }

  fun deleteConversation(conversationId: String) {
    if (_isTemporaryChat.value && temporaryConversation?.id == conversationId) {
      enterTemporaryChat()
      return
    }
    val remaining = _conversations.value.filterNot { it.id == conversationId }
    if (remaining.isEmpty()) {
      val fresh = newConversation()
      lastConsumedInitialPrompt = null
      _isTyping.value = false
      publish(listOf(fresh), fresh.id)
    } else {
      val sorted = sortConversations(remaining)
      lastConsumedInitialPrompt = null
      _isTyping.value = false
      publish(sorted, sorted.first().id)
    }
    persist()
  }

  /** Export every conversation as one backup JSON string. */
  fun exportAllConversations(): String {
    return ChatHistoryStore.toJson(_conversations.value)
  }

  /** Export a single conversation as a backup JSON string. */
  fun exportConversation(conversationId: String): String {
    val single = _conversations.value.filter { it.id == conversationId }
    return ChatHistoryStore.toJson(single)
  }

  /**
   * Imports a backup JSON string. Conversations already present (same id) are
   * skipped, never overwritten; new ones are appended. Returns the counts for
   * the confirmation toast.
   */
  fun importConversations(json: String): ChatImportResult {
    return runCatching {
      val imported = ChatHistoryStore.fromJson(json)
      val (merged, result) = ChatHistoryStore.mergeImport(_conversations.value, imported)
      publish(sortConversations(merged), _activeConversation.value?.id)
      persist()
      result
    }.getOrElse { ChatImportResult(imported = 0, skipped = 0) }
  }

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
    updateActiveMessages(cleanedMessages + userMessage, newUserPrompt = trimmed)
    persist()
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

    updateActiveMessages(withoutErrors.subList(0, lastUserIndex + 1))
    persist()
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
          preferredModel = _activeConversation.value?.modelId,
          thinkingLevel = _activeConversation.value?.thinkingLevel,
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
          updateActiveMessages(_messages.value + aiMsg)
          persist()
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
          // Error cards are transient: shown, but not saved into history.
          _messages.value = _messages.value + errorMsg
        }
    }
  }

  fun clearConversation() {
    val active = _activeConversation.value
    if (active == null) {
      _messages.value = listOf(createWelcomeMessage())
      return
    }
    if (_isTemporaryChat.value) {
      val resetTemp = active.copy(messages = listOf(createWelcomeMessage()), updatedAtMillis = System.currentTimeMillis())
      temporaryConversation = resetTemp
      _activeConversation.value = resetTemp
      _messages.value = resetTemp.messages
      return
    }
    lastConsumedInitialPrompt = null
    _isTyping.value = false
    val reset = active.copy(messages = listOf(createWelcomeMessage()), updatedAtMillis = System.currentTimeMillis())
    val updated = _conversations.value.map { if (it.id == active.id) reset else it }
    publish(updated, active.id)
    persist()
  }

  private fun newConversation(): ChatConversation {
    val now = System.currentTimeMillis()
    return ChatConversation(
      id = UUID.randomUUID().toString(),
      title = "محادثة جديدة",
      createdAtMillis = now,
      updatedAtMillis = now,
      pinned = false,
      modelId = null,
      thinkingLevel = null,
      messages = listOf(createWelcomeMessage()),
    )
  }

  private fun sortConversations(list: List<ChatConversation>): List<ChatConversation> {
    return list.sortedWith(
      compareByDescending<ChatConversation> { it.pinned }
        .thenByDescending { it.updatedAtMillis },
    )
  }

  /**
   * Publishes a conversation list and selects the active one. The active
   * conversation's messages become [_messages].
   */
  private fun publish(list: List<ChatConversation>, activeId: String?) {
    // If the requested active id is not a saved conversation, it was the
    // temporary chat: leaving it through a saved-list action discards it.
    if (activeId != null && list.none { it.id == activeId }) {
      discardTemporaryChat()
    }
    val sorted = sortConversations(list)
    _conversations.value = sorted
    val active = sorted.firstOrNull { it.id == activeId } ?: sorted.firstOrNull()
    _activeConversation.value = active
    _messages.value = active?.messages ?: listOf(createWelcomeMessage())
  }

  private fun updateActiveMessages(newMessages: List<BillChatMessage>, newUserPrompt: String? = null) {
    val active = _activeConversation.value
    if (active == null) {
      _messages.value = newMessages
      return
    }
    if (_isTemporaryChat.value) {
      // Temporary chats are never written to the saved list or to disk.
      val updatedTemp = active.copy(messages = newMessages, updatedAtMillis = System.currentTimeMillis())
      temporaryConversation = updatedTemp
      _activeConversation.value = updatedTemp
      _messages.value = newMessages
      return
    }
    var updated = active.copy(messages = newMessages, updatedAtMillis = System.currentTimeMillis())
    // Auto-title a fresh conversation from its first user message.
    if (newUserPrompt != null && active.title == "محادثة جديدة") {
      updated = updated.copy(title = ChatHistoryStore.defaultTitle(newMessages))
    }
    val list = _conversations.value.map { if (it.id == active.id) updated else it }
    _messages.value = newMessages
    _conversations.value = sortConversations(list)
    _activeConversation.value = updated
  }

  private fun persist() {
    val snapshot = _conversations.value
    viewModelScope.launch(Dispatchers.IO) {
      ChatHistoryStore.save(snapshot)
    }
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
