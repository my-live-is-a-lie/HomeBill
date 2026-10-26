package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.remote.GeminiApiClient
import com.example.data.remote.UserGeminiKeyStore
import kotlinx.coroutines.launch

/**
 * Settings hub. Today it holds one entry — AI keys — so more settings
 * can be added here later as further list entries without restructuring.
 * The key-management UI itself lives on the AI-key page, reached by
 * tapping the AI entry below, not directly on the hub.
 */
@Composable
fun SettingsScreen() {
  var showAiKeys by remember { mutableStateOf(false) }
  if (showAiKeys) {
    GeminiKeysScreen(onBack = { showAiKeys = false })
  } else {
    SettingsHubScreen(onOpenAiKeys = { showAiKeys = true })
  }
}

@Composable
private fun SettingsHubScreen(onOpenAiKeys: () -> Unit) {
  val context = LocalContext.current
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
    Text(text = "الإعدادات", style = MaterialTheme.typography.titleLarge)
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
    Text(
      text = "ستظهر هنا إعدادات أخرى مستقبلًا.",
      style = MaterialTheme.typography.bodySmall,
    )
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
