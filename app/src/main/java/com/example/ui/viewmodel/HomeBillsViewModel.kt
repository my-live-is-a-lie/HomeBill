package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.CurrencyPreferenceStore
import com.example.data.model.BillCategory
import com.example.data.model.BillRecord
import com.example.data.model.BillSheetRow
import com.example.data.model.BillTemplates
import com.example.data.model.HouseholdBudget
import com.example.data.model.TemplateModes
import com.example.data.model.UiState
import com.example.data.model.UtilityType
import com.example.data.remote.GeminiBillScanner
import com.example.data.remote.GeminiBillTextScanner
import com.example.data.remote.ScannedBillDraft
import com.example.data.repository.BillRepository
import com.example.domain.BillAnalyticsEngine
import com.example.domain.BillSearchEngine
import com.example.domain.BillSearchScope
import com.example.domain.BudgetDashboardState
import com.example.domain.MonthlyAnalyticsSummary
import com.example.domain.MonthlyBudgetDashboardEngine
import com.example.util.BillImageStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScanUiState {
  data object Idle : ScanUiState

  data class Scanning(val imageUri: Uri) : ScanUiState

  data class Extracted(
    val imageUri: Uri,
    val draft: ScannedBillDraft,
    val preselectedCategoryId: String?,
    // True when the draft came from a dictated sentence instead of a picture of a bill.
    val fromVoice: Boolean = false,
  ) : ScanUiState

  data class Error(val message: String, val lastUri: Uri? = null) : ScanUiState
}

class HomeBillsViewModel(
  private val repository: BillRepository,
  private val scanner: GeminiBillScanner,
  val currentUserId: String,
  private val appContext: Context? = null,
  private val textScanner: GeminiBillTextScanner = GeminiBillTextScanner(scanner),
) : ViewModel() {

  val categoriesState: StateFlow<UiState<List<BillCategory>>> =
    repository
      .observeCategories(currentUserId)
      .map<List<BillCategory>, UiState<List<BillCategory>>> { UiState.Success(it) }
      .flowOn(Dispatchers.Default)
      .catch { error ->
        Log.w(TAG, "Error observing categories", error)
        emit(UiState.Error(error.localizedMessage ?: "تعذر تحميل أقسام الفواتير"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val allBillsState: StateFlow<UiState<List<BillRecord>>> =
    repository
      .observeAllBills(currentUserId)
      .map<List<BillRecord>, UiState<List<BillRecord>>> { UiState.Success(it) }
      .flowOn(Dispatchers.Default)
      .catch { error ->
        Log.w(TAG, "Error observing bills", error)
        emit(UiState.Error(error.localizedMessage ?: "تعذر تحميل سجل الفواتير"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val analyticsSummary: StateFlow<MonthlyAnalyticsSummary> =
    combine(categoriesState, allBillsState) { catState, billState ->
        val cats = (catState as? UiState.Success)?.data.orEmpty()
        val bills = (billState as? UiState.Success)?.data.orEmpty()
        BillAnalyticsEngine.computeMonthlyAnalytics(bills, cats)
      }
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = BillAnalyticsEngine.computeMonthlyAnalytics(emptyList(), emptyList()),
      )

  val householdBudgetState: StateFlow<HouseholdBudget?> =
    repository
      .observeHouseholdBudget(currentUserId)
      .flowOn(Dispatchers.Default)
      .catch { error ->
        Log.w(TAG, "Error observing household budget", error)
        emit(null)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null,
      )

  private val _selectedDashboardMonth = MutableStateFlow<String?>(null)
  val selectedDashboardMonth: StateFlow<String?> = _selectedDashboardMonth.asStateFlow()

  val budgetDashboardState: StateFlow<BudgetDashboardState> =
    combine(
        allBillsState,
        categoriesState,
        householdBudgetState,
        _selectedDashboardMonth,
      ) { billState, catState, budget, requestedMonth ->
        val bills = (billState as? UiState.Success)?.data.orEmpty()
        val cats = (catState as? UiState.Success)?.data.orEmpty()
        MonthlyBudgetDashboardEngine.compute(
          allBills = bills,
          categories = cats,
          householdBudget = budget,
          requestedMonth = requestedMonth,
        )
      }
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue =
          MonthlyBudgetDashboardEngine.compute(
            allBills = emptyList(),
            categories = emptyList(),
            householdBudget = null,
          ),
      )

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _searchScope = MutableStateFlow(BillSearchScope.ALL)
  val searchScope: StateFlow<BillSearchScope> = _searchScope.asStateFlow()

  val filteredBillsState: StateFlow<UiState<List<BillRecord>>> =
    combine(allBillsState, categoriesState, _searchQuery, _searchScope) {
        billState,
        catState,
        query,
        scope ->
        when (billState) {
          is UiState.Loading -> UiState.Loading
          is UiState.Error -> UiState.Error(billState.message)
          is UiState.Success -> {
            val categoriesById =
              (catState as? UiState.Success)?.data.orEmpty().associateBy { it.id }
            val filtered =
              BillSearchEngine.filterBills(
                bills = billState.data,
                query = query,
                scope = scope,
                categoriesById = categoriesById,
              )
            UiState.Success(filtered)
          }
        }
      }
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
  private var scanJob: Job? = null
  val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

  private val _isSaving = MutableStateFlow(false)
  val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
  private val _isSavingScannedBill = MutableStateFlow(false)
  val isSavingScannedBill: StateFlow<Boolean> = _isSavingScannedBill.asStateFlow()

  private val _isReclassifying = MutableStateFlow(false)
  val isReclassifying: StateFlow<Boolean> = _isReclassifying.asStateFlow()

  private val _snackbarMessage = MutableStateFlow<String?>(null)
  val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun updateSearchScope(scope: BillSearchScope) {
    _searchScope.value = scope
  }

  fun clearSearch() {
    _searchQuery.value = ""
    _searchScope.value = BillSearchScope.ALL
  }

  fun clearSnackbar() {
    _snackbarMessage.value = null
  }

  fun cancelScan() {
    scanJob?.cancel()
    scanJob = null
    _scanState.value = ScanUiState.Idle
  }

  fun resetScanState() {
    cancelScan()
  }

  fun selectDashboardMonth(yearMonth: String?) {
    _selectedDashboardMonth.value = yearMonth
  }

  fun saveHouseholdBudget(
    monthlyLimit: Double,
    currency: String = "ر.س",
    warningPercent: Double = 80.0,
  ) {
    viewModelScope.launch {
      _isSaving.value = true
      val res =
        repository.saveHouseholdBudget(
          monthlyLimit = monthlyLimit,
          currency = currency,
          warningPercent = warningPercent,
        )
      _isSaving.value = false
      res
        .onSuccess {
          _snackbarMessage.value =
            if (monthlyLimit > 0.0) {
              "تم حفظ سقف ميزانية المنزل الشهرية بنجاح"
            } else {
              "تم إلغاء سقف الميزانية الشهرية"
            }
        }
        .onFailure { err ->
          _snackbarMessage.value = err.localizedMessage ?: "تعذر حفظ إعدادات ميزانية المنزل"
        }
    }
  }

  fun createCategory(
    name: String,
    utilityType: UtilityType,
    defaultUnit: String,
    alertThresholdPercent: Double,
    trackDuration: Boolean = false,
    onCreated: (BillCategory) -> Unit = {},
  ) {
    // Callers that do not choose a template get the ready-made template of the utility type.
    createCategoryWithTemplate(
      name = name,
      utilityType = utilityType,
      defaultUnit = defaultUnit,
      alertThresholdPercent = alertThresholdPercent,
      trackDuration = trackDuration,
      templateMode = TemplateModes.PRESET,
      onCreated = onCreated,
    )
  }

  /** templateMode is TemplateModes.PRESET (ready-made rows) or TemplateModes.CUSTOM (no template). */
  fun createCategoryWithTemplate(
    name: String,
    utilityType: UtilityType,
    defaultUnit: String,
    alertThresholdPercent: Double,
    trackDuration: Boolean,
    templateMode: String,
    onCreated: (BillCategory) -> Unit = {},
  ) {
    viewModelScope.launch {
      _isSaving.value = true
      val mode =
        TemplateModes.normalize(templateMode).takeIf { it != TemplateModes.LEGACY }
          ?: TemplateModes.PRESET
      // A new template section starts with the extra rows already added in the user's other
      // sections of the same utility type. A custom section starts clean.
      val seedRows =
        if (mode == TemplateModes.PRESET) {
          BillTemplates.seedRowsFor(
            utilityType,
            (categoriesState.value as? UiState.Success)?.data.orEmpty(),
          )
        } else {
          emptyList()
        }
      val res =
        repository.createCategory(
          name,
          utilityType,
          defaultUnit,
          alertThresholdPercent,
          trackDuration,
          mode,
          seedRows,
        )
      _isSaving.value = false
      res
        .onSuccess { cat ->
          _snackbarMessage.value = "تمت إضافة قسم «${cat.name}» بنجاح"
          onCreated(cat)
        }
        .onFailure { err ->
          _snackbarMessage.value = err.localizedMessage ?: "تعذر إنشاء قسم الفاتورة"
        }
    }
  }

  fun updateCategory(category: BillCategory, onComplete: () -> Unit = {}) {
    viewModelScope.launch {
      _isSaving.value = true
      val res = repository.updateCategory(category)
      _isSaving.value = false
      res
        .onSuccess {
          _snackbarMessage.value = "تم تحديث إعدادات القسم"
          onComplete()
        }
        .onFailure { err ->
          _snackbarMessage.value = err.localizedMessage ?: "تعذر تحديث القسم"
        }
    }
  }

  // Records the moment consumption of a bill starts. Silent on success: the bill sheet swaps
  // its start button for the end button as soon as the updated bill flows back.
  fun markBillConsumptionStarted(bill: BillRecord) {
    viewModelScope.launch {
      _isSaving.value = true
      val res = repository.updateBill(bill.copy(startedAtEpochMillis = System.currentTimeMillis()))
      _isSaving.value = false
      res.onFailure { err ->
        _snackbarMessage.value = err.localizedMessage ?: "تعذر تسجيل بدء الاستهلاك"
      }
    }
  }

  // Records the moment consumption of a bill ends. Silent on success: the caller shows its own
  // confirmation snackbar with the undo action, so no second message is posted here.
  fun markBillConsumptionEnded(bill: BillRecord) {
    viewModelScope.launch {
      _isSaving.value = true
      val res = repository.updateBill(bill.copy(endedAtEpochMillis = System.currentTimeMillis()))
      _isSaving.value = false
      res.onFailure { err ->
        _snackbarMessage.value = err.localizedMessage ?: "تعذر تسجيل انتهاء الاستهلاك"
      }
    }
  }

  // Reverts an end recording (the snackbar undo action). The bill returns to the
  // started-but-not-ended state and the end button appears again.
  fun undoBillConsumptionEnded(bill: BillRecord) {
    viewModelScope.launch {
      _isSaving.value = true
      val res = repository.updateBill(bill.copy(endedAtEpochMillis = null))
      _isSaving.value = false
      res.onFailure { err ->
        _snackbarMessage.value = err.localizedMessage ?: "تعذر التراجع عن إنهاء الاستهلاك"
      }
    }
  }

  fun deleteCategory(categoryId: String, onDeleted: () -> Unit = {}) {
    viewModelScope.launch {
      _isSaving.value = true
      // Remember the bills of this category so their attached images can be cleaned up too.
      val billIdsToCleanUp =
        (allBillsState.value as? UiState.Success)
          ?.data
          .orEmpty()
          .filter { it.categoryId == categoryId }
          .map { it.id }
      val res = repository.deleteCategoryAndBills(categoryId)
      _isSaving.value = false
      res
        .onSuccess {
          val ctx = appContext
          if (ctx != null && billIdsToCleanUp.isNotEmpty()) {
            withContext(Dispatchers.IO) {
              billIdsToCleanUp.forEach { BillImageStore.deleteForBill(ctx, it) }
            }
          }
          _snackbarMessage.value = "تم حذف قسم الفاتورة وسجلاته"
          onDeleted()
        }
        .onFailure { err ->
          _snackbarMessage.value = err.localizedMessage ?: "تعذر حذف القسم"
        }
    }
  }

  fun saveBill(
    bill: BillRecord,
    categoryThresholdPercent: Double,
    existingCategoryBills: List<BillRecord>,
    onSaved: (String) -> Unit = {},
  ) {
    viewModelScope.launch {
      _isSaving.value = true
      val draftComparison =
        BillAnalyticsEngine.previewDraftComparison(
          draftBill = bill,
          existingCategoryBills = existingCategoryBills,
          thresholdPercent = categoryThresholdPercent,
        )

      val result =
        if (bill.id.isBlank()) {
          repository.createBill(bill)
        } else {
          repository.updateBill(bill).map { bill.id }
        }
      _isSaving.value = false

      result
        .onSuccess { savedId ->
          // Remember rows added to this bill with the section, and dismiss suggested rows the
          // user dropped. Only a new bill had suggestions seeded into its form, so only a new
          // bill can dismiss anything. A failure here must never affect the bill that was just
          // saved, so it is only logged.
          val savedCategory =
            (categoriesState.value as? UiState.Success)?.data.orEmpty().firstOrNull {
              it.id == bill.categoryId
            }
          val seededRows =
            if (bill.id.isBlank() && savedCategory != null) {
              BillTemplates.suggestedRows(savedCategory)
            } else {
              emptyList()
            }
          val previousLabelKeys =
            existingCategoryBills
              .firstOrNull { it.id == bill.id }
              ?.sheetItems
              .orEmpty()
              .map { BillTemplates.normalizeLabel(it.label) }
              .toSet()
          repository
            .rememberSheetRows(bill.categoryId, bill.sheetItems, seededRows, previousLabelKeys)
            .onFailure {
              Log.w(TAG, "Could not remember bill rows for category ${bill.categoryId}", it)
            }
          if (draftComparison.hasSpike) {
            val pct =
              draftComparison.amountChangePercent ?: draftComparison.consumptionChangePercent ?: 0.0
            _snackbarMessage.value =
              "تنبيه: تم حفظ الفاتورة، وتوجد زيادة مرتفعة بنسبة +${String.format(java.util.Locale.US, "%.1f", pct)}% مقارنة بالسابقة!"
          } else {
            _snackbarMessage.value = "تم حفظ الفاتورة وجدول التفاصيل بنجاح"
          }
          onSaved(savedId)
        }
        .onFailure { err ->
          _snackbarMessage.value = err.localizedMessage ?: "تعذر حفظ بيانات الفاتورة"
        }
    }
  }

  fun deleteBill(billId: String) {
    viewModelScope.launch {
      repository
        .deleteBill(billId)
        .onSuccess {
          val ctx = appContext
          if (ctx != null) {
            withContext(Dispatchers.IO) { BillImageStore.deleteForBill(ctx, billId) }
          }
          _snackbarMessage.value = "تم حذف الفاتورة"
        }
        .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "تعذر حذف الفاتورة" }
    }
  }

  fun scanBillImage(context: Context, imageUri: Uri, preselectedCategoryId: String? = null) {
    scanJob?.cancel()
    scanJob = viewModelScope.launch {
      _scanState.value = ScanUiState.Scanning(imageUri)
      val result =
        scanner.scanBillImage(
          context.applicationContext,
          imageUri,
          CurrencyPreferenceStore.getCurrency(),
        )
      if (!isActive) return@launch
      result
        .onSuccess { draft ->
          _scanState.value =
            ScanUiState.Extracted(
              imageUri = imageUri,
              draft = draft,
              preselectedCategoryId = preselectedCategoryId,
            )
        }
        .onFailure { err ->
          _scanState.value =
            ScanUiState.Error(
              message = err.localizedMessage ?: "تعذر استخراج بيانات الفاتورة من الصورة.",
              lastUri = imageUri,
            )
        }
    }
  }

  fun scanCapturedBitmap(
    bitmap: Bitmap,
    previewUri: Uri = Uri.EMPTY,
    preselectedCategoryId: String? = null,
  ) {
    scanJob?.cancel()
    scanJob = viewModelScope.launch {
      _scanState.value = ScanUiState.Scanning(previewUri)
      val result = scanner.scanBillBitmap(bitmap, CurrencyPreferenceStore.getCurrency())
      if (!isActive) return@launch
      result
        .onSuccess { draft ->
          _scanState.value =
            ScanUiState.Extracted(
              imageUri = previewUri,
              draft = draft,
              preselectedCategoryId = preselectedCategoryId,
            )
        }
        .onFailure { err ->
          _scanState.value =
            ScanUiState.Error(
              message = err.localizedMessage ?: "تعذر استخراج بيانات الفاتورة من الصورة.",
              lastUri = previewUri.takeIf { it != Uri.EMPTY },
            )
        }
    }
  }

  /**
   * Turns a dictated sentence ("دفعت فاتورة الكهرباء ٣٥٠ جنيه اليوم") into a bill draft and shows it in
   * the same review form as a scanned bill; nothing is saved until the user confirms there.
   */
  fun scanBillText(spokenText: String) {
    scanJob?.cancel()
    scanJob = viewModelScope.launch {
      _scanState.value = ScanUiState.Scanning(Uri.EMPTY)
      val categoryNames = (categoriesState.value as? UiState.Success)?.data.orEmpty().map { it.name }
      val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
      val currency = CurrencyPreferenceStore.getCurrency()
      val result =
        textScanner.scanBillText(
          spokenText = spokenText,
          todayIso = todayIso,
          defaultCurrency = currency,
          categoryNames = categoryNames,
        )
      if (!isActive) return@launch
      result
        .onSuccess { draft ->
          _scanState.value =
            ScanUiState.Extracted(
              imageUri = Uri.EMPTY,
              draft = draft,
              preselectedCategoryId = null,
              fromVoice = true,
            )
        }
        .onFailure { err ->
          _scanState.value =
            ScanUiState.Error(
              message = err.localizedMessage ?: "تعذر تحويل الجملة المنطوقة إلى فاتورة.",
              lastUri = null,
            )
        }
    }
  }

  fun updateScannedDraft(updated: ScannedBillDraft) {
    val current = _scanState.value
    if (current is ScanUiState.Extracted) {
      _scanState.value = current.copy(draft = updated)
    }
  }

  fun reclassifyScannedDraft() {
    val current = _scanState.value as? ScanUiState.Extracted ?: return
    if (_isReclassifying.value) return
    viewModelScope.launch {
      _isReclassifying.value = true
      scanner
        .classifyDraftExpense(current.draft)
        .onSuccess { reclassified ->
          val latest = _scanState.value
          if (latest is ScanUiState.Extracted) {
            _scanState.value = latest.copy(draft = reclassified)
          }
          _snackbarMessage.value =
            "تم تصنيف المصروف تلقائياً: ${reclassified.suggestedCategoryName}"
        }
        .onFailure { err ->
          _snackbarMessage.value =
            err.localizedMessage ?: "تعذر إعادة تصنيف نوع المصروف عبر Gemini حالياً."
        }
      _isReclassifying.value = false
    }
  }

  fun saveScannedBillToCategory(
    draft: ScannedBillDraft,
    selectedCategoryId: String?,
    onSuccessNavigateToCategory: (String) -> Unit,
  ) {
    // The scanned (cropped/filtered) image that will be attached to the saved bill. A bill that
    // came from a dictated sentence has no image.
    val extractedNow = _scanState.value as? ScanUiState.Extracted
    val scannedImageUri: Uri? = extractedNow?.imageUri
    val isVoiceEntry = extractedNow?.fromVoice == true

    viewModelScope.launch {
      _isSaving.value = true
      _isSavingScannedBill.value = true
      val existingCategories = (categoriesState.value as? UiState.Success)?.data.orEmpty()
      val existingBills = (allBillsState.value as? UiState.Success)?.data.orEmpty()

      // Resolve target category: explicit selection -> matching utilityType -> auto-create category
      val matchedCategory: BillCategory? =
        when {
          !selectedCategoryId.isNullOrBlank() ->
            existingCategories.firstOrNull { it.id == selectedCategoryId }
          else ->
            existingCategories.firstOrNull {
              it.utilityType == draft.detectedUtilityType.firestoreKey
            }
              ?: existingCategories.firstOrNull {
                it.name.trim().equals(draft.suggestedCategoryName.trim(), ignoreCase = true)
              }
        }

      val targetCategoryResult: Result<BillCategory> =
        if (matchedCategory != null) {
          Result.success(matchedCategory)
        } else {
          repository.createCategory(
            name = draft.suggestedCategoryName,
            utilityType = draft.detectedUtilityType,
            defaultUnit = draft.consumptionUnit.ifBlank { draft.detectedUtilityType.defaultUnit },
            alertThresholdPercent = 25.0,
          )
        }

      targetCategoryResult
        .onSuccess { targetCat ->
          val catBills = existingBills.filter { it.categoryId == targetCat.id }
          val newBill =
            BillRecord(
              userId = currentUserId,
              categoryId = targetCat.id,
              categoryName = targetCat.name,
              utilityType = targetCat.utilityType,
              billDateIso = draft.billDateIso,
              billingMonth = draft.billingMonth,
              billDateEpochMillis = draft.billDateEpochMillis,
              totalAmount = draft.totalAmount,
              currency = draft.currency,
              consumptionValue = draft.consumptionValue,
              consumptionUnit = draft.consumptionUnit.ifBlank { targetCat.defaultUnit },
              previousReading = draft.previousReading,
              currentReading = draft.currentReading,
              invoiceNumber = draft.invoiceNumber,
              providerName = draft.storeName.ifBlank { draft.providerName },
              rawExtractedText = draft.rawExtractedText,
              notes = "",
              sourceType = if (isVoiceEntry) "voice_ai" else "camera_ai",
              sheetItems =
                draft.sheetRows.ifEmpty {
                  listOf(
                    BillSheetRow(
                      label = "إجمالي الفاتورة",
                      value = draft.totalAmount.toString(),
                      unit = draft.currency,
                    )
                  )
                },
            )

          val comparison =
            BillAnalyticsEngine.previewDraftComparison(
              draftBill = newBill,
              existingCategoryBills = catBills,
              thresholdPercent = targetCat.alertThresholdPercent,
            )

          repository
            .createBill(newBill)
            .onSuccess { savedBillId ->
              // Attach the scanned image to the new bill (stored on the phone, keyed by bill id).
              val ctx = appContext
              if (ctx != null && scannedImageUri != null && !isVoiceEntry) {
                withContext(Dispatchers.IO) {
                  BillImageStore.saveForBill(ctx, scannedImageUri, savedBillId)
                }
              }
              _isSaving.value = false
          _isSavingScannedBill.value = false
              _isSavingScannedBill.value = false
              _isSavingScannedBill.value = false
              _scanState.value = ScanUiState.Idle
              if (comparison.hasSpike) {
                val pct =
                  comparison.amountChangePercent ?: comparison.consumptionChangePercent ?: 0.0
                _snackbarMessage.value =
                  "تنبيه تفاوت! أضيفت الفاتورة إلى «${targetCat.name}» مع ارتفاع +${String.format(java.util.Locale.US, "%.1f", pct)}% عن السابقة."
              } else {
                _snackbarMessage.value =
                  "تم إنشاء الـ Sheet وإضافة الفاتورة إلى «${targetCat.name}» بنجاح!"
              }
              onSuccessNavigateToCategory(targetCat.id)
            }
            .onFailure { err ->
              _isSaving.value = false
              _snackbarMessage.value = err.localizedMessage ?: "تعذر حفظ الفاتورة المستخرجة"
            }
        }
        .onFailure { err ->
          _isSaving.value = false
          _snackbarMessage.value = err.localizedMessage ?: "تعذر تجهيز قسم الفاتورة"
        }
    }
  }

  companion object {
    private const val TAG = "HomeBillsVM"
    private const val STOP_TIMEOUT_MILLIS = 5000L

    fun factory(currentUserId: String) = viewModelFactory {
      initializer {
        val app =
          checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing from CreationExtras" }
        HomeBillsViewModel(
          repository = BillRepository(app.applicationContext),
          scanner = GeminiBillScanner(),
          currentUserId = currentUserId,
          appContext = app.applicationContext,
        )
      }
    }
  }
}
