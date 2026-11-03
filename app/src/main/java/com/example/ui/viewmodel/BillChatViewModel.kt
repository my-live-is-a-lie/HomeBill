package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CurrencyPreferenceStore
import com.example.data.IpLocationResolver
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.remote.BillChatAiReplyDto
import com.example.data.remote.ChatContextMode
import com.example.data.remote.ChatContextSelection
import com.example.data.remote.ChatContextSelector
import com.example.data.remote.CurrencyProposal
import com.example.data.remote.ChatChartBar
import com.example.data.remote.ChatStatHighlight
import com.example.data.remote.GeminiBillChatService
import com.example.domain.MonthlyAnalyticsSummary
import com.example.util.AssistantImageBridge
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
  // Path of the bill image that was sent together with this user message (null = no image).
  val imagePath: String? = null,
)

class BillChatViewModel(
  private val chatService: GeminiBillChatService = GeminiBillChatService(),
) : ViewModel() {

  // Messages of the active conversation, kept for the existing screen wiring.
  private val _messages = MutableStateFlow(listOf(createWelcomeMessage()))
  val messages: StateFlow<List<BillChatMessage>> = _messages.asStateFlow()

  private val _isTyping = MutableStateFlow(false)
  val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

  // Waiting text for the current turn, chosen by the context selector so it
  // describes what the turn is actually doing. Empty = no turn in flight.
  private val _typingLabel = MutableStateFlow("")
  val typingLabel: StateFlow<String> = _typingLabel.asStateFlow()

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

  // A draft is a new conversation that does NOT exist in the saved list yet:
  // it joins _conversations (and disk) only after the first successful AI
  // reply. Until then it behaves like a temporary chat — switching away
  // discards it — so empty "محادثة جديدة" rows never clutter the list.
  private var draftConversation: ChatConversation? = null

  private var historyLoaded = false
  private var lastConsumedInitialPrompt: String? = null

  // A currency proposal the assistant resolved and is waiting for the user
  // to confirm. In-memory only, never persisted: switching conversations
  // discards it, and "apply" acts on it only while it exists, so a bare
  // model claim can never change the setting on its own.
  private var pendingCurrencyProposal: CurrencyProposal? = null

  // True after the app asked the user to name their country (IP lookup
  // failed, or the currency could not be determined from it). While true,
  // a country-like answer is routed to the currency flow instead of being
  // classified as general chat, so the model keeps the currency protocol
  // and returns a proposal instead of merely claiming success in prose.
  private var awaitingCountryName = false

  private fun clearPendingCurrencyProposal() {
    pendingCurrencyProposal = null
    awaitingCountryName = false
  }

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
    // Drop saved conversations that never got a user message: they contain
    // only the welcome card (created by the old "new chat" behaviour).
    val pruned = saved.filter { conversation -> conversation.messages.any { it.isUser } }
    if (pruned.size != saved.size) {
      viewModelScope.launch(Dispatchers.IO) { ChatHistoryStore.save(pruned) }
    }
    if (pruned.isEmpty()) {
      enterDraft()
    } else {
      val sorted = sortConversations(pruned)
      val active = sorted.firstOrNull { it.messages.isNotEmpty() } ?: sorted.first()
      publish(sorted, active.id)
    }
  }

  /**
   * Starts a new conversation as a DRAFT: it is not added to the saved list
   * and not written to disk until the first successful AI reply (see
   * [promoteDraft]). Tapping "new chat" repeatedly no longer creates empty
   * rows in the drawer.
   */
  fun createConversation() {
    enterDraft()
  }

  private fun enterDraft() {
    discardTemporaryChat()
    discardDraft()
    clearPendingCurrencyProposal()
    val fresh = newConversation()
    draftConversation = fresh
    lastConsumedInitialPrompt = null
    _isTyping.value = false
    _activeConversation.value = fresh
    _messages.value = fresh.messages
  }

  private fun discardDraft() {
    draftConversation = null
  }

  private fun isDraftActive(): Boolean =
    draftConversation != null && draftConversation?.id == _activeConversation.value?.id

  /** Moves the active draft into the saved list after its first AI reply. */
  private fun promoteDraft() {
    val active = _activeConversation.value ?: return
    if (!isDraftActive()) return
    draftConversation = null
    publish(_conversations.value + active, active.id)
    persist()
  }

  /**
   * Starts a fresh temporary chat and switches to it. Any previous temporary
   * chat is discarded, unsaved, by design. The top-bar button that used to
   * clear the active conversation now calls this instead.
   */
  fun enterTemporaryChat() {
    discardDraft()
    clearPendingCurrencyProposal()
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
    // Leaving a temporary or draft chat discards it, unsaved.
    discardTemporaryChat()
    discardDraft()
    clearPendingCurrencyProposal()
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
    val draft = draftConversation
    if (draft != null && draft.id == conversationId) {
      val updatedDraft = transform(draft)
      draftConversation = updatedDraft
      if (_activeConversation.value?.id == conversationId) {
        _activeConversation.value = updatedDraft
      }
      return
    }
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
    clearPendingCurrencyProposal()
    if (_isTemporaryChat.value && temporaryConversation?.id == conversationId) {
      enterTemporaryChat()
      return
    }
    val remaining = _conversations.value.filterNot { it.id == conversationId }
    if (remaining.isEmpty()) {
      enterDraft()
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

    // The bill image attached to the assistant (if any) is sent with this message and shown in
    // the message bubble.
    val attachedImagePath = AssistantImageBridge.attachment.value?.filePath
    val userMessage = BillChatMessage(isUser = true, text = trimmed, imagePath = attachedImagePath)
    updateActiveMessages(cleanedMessages + userMessage, newUserPrompt = trimmed)
    if (!isDraftActive()) persist()

    // A short confirmation while a currency proposal is pending is applied
    // here, by the app itself, instead of depending on the model returning
    // the exact apply action: a model that merely claims the change in
    // words must never be the only thing standing between "yes" and the
    // setting actually changing. The applied currency is also added to the
    // user's currency list, so it shows up selected in Settings.
    val pendingProposal = pendingCurrencyProposal
    if (pendingProposal != null && ChatContextSelector.isConfirmationPrompt(trimmed)) {
      pendingCurrencyProposal = null
      CurrencyPreferenceStore.addCurrency(pendingProposal.currencyLabel, pendingProposal.symbol)
      CurrencyPreferenceStore.setCurrency(pendingProposal.symbol)
      postAssistantMessage(
        "تم تغيير العملة الافتراضية لفواتيرك إلى ${pendingProposal.currencyLabel} (${pendingProposal.symbol}). ستُستخدم في الفواتير الجديدة التي لا تحمل عملة محددة، وهي الآن موجودة في قائمة العملات بالإعدادات.",
      )
      if (!isDraftActive()) persist()
      return
    }

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
    if (!isDraftActive()) persist()
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
    // Pick the smallest context that can answer this prompt. A bill image
    // attached to the turn keeps full context: image questions may need
    // category context to classify what the photo shows.
    // A country-like answer while the app awaits a country name goes to
    // the currency flow: without the currency rules, the model would
    // answer a bare country name with a prose success claim and no
    // proposal action, so nothing would ever be applied.
    val isCountryAnswer =
      awaitingCountryName && ChatContextSelector.isCountryLikeAnswer(userPrompt)
    if (awaitingCountryName && !isCountryAnswer) {
      // The user moved on to something else; stop waiting for a country.
      awaitingCountryName = false
    }
    val selection =
      if (AssistantImageBridge.attachment.value != null) {
        ChatContextSelection(
          ChatContextMode.FULL,
          categories,
          allBills,
          allBills,
          "جارٍ تحليل بيانات الفواتير وإعداد الإجابة...",
        )
      } else if (isCountryAnswer) {
        ChatContextSelection(
          ChatContextMode.CURRENCY,
          emptyList(),
          emptyList(),
          emptyList(),
          "جارٍ تجهيز إعداد العملة...",
        )
      } else {
        ChatContextSelector.select(userPrompt, categories, allBills, pendingCurrencyProposal)
      }
    _isTyping.value = true
    _typingLabel.value = selection.typingLabel
    viewModelScope.launch {
      val result =
        chatService.sendChatTurn(
          conversationHistory = priorHistory,
          userPrompt = userPrompt,
          categories = selection.categories,
          allBills = selection.bills,
          analyticsSummary = analyticsSummary,
          preferredModel = _activeConversation.value?.modelId,
          thinkingLevel = _activeConversation.value?.thinkingLevel,
          pendingCurrencyProposal = pendingCurrencyProposal,
          contextMode = selection.mode,
          detailBills = selection.detailBills,
        )

      _isTyping.value = false
      _typingLabel.value = ""

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
          handleCurrencyAction(dto, categories, allBills, analyticsSummary)
          if (isDraftActive()) {
            promoteDraft()
          } else {
            persist()
          }
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

  /**
   * Executes a currency-setting action the model requested. The setting is
   * never changed on a bare model claim: "apply" only acts on a pending
   * proposal the user already confirmed, and every other action only
   * proposes or cancels. All messages posted here are local assistant
   * messages, not model turns — except the internal determination turn in
   * [executeCurrencyDeterminationTurn], whose model text is never shown.
   */
  private suspend fun handleCurrencyAction(
    dto: BillChatAiReplyDto,
    categories: List<BillCategory>,
    allBills: List<BillRecord>,
    analyticsSummary: MonthlyAnalyticsSummary,
  ) {
    when (dto.currencyAction) {
      "resolve_by_residence" -> {
        // A new resolve supersedes any older pending proposal.
        pendingCurrencyProposal = null
        awaitingCountryName = false
        IpLocationResolver.resolveCountryName()
          .onSuccess { countryDisplay ->
            executeCurrencyDeterminationTurn(
              countryDisplay = countryDisplay,
              categories = categories,
              allBills = allBills,
              analyticsSummary = analyticsSummary,
            )
          }
          .onFailure {
            postAssistantMessage(
              "تعذّر تحديد محل إقامتك من الشبكة حاليًا. أخبرني باسم الدولة التي تقيم فيها وسأضبط عملتها لك.",
            )
            awaitingCountryName = true
          }
      }
      "propose_currency" -> {
        val label = dto.currencyLabel.trim()
        val symbol = dto.currencySymbol.trim()
        if (label.isNotBlank() && symbol.isNotBlank()) {
          pendingCurrencyProposal = CurrencyProposal(currencyLabel = label, symbol = symbol)
          awaitingCountryName = false
          postAssistantMessage(
            "تم تحديد العملة المحلية: $label ($symbol). هل أغيّر العملة الافتراضية لفواتيرك إلى $symbol؟",
          )
        }
        // Blank label or symbol: ignore. A proposal without both parts is unusable.
      }
      "apply_currency_change" -> {
        val proposal = pendingCurrencyProposal
        if (proposal != null) {
          pendingCurrencyProposal = null
          CurrencyPreferenceStore.addCurrency(proposal.currencyLabel, proposal.symbol)
          CurrencyPreferenceStore.setCurrency(proposal.symbol)
          postAssistantMessage(
            "تم تغيير العملة الافتراضية لفواتيرك إلى ${proposal.currencyLabel} (${proposal.symbol}). ستُستخدم في الفواتير الجديدة التي لا تحمل عملة محددة، وهي الآن موجودة في قائمة العملات بالإعدادات.",
          )
        } else {
          // The model claimed a change the app never applied (no proposal
          // was pending): say so explicitly instead of leaving the claim
          // standing uncorrected.
          postAssistantMessage(
            "لم يتم تغيير العملة الافتراضية، لأنه لا يوجد اقتراح معلّق حاليًا. اطلب مني تحديد العملة من جديد وسأعرض عليك الاقتراح للتأكيد قبل أي تغيير.",
          )
        }
      }
      "cancel_currency_change" -> {
        pendingCurrencyProposal = null
        awaitingCountryName = false
      }
    }
  }

  /**
   * Internal follow-up turn, never shown as a user message: the app already
   * knows the location from the IP lookup, so it asks the model itself to
   * determine that location's currency and return it in structured fields,
   * with a guiding example of the required format. The model's reply text
   * is intentionally not posted; only the resulting confirmation question
   * (or a failure note) reaches the user.
   */
  private suspend fun executeCurrencyDeterminationTurn(
    countryDisplay: String,
    categories: List<BillCategory>,
    allBills: List<BillRecord>,
    analyticsSummary: MonthlyAnalyticsSummary,
  ) {
    _isTyping.value = true
    _typingLabel.value = "جارٍ تجهيز إعداد العملة..."
    val history = _messages.value.drop(1).map { it.isUser to it.text }
    val result =
      chatService.sendChatTurn(
        conversationHistory = history,
        userPrompt =
          "محل إقامة المستخدم حسب عنوان IP هو: $countryDisplay. حدّد العملة المحلية لهذه الدولة من معرفتك، واضبط currencyAction على \"propose_currency\"، واملأ currencyLabel باسم العملة والدولة وcurrencySymbol برمزها. مثال على الصيغة المطلوبة: (جنيه مصري ج.م). اجعل replyText جملة قصيرة فقط.",
        categories = emptyList(),
        allBills = emptyList(),
        analyticsSummary = analyticsSummary,
        preferredModel = _activeConversation.value?.modelId,
        thinkingLevel = _activeConversation.value?.thinkingLevel,
        contextMode = ChatContextMode.CURRENCY,
        detailBills = emptyList(),
      )
    _isTyping.value = false
    _typingLabel.value = ""
    result
      .onSuccess { dto ->
        if (
          dto.currencyAction == "propose_currency" &&
            dto.currencyLabel.isNotBlank() &&
            dto.currencySymbol.isNotBlank()
        ) {
          val label = dto.currencyLabel.trim()
          val symbol = dto.currencySymbol.trim()
          pendingCurrencyProposal = CurrencyProposal(currencyLabel = label, symbol = symbol)
          awaitingCountryName = false
          postAssistantMessage(
            "محل إقامتك حسب الشبكة هو $countryDisplay، والعملة المحلية هي $label ($symbol). هل أغيّر العملة الافتراضية لفواتيرك إلى $symbol؟",
          )
        } else {
          postAssistantMessage(
            "تعذّر تحديد العملة من محل إقامتك. أخبرني باسم الدولة التي تقيم فيها وسأضبط عملتها لك.",
          )
          awaitingCountryName = true
        }
      }
      .onFailure {
        postAssistantMessage(
          "تعذّر تحديد العملة من محل إقامتك. أخبرني باسم الدولة التي تقيم فيها وسأضبط عملتها لك.",
        )
      }
  }

  private fun postAssistantMessage(text: String) {
    updateActiveMessages(_messages.value + BillChatMessage(isUser = false, text = text))
  }

  fun clearConversation() {
    clearPendingCurrencyProposal()
    val active = _activeConversation.value
    if (active == null) {
      _messages.value = listOf(createWelcomeMessage())
      return
    }
    if (isDraftActive()) {
      val resetDraft = active.copy(messages = listOf(createWelcomeMessage()), updatedAtMillis = System.currentTimeMillis())
      draftConversation = resetDraft
      _activeConversation.value = resetDraft
      _messages.value = resetDraft.messages
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
    // temporary chat or a draft: leaving it through a saved-list action
    // discards it.
    if (activeId != null && list.none { it.id == activeId }) {
      discardTemporaryChat()
      discardDraft()
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
    if (isDraftActive()) {
      // Drafts are not in the saved list yet: keep them in memory only until
      // the first AI reply promotes them.
      var updatedDraft = active.copy(messages = newMessages, updatedAtMillis = System.currentTimeMillis())
      if (newUserPrompt != null && active.title == "محادثة جديدة") {
        updatedDraft = updatedDraft.copy(title = ChatHistoryStore.defaultTitle(newMessages))
      }
      draftConversation = updatedDraft
      _activeConversation.value = updatedDraft
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
