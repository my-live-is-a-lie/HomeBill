# PROJECT_CONTEXT — HomeBill (فواتيري)

> اقرأ هذا الملف أولاً ثم `WORK_STATE.md` ثم `CHANGELOG_HANDOFF.md`.
> كُتبت هذه الملفات في 2026-11-03 لتسليم العمل بين جلسات Claude. كل ما فيها مأخوذ من محادثة التطوير،
> وما لم أتحقق منه أشرت إليه صراحة بكلمة "غير متحقّق".

## ما هو التطبيق
تطبيق Android لإدارة فواتير المنزل (كهرباء، مياه، غاز، إنترنت، تسوق…) مع:
- أقسام لكل نوع فاتورة ومقارنة بالفواتير السابقة وتنبيهات ارتفاع.
- مسح الفاتورة بالكاميرا/المعرض واستخراج بياناتها بـ Gemini ثم نموذج مراجعة قبل الحفظ.
- إحصائيات وميزانية شهرية للمنزل.
- مساعد ذكي (محادثات مع Gemini عن الفواتير) بشكل شبيه بتطبيق Gemini.
- إدخال صوتي: إملاء في شريط المساعد وإضافة فاتورة بالصوت.

- المستودع: `https://github.com/my-live-is-a-lie/HomeBill` (الفرع الرئيسي `main` محمي).
- واجهة التطبيق عربية بالكامل ومن اليمين إلى اليسار (RTL). المستخدم مصري غالباً (العملة الظاهرة في لقطات الشاشة: ج.م).

## التقنيات
- Kotlin + Jetpack Compose + Material 3 (أيقونات `material-icons-extended` متاحة).
- Navigation Compose بمسارات `@Serializable` في `MainActivity.kt`:
  `DashboardRoute`, `ScannerRoute(preselectedCategoryId)`, `AnalyticsRoute`, `AssistantRoute(initialPrompt)`,
  `CategoryDetailRoute(categoryId)`, `SettingsRoute`.
- Firebase Auth + Firestore (معرّف قاعدة البيانات من `R.string.firestore_database_id`).
- Retrofit + OkHttp (بما فيه WebSocket) + kotlinx.serialization (+ `org.json` في مكان واحد).
- Coil للصور، CameraX وML Kit Document Scanner للمسح، accompanist-permissions.
- Gemini عبر **مفتاح المستخدم نفسه** المحفوظ في `UserGeminiKeyStore` (يُدخل من تبويب الإعدادات).
  لا تضع مفتاحاً في الكود.
- بناء release مفعّل فيه R8 (`isMinifyEnabled = true`).

## هيكل الكود (`app/src/main/java/com/example/`)
- `MainActivity.kt` — المصادقة، التنقّل، ربط الـ ViewModels بالشاشات (ومنها `openVoiceBill`).
- `data/model` — `BillRecord`, `BillCategory`, `UtilityType`, `UiState`, …
- `data/remote` — `GeminiApiClient` (استخراج الصورة + التصنيف، قائمة `SUPPORTED_VISION_MODELS`)،
  `GeminiBillScanner` (تحويل النتيجة إلى `ScannedBillDraft`)، `GeminiBillTextScanner` (نص منطوق → مسودة فاتورة)،
  `UserGeminiKeyStore`.
- `data/repository/BillRepository` — Firestore.
- `domain` — محركات التحليل والبحث والميزانية.
- `ui/viewmodel` — `HomeBillsViewModel` (الفواتير، حالة المسح `ScanUiState`) و`BillChatViewModel` (محادثات المساعد).
- `ui/screens` — `DashboardScreen`, `ScannerScreen` (ونموذج المراجعة `ExtractedSheetReviewCard`),
  `BillAssistantScreen`, `AnalyticsScreen`, `CategoryDetailScreen`, `SettingsScreen`.
- `ui/components` — `AssistantIcons` (أيقونتا الفلتر والشريط الجانبي)، `Dictation` + `VoiceDictation` (المستمع المدمج
  في أندرويد) + `GeminiLiveDictation` (تفريغ Gemini المباشر)، `VoiceBillEntrySheet` + `VoiceRecordingBar` + `VoiceWaveform`.
- `util` — `DateAndFormatUtils`, `BillImageStore`, `AssistantImageBridge`, …

## التدفقات المهمة
1. **مسح فاتورة:** `ScannerScreen` → `HomeBillsViewModel.scanBillImage/scanCapturedBitmap` → `ScanUiState.Extracted`
   → نموذج المراجعة → `saveScannedBillToCategory` (يختار/ينشئ القسم ثم `repository.createBill`).
2. **فاتورة بالصوت:** نص منطوق → `HomeBillsViewModel.scanBillText` → `GeminiBillTextScanner.scanBillText`
   (نفس مخطط JSON المستخدم للصورة) → `ScanUiState.Extracted(imageUri = Uri.EMPTY, fromVoice = true)` → نفس نموذج
   المراجعة (يعرض الجملة المسموعة بدل الصورة). الحفظ يكون بـ `sourceType = "voice_ai"` (المسح: `"camera_ai"`).
   نقاط البدء: زر عائم "إضافة بالصوت" في `DashboardScreen`، وزر "سجّلها كفاتورة" بعد الإملاء في `BillAssistantScreen`.
3. **الإملاء في شريط المساعد:** ميكروفون → `GeminiLiveDictation` إن وُجد مفتاح Gemini، وإلا `VoiceDictation`
   (المدمج)، وإلا شاشة النظام. شريط التسجيل: ✕ يلغي ويحذف ما أُملي، ✓ يثبّت النص.
4. **المحادثة المؤقتة:** زر القلم المتقطع في أعلى المساعد يفتح صفحة "محادثة عابرة؟" (بلا حفظ)، ويتحول إلى ✕ داخلها.

## المقارنة بالمستخدم: تفضيلات وقواعد العمل
- الرد بالعربية، قصير ومباشر (شاشة هاتف)، بدون جداول طويلة.
- المستخدم **لا يعدّل الكود بنفسه**: أرسل دائماً الملف الكامل المعدّل وليس مقتطفاتً. على GitHub يعني ذلك
  دفع الملف كاملاً (`push_files` / `create_or_update_file`) — لا توجد أداة ترقيع (patch).
- **لا تعمل على `main`** (محمي): فرع جديد من `main` → PR. المستخدم هو من يدمج ويحذف الفروع.
  `create_pull_request` تعرض نموذجاً يضغط عليه المستخدم Submit — لا تقل إن الطلب أُنشئ قبل أن يتأكد.
- استمرّ من النسخة الحالية للملف على `main` (اقرأه أولاً) قبل إعادة كتابته: قد تكون جلسات أخرى عدّلته.
- Claude **لا يستطيع بناء التطبيق أو تشغيله** هنا. المستخدم يشغّل "بناء التحقق" على الفرع ويرسل سطر الخطأ
  إن فشل. صرّح دائماً أنك لم تجرّب الكود.
- اختبار أشياء لا تُجرّب بدون مفتاح Gemini أو بدون جهاز حقيقي: اطلب من المستخدم التجربة.
- أداة البحث عن الكود (`search_code`) أعادت نتائج فارغة في هذه الجلسة — اقرأ الملفات مباشرة.
- ملاحظة عن الذاكرة: في المحادثات التي تستخدم موصّل GitHub لا يمكن كتابة شيء إلى ذاكرة Claude — لذلك هذه
  الملفات هي وسيلة التسليم الوحيدة.

## مطبات تقنية تعلّمناها (تجنّب تكرارها)
- Compose: نداء `AnimatedVisibility` داخل `Box` متضمّن في `Column` يختار نسخة `ColumnScope` ويفشل بخطأ
  "cannot be called in this context with an implicit receiver" → استخدم الاسم الكامل `androidx.compose.animation.AnimatedVisibility`.
- RTL: أول عنصر في `Row` يظهر على اليمين.
- GitHub API: لا يمكن دمج `main` داخل فرع قديم عبر الأدوات؛ إن ظهر تعارض (إعادة كتابة الملف على نفس الفرع لا تحله)
  فانشئ فرعاً جديداً من `main` وأعد تطبيق التعديل عليه وافتح PR جديداً.
- Gemini Live للتفريغ: النموذج `models/gemini-3.5-transcribe-live` عبر WebSocket (`BidiGenerateContent`) والمفتاح في الرابط؛
  ردود الخادم تأتي في إطارات ثنائية (binary) بمحتوى JSON. الجلسة تنتهي بعد 10 دقائق (الكود يعيد الاتصال تلقائياً).
  تحقق من الوثائق الحالية عند أي تعديل: https://ai.google.dev/gemini-api/docs/live-api/live-transcribe
