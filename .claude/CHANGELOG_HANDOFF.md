# CHANGELOG_HANDOFF — ما حدث في هذه المحادثة (بترتيب زمني)

كل بند يذكر الفرع والـ commits المعروفة والملفات الملموسة. أرقام PR التي لم أتأكد منها لم أذكرها.

## 1) أيقونات المساعد + صفحة المحادثة المؤقتة
- الفرع `feature/icons-temp-chat` (commit `57b2f86`) → دُمج في `main`.
- ملف جديد: `ui/components/AssistantIcons.kt` (أيقونة `Sidebar` و`Filter` كـ ImageVector بخط خارجي).
- تعديل: `BillAssistantScreen.kt` (صفحة "محادثة عابرة؟"، زر ✕ بدل زر القلم داخل المحادثة المؤقتة، `TempChatIcon(size)`).
- نص الصفحة التوضيحي كتبته أنا (لا تذكر "72 ساعة" لأنها خاصة بخوادم Google).

## 2) تعارض دمج فرع التمرير (PR #4 ثم #5)
- فرع `feature/assistant-scroll-behavior` كان يتعارض مع `main` (نفس منطقة قائمة الرسائل). دمج التعديلين من داخل الفرع
  القديم (commit `a4faf9a`) **لم يحل التعارض** لأن تاريخه لا يحوي `main`.
- الحل: فرع جديد `feature/assistant-scroll-behavior-v2` من `main` (commits `6af0bb0` ثم إصلاح `85e20d2`) → PR #5 دُمج
  (دمج `37ca573`). PR #4 كان يجب إغلاقه.
- خطأ بناء كان في v2: `AnimatedVisibility` داخل `Box` (الحل في `PROJECT_CONTEXT.md` → مطبات).
- الميزة: شريط الاقتراحات يُطوى عند النزول (NestedScrollConnection) وسهم النزول لآخر المحادثة.

## 3) الميكروفون والإملاء (ثلاث مراحل)
- **أ) نافذة النظام:** `feature/voice-input-mic` (`4665970`): `RecognizerIntent` — تجربة المستخدم: تعمل.
- **ب) إملاء داخل التطبيق:** نفس الفرع (`7a473e7`): `SpeechRecognizer` مع نتائج جزئية، إعادة تشغيل بين الجمل،
  إذن `RECORD_AUDIO` + `<queries>` في المانيفست، الملف `VoiceDictation.kt`، زر "حذف النص المُملى". المشكلة: بطء عند الكلام السريع
  وضياع كلمات في فجوات إعادة التشغيل (سلوك من الخدمة نفسها).
- **ج) تفريغ Gemini المباشر:** `feature/voice-live-gemini` (`587e3a4` ثم `715d21d`): ملف جديد `GeminiLiveDictation.kt`، واجهة `Dictation`،
  شريط تسجيل (موجة + ✕ + ✓)، المحرك المدمج كبديل. تجربة المستخدم: "النتيجة جيدة ودقة التحويل ممتازة". دُمج في `main`
  (`6533b8e` هو رأس `main` قبل المرحلة التالية).

## 4) إضافة فاتورة بالصوت
- الفرع `feature/voice-bill-entry` → PR #7 → دُمج (`0023ec9`). commits كتبتها أنا: `16c4dcf`, `4207334`, `4c70c21`, `be2acc3`, `81411a9`.
- ملفات جديدة: `data/remote/GeminiBillTextScanner.kt`, `ui/components/VoiceBillEntrySheet.kt`
  (وفيه `VoiceRecordingBar` و`VoiceWaveform` المشتركتان بعد نقلهما من `BillAssistantScreen`).
- تعديل: `HomeBillsViewModel.kt` (`scanBillText`، `ScanUiState.Extracted.fromVoice`، مصدر الحفظ `voice_ai` وتجاهل الصورة)،
  `MainActivity.kt` (`openVoiceBill`)، `DashboardScreen.kt` (زر عائم + النافذة السفلية)، `ScannerScreen.kt` (إخفاء مساحة الصورة
  الفارغة وعرض الجملة المسموعة، زر "إلغاء" بدل "إعادة المسح"، إخفاء "النص المستخرج" للصوت)،
  `BillAssistantScreen.kt` (المعامل `onVoiceBillText` وزر "سجّلها كفاتورة").
- قرار المستخدم: خيار أ (تعبئة ثم تأكيد) ونقطتا بدء: شريط المساعد + زر مستقل في شاشة الفواتير.
- ملاحظة: الدمج شمل أيضاً commits لم أكتبها (انظر `WORK_STATE.md` → "غير متحقّق").

## 5) هذه الملفات
- فرع `docs/claude-handoff` يحمل `.claude/PROJECT_CONTEXT.md`, `.claude/WORK_STATE.md`, `.claude/CHANGELOG_HANDOFF.md`.
