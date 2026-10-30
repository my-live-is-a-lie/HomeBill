package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.remote.GeminiApiClient
import com.example.ui.theme.AccentPresets
import com.example.ui.theme.AccentRole
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemeSettingsStore
import com.example.ui.viewmodel.ChatImportResult
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.remote.UserGeminiKeyStore
import kotlinx.coroutines.launch

/**
 * Settings hub. Today it holds one entry — AI keys — so more settings
 * can be added here later as further list entries without restructuring.
 * The key-management UI itself lives on the AI-key page, reached by
 * tapping the AI entry below, not directly on the hub.
 */
@Composable
fun SettingsScreen(
  onExportChats: () -> String = { "" },
  onImportChats: (String) -> ChatImportResult = { ChatImportResult(0, 0) },
) {
  var showAiKeys by remember { mutableStateOf(false) }
  // The screen has no top app bar, so keep its content below the status bar.
  Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
    if (showAiKeys) {
      GeminiKeysScreen(onBack = { showAiKeys = false })
    } else {
      SettingsHubScreen(
        onOpenAiKeys = { showAiKeys = true },
        onExportChats = onExportChats,
        onImportChats = onImportChats,
      )
    }
  }
}

@Composable
private fun SettingsHubScreen(
  onOpenAiKeys: () -> Unit,
  onExportChats: () -> String,
  onImportChats: (String) -> ChatImportResult,
) {
  val context = LocalContext.current
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
      val result = onImportChats(json)
      val message =
        if (result.imported == 0 && result.skipped == 0) {
          "لم يتم استيراد أي محادثة — تأكد من أن الملف نسخة احتياطية صحيحة"
        } else {
          "تم استيراد ${result.imported} محادثة، وتخطي ${result.skipped} مكررة"
        }
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  val keys = UserGeminiKeyStore.getKeys(context)
  val names = UserGeminiKeyStore.getNames(context)
  val active = UserGeminiKeyStore.activeIndex(context)
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Title on the right (RTL start); the appearance button sits at the left end of the row.
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "الإعدادات",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.weight(1f),
      )
      ThemeMenuButton()
    }
    Card(onClick = onOpenAiKeys, modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
        Column(modifier = Modifier.weight(1f)) {
          Text(text = "الذكاء الاصطناعي", style = MaterialTheme.typography.titleMedium)
          Text(
            text = if (keys.isEmpty()) {
              "إدارة مفاتيح Gemini — لم يتم إضافة مفتاح بعد"
            } else {
              val activeName = names.getOrNull(active.coerceIn(0, keys.lastIndex)) ?: "مفتاح 1"
              "إدارة مفاتيح Gemini — ${keys.size} مفتاح، النشط: $activeName (${UserGeminiKeyStore.maskedKey(keys[active.coerceIn(0, keys.lastIndex)])})"
            },
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(text = "النسخ الاحتياطي للمحادثات", style = MaterialTheme.typography.titleMedium)
        Text(
          text = "صدّر جميع محادثاتك المحفوظة إلى ملف JSON، أو استعد نسخة احتياطية سابقة. المحادثات المؤقتة والمسودات التي لم تصلها ردود بعد لا تُصدَّر.",
          style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = {
              pendingExportJson = onExportChats()
              exportLauncher.launch("homebill-chats-backup.json")
            },
          ) {
            Icon(imageVector = Icons.Default.Download, contentDescription = null)
            Spacer(modifier = Modifier.size(6.dp))
            Text(text = "تصدير")
          }
          OutlinedButton(onClick = { importLauncher.launch("application/json") }) {
            Icon(imageVector = Icons.Default.Upload, contentDescription = null)
            Spacer(modifier = Modifier.size(6.dp))
            Text(text = "استيراد")
          }
        }
      }
    }
  }
}

/**
 * Appearance button (icon only). Opens a popup menu right under it with:
 *  1. the theme choices (تلقائي / فاتح / غامق / مظلم);
 *  2. a switch for dynamic colors taken from the phone's wallpaper (Android 12+);
 *  3. an accent color picker with a toggle to choose between the primary and the secondary accent.
 *     Both share the same palette, and each keeps its own saved color. The picker is disabled while
 *     dynamic colors are on, since they replace the whole color scheme.
 */
@Composable
private fun ThemeMenuButton() {
  val context = LocalContext.current
  var expanded by remember { mutableStateOf(false) }
  var role by remember { mutableStateOf(AccentRole.PRIMARY) }
  val mode = ThemeSettingsStore.mode
  val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  val dynamicOn = dynamicSupported && ThemeSettingsStore.dynamicColor
  // Show swatches in the tone the current theme actually uses.
  val isDarkNow = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val primaryPreset = AccentPresets.byId(ThemeSettingsStore.accentId, AccentRole.PRIMARY)
  val secondaryPreset =
    AccentPresets.byId(ThemeSettingsStore.secondaryAccentId, AccentRole.SECONDARY)
  val selectedId = if (role == AccentRole.PRIMARY) primaryPreset.id else secondaryPreset.id

  Box {
    IconButton(
      onClick = { expanded = true },
      modifier = Modifier.testTag("theme_menu_button"),
    ) {
      Icon(imageVector = Icons.Default.Palette, contentDescription = "المظهر")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      Column(
        modifier = Modifier.width(264.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "السمة",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          ThemeMode.values().forEach { option ->
            ThemeModeButton(
              mode = option,
              selected = option == mode,
              onClick = { ThemeSettingsStore.setMode(context, option) },
              modifier = Modifier.weight(1f),
            )
          }
        }

        if (dynamicSupported) {
          HorizontalDivider()
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .toggleable(
                  value = dynamicOn,
                  role = Role.Switch,
                  onValueChange = { ThemeSettingsStore.setDynamicColor(context, it) },
                )
                .padding(vertical = 4.dp)
                .testTag("dynamic_color_toggle"),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(text = "ألوان ديناميكية", style = MaterialTheme.typography.bodyMedium)
              Text(
                text = "مأخوذة من خلفية الهاتف",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(checked = dynamicOn, onCheckedChange = null)
          }
        }

        HorizontalDivider()
        Text(
          text = "لون التمييز",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Primary / secondary toggle; each button shows the color currently saved for its role.
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          AccentRoleButton(
            label = "الأساسي",
            dotColor = primaryPreset.swatch(isDarkNow),
            selected = role == AccentRole.PRIMARY,
            enabled = !dynamicOn,
            onClick = { role = AccentRole.PRIMARY },
            tag = "accent_role_primary",
            modifier = Modifier.weight(1f),
          )
          AccentRoleButton(
            label = "الثانوي",
            dotColor = secondaryPreset.swatch(isDarkNow),
            selected = role == AccentRole.SECONDARY,
            enabled = !dynamicOn,
            onClick = { role = AccentRole.SECONDARY },
            tag = "accent_role_secondary",
            modifier = Modifier.weight(1f),
          )
        }
        // One shared palette for both roles.
        AccentPresets.all.chunked(4).forEach { rowItems ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            rowItems.forEach { preset ->
              val swatch = preset.swatch(isDarkNow)
              val selected = preset.id == selectedId
              Box(
                contentAlignment = Alignment.Center,
                modifier =
                  Modifier.size(40.dp)
                    .alpha(if (dynamicOn) 0.35f else 1f)
                    .clip(CircleShape)
                    .background(swatch)
                    .then(
                      if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                      } else {
                        Modifier
                      }
                    )
                    .clickable(enabled = !dynamicOn) {
                      ThemeSettingsStore.setAccent(context, role, preset)
                    }
                    .semantics { contentDescription = "لون ${preset.nameAr}" }
                    .testTag("accent_${preset.id}"),
              ) {
                if (selected) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp),
                  )
                }
              }
            }
          }
        }
        if (dynamicOn) {
          Text(
            text = "غير متاح أثناء تفعيل الألوان الديناميكية",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun ThemeModeButton(
  mode: ThemeMode,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val (icon, label) =
    when (mode) {
      ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto to "تلقائي"
      ThemeMode.LIGHT -> Icons.Default.LightMode to "فاتح"
      ThemeMode.DARK -> Icons.Default.DarkMode to "غامق"
      ThemeMode.BLACK -> Icons.Default.Contrast to "مظلم"
    }
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceVariant,
    contentColor =
      if (selected) MaterialTheme.colorScheme.onPrimaryContainer
      else MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.testTag("theme_mode_${mode.key}"),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
      Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
      Spacer(modifier = Modifier.height(4.dp))
      Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
  }
}

/** One of the two buttons that pick which accent (primary / secondary) the swatches change. */
@Composable
private fun AccentRoleButton(
  label: String,
  dotColor: Color,
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  tag: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(14.dp),
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceVariant,
    contentColor =
      if (selected) MaterialTheme.colorScheme.onPrimaryContainer
      else MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.alpha(if (enabled) 1f else 0.5f).testTag(tag),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp),
    ) {
      Box(
        modifier =
          Modifier.size(14.dp)
            .clip(CircleShape)
            .background(dotColor)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
  }
}

@Composable
private fun GeminiKeysScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var keys by remember { mutableStateOf(UserGeminiKeyStore.getKeys(context)) }
  var names by remember { mutableStateOf(UserGeminiKeyStore.getNames(context)) }
  var activeIndex by remember { mutableStateOf(UserGeminiKeyStore.activeIndex(context)) }
  var inputText by remember { mutableStateOf("") }
  var inputName by remember { mutableStateOf("") }
  var showInputKey by remember { mutableStateOf(false) }
  var shownKeys by remember { mutableStateOf(setOf<Int>()) }
  var message by remember { mutableStateOf("") }
  var messageIsError by remember { mutableStateOf(false) }
  var testingIndex by remember { mutableStateOf(-1) }
  var testResults by remember { mutableStateOf(mapOf<Int, Pair<Boolean, String>>()) }
  var testingInput by remember { mutableStateOf(false) }
  var inputTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
  var editingIndex by remember { mutableStateOf(-1) }
  var editName by remember { mutableStateOf("") }
  var editKey by remember { mutableStateOf("") }
  var showEditKey by remember { mutableStateOf(false) }

  fun refresh() {
    keys = UserGeminiKeyStore.getKeys(context)
    names = UserGeminiKeyStore.getNames(context)
    activeIndex = UserGeminiKeyStore.activeIndex(context)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "عودة إلى الإعدادات")
      }
      Text(text = "مفاتيح Gemini", style = MaterialTheme.typography.titleLarge)
    }
    var showInfoDialog by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = "أضف مفتاح Gemini الخاص بك لاستخدام الذكاء الاصطناعي.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = { showInfoDialog = true }) {
        Icon(imageVector = Icons.Default.Info, contentDescription = "معلومات عن مفاتيح Gemini")
      }
    }
    if (showInfoDialog) {
      AlertDialog(
        onDismissRequest = { showInfoDialog = false },
        title = { Text("معلومات عن مفاتيح Gemini") },
        text = {
          Text(
            "المفاتيح تُحفظ على جهازك فقط، ولا تُرسل إلى خادم، ولا تُرفع إلى GitHub، " +
              "وحصتها من حسابك أنت في Google AI Studio. يمكنك إضافة أكثر من مفتاح " +
              "واختيار أي واحد يُستخدم حاليًا، ويمكنك اختبار المفتاح قبل إضافته، " +
              "وتعديل اسمه وقيمته بالقلم، ونسخه أو حذفه في أي وقت.",
          )
        },
        confirmButton = {
          TextButton(onClick = { showInfoDialog = false }) {
            Text("فهمت")
          }
        },
      )
    }
    OutlinedTextField(
      value = inputName,
      onValueChange = { inputName = it },
      label = { Text("اسم المفتاح (اختياري، مثل: مفتاحي الشخصي)") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    // In RTL, trailingIcon renders on the left side of the text field.
    OutlinedTextField(
      value = inputText,
      onValueChange = { inputText = it; message = ""; inputTestResult = null },
      label = { Text("مفتاح Gemini API جديد") },
      singleLine = true,
      visualTransformation = if (showInputKey) VisualTransformation.None else PasswordVisualTransformation(),
      trailingIcon = {
        IconButton(onClick = { showInputKey = !showInputKey }) {
          Icon(
            imageVector = if (showInputKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (showInputKey) "إخفاء المفتاح" else "إظهار المفتاح",
          )
        }
      },
      modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      Button(
        onClick = {
          val added = if (inputName.trim().isBlank()) {
            UserGeminiKeyStore.addKey(inputText, context)
          } else {
            UserGeminiKeyStore.addKey(inputText, inputName, context)
          }
          if (added) {
            inputText = ""
            inputName = ""
            inputTestResult = null
            message = "تمت إضافة المفتاح."
            messageIsError = false
          } else if (inputText.trim().isBlank()) {
            message = "أدخل مفتاحًا غير فارغ أولًا."
            messageIsError = true
          } else {
            message = "هذا المفتاح موجود بالفعل."
            messageIsError = true
          }
          refresh()
        },
        modifier = Modifier.weight(1f),
      ) {
        Text("إضافة مفتاح")
      }
      OutlinedButton(
        enabled = inputText.trim().isNotBlank() && !testingInput,
        onClick = {
          testingInput = true
          inputTestResult = null
          scope.launch {
            val result = GeminiApiClient().testApiKey(inputText)
            inputTestResult = result.isSuccess to (result.getOrNull() ?: result.exceptionOrNull()?.message ?: "فشل الاختبار.")
            testingInput = false
          }
        },
        modifier = Modifier.weight(1f),
      ) {
        Text(if (testingInput) "جارٍ الاختبار..." else "اختبار قبل الإضافة")
      }
    }
    inputTestResult?.let { (ok, text) ->
      Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
      )
    }
    OutlinedButton(
      onClick = {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
        context.startActivity(intent)
      },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("إنشاء مفتاح من Google AI Studio")
    }
    if (message.isNotBlank()) {
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
      )
    }
    if (keys.isEmpty()) {
      Text(
        text = "لم يتم إضافة أي مفتاح بعد — أدوات الذكاء الاصطناعي لن تعمل حتى تضيف واحدًا.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }
    keys.forEachIndexed { index, key ->
      val isActive = index == activeIndex
      val shown = index in shownKeys
      val displayName = names.getOrNull(index) ?: "مفتاح ${index + 1}"
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = displayName,
              style = MaterialTheme.typography.titleMedium,
              modifier = Modifier.weight(1f),
            )
            if (isActive) {
              Text(
                text = "مستخدم حاليًا",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            // Pencil on the left end of the row (RTL end) — edits name and key value.
            IconButton(
              onClick = {
                editingIndex = index
                editName = displayName
                editKey = key
                showEditKey = false
              },
            ) {
              Icon(imageVector = Icons.Default.Edit, contentDescription = "تعديل اسم المفتاح والمفتاح نفسه")
            }
          }
          if (editingIndex == index) {
            OutlinedTextField(
              value = editName,
              onValueChange = { editName = it },
              label = { Text("اسم المفتاح") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = editKey,
              onValueChange = { editKey = it },
              label = { Text("قيمة المفتاح") },
              singleLine = true,
              visualTransformation = if (showEditKey) VisualTransformation.None else PasswordVisualTransformation(),
              trailingIcon = {
                IconButton(onClick = { showEditKey = !showEditKey }) {
                  Icon(
                    imageVector = if (showEditKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showEditKey) "إخفاء المفتاح" else "إظهار المفتاح",
                  )
                }
              },
              modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                onClick = {
                  val ok = UserGeminiKeyStore.updateKey(index, editKey, editName, context)
                  if (ok) {
                    editingIndex = -1
                    message = "تم تحديث المفتاح."
                    messageIsError = false
                  } else if (editKey.trim().isBlank()) {
                    message = "قيمة المفتاح لا يمكن أن تكون فارغة."
                    messageIsError = true
                  } else {
                    message = "هذه القيمة موجودة بالفعل لمفتاح آخر."
                    messageIsError = true
                  }
                  refresh()
                },
              ) {
                Text("حفظ التعديل")
              }
              OutlinedButton(onClick = { editingIndex = -1 }) {
                Text("إلغاء")
              }
            }
          } else {
            Text(
              text = if (shown) key else UserGeminiKeyStore.maskedKey(key),
              style = MaterialTheme.typography.bodySmall,
            )
            // Icon row: test is FIRST, then show, copy, delete.
            Row(verticalAlignment = Alignment.CenterVertically) {
              OutlinedButton(
                enabled = testingIndex != index,
                onClick = {
                  testingIndex = index
                  testResults = testResults - index
                  scope.launch {
                    val result = GeminiApiClient().testApiKey(key)
                    testResults = testResults + (index to (result.isSuccess to (result.getOrNull() ?: result.exceptionOrNull()?.message ?: "فشل الاختبار.")))
                    testingIndex = -1
                  }
                },
              ) {
                Text(if (testingIndex == index) "جارٍ الاختبار..." else "اختبار المفتاح")
              }
              IconButton(
                onClick = {
                  shownKeys = if (shown) shownKeys - index else shownKeys + index
                },
              ) {
                Icon(
                  imageVector = if (shown) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  contentDescription = if (shown) "إخفاء المفتاح" else "إظهار المفتاح",
                  modifier = Modifier.size(20.dp),
                )
              }
              IconButton(
                onClick = {
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  clipboard.setPrimaryClip(ClipData.newPlainText("Gemini API key", key))
                  Toast.makeText(context, "تم نسخ المفتاح", Toast.LENGTH_SHORT).show()
                },
              ) {
                Icon(
                  imageVector = Icons.Default.ContentCopy,
                  contentDescription = "نسخ المفتاح",
                  modifier = Modifier.size(20.dp),
                )
              }
              IconButton(
                onClick = {
                  UserGeminiKeyStore.removeKey(index, context)
                  shownKeys = shownKeys - index
                  testResults = testResults - index
                  if (editingIndex == index) editingIndex = -1
                  refresh()
                },
              ) {
                Icon(
                  imageVector = Icons.Default.Delete,
                  contentDescription = "حذف المفتاح",
                  modifier = Modifier.size(20.dp),
                )
              }
            }
            if (!isActive) {
              OutlinedButton(
                onClick = {
                  UserGeminiKeyStore.setActiveKey(index, context)
                  refresh()
                },
              ) {
                Text("استخدام هذا المفتاح")
              }
            }
            testResults[index]?.let { (ok, text) ->
              Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
              )
            }
          }
        }
      }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "تنبيه: مفاتيحك شخصية — لا تشاركها مع أحد، ويمكنك حذف أي مفتاح من هنا في أي وقت.",
      style = MaterialTheme.typography.bodySmall,
    )
  }
}
