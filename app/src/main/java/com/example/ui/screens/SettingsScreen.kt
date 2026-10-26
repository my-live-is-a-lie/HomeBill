package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.remote.UserGeminiKeyStore

/**
 * Settings screen for the user's own Gemini API key.
 *
 * The key is stored on the device only and is required before any AI
 * feature (bill scanning, reclassification, assistant chat) can run.
 * No developer key is baked into the app anymore for these features.
 */
@Composable
fun SettingsScreen() {
  val context = LocalContext.current
  var keyText by remember { mutableStateOf(UserGeminiKeyStore.getKey(context)) }
  var showKey by remember { mutableStateOf(false) }
  var savedMessage by remember { mutableStateOf("") }
  var hasKey by remember { mutableStateOf(UserGeminiKeyStore.hasKey(context)) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(
      text = "إعدادات الذكاء الاصطناعي",
      style = MaterialTheme.typography.titleLarge,
    )
    Text(
      text = "أدخل مفتاح Gemini الخاص بك قبل استخدام قراءة الفواتير والمساعد الذكي. " +
        "المفتاح يُحفظ على جهازك فقط، ولا يُرسل إلى خادم، ولا يُرفع إلى GitHub، " +
        "وحصته من حسابك أنت في Google AI Studio.",
      style = MaterialTheme.typography.bodyMedium,
    )
    Text(
      text = if (hasKey) {
        "تم حفظ مفتاح: ${UserGeminiKeyStore.maskedKey()}"
      } else {
        "لم يتم إضافة مفتاح بعد — أدوات الذكاء الاصطناعي لن تعمل حتى تضيفه."
      },
      style = MaterialTheme.typography.bodyMedium,
      color = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
    OutlinedTextField(
      value = keyText,
      onValueChange = { keyText = it; savedMessage = "" },
      label = { Text("مفتاح Gemini API") },
      singleLine = true,
      visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
      onClick = { showKey = !showKey },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(if (showKey) "إخفاء المفتاح" else "إظهار المفتاح")
    }
    Button(
      onClick = {
        UserGeminiKeyStore.saveKey(keyText, context)
        hasKey = UserGeminiKeyStore.hasKey(context)
        savedMessage = if (hasKey) "تم حفظ المفتاح على جهازك." else "أدخل مفتاحًا غير فارغ أولًا."
      },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("حفظ المفتاح")
    }
    OutlinedButton(
      onClick = {
        UserGeminiKeyStore.clearKey(context)
        keyText = ""
        hasKey = false
        savedMessage = "تم حذف المفتاح المحفوظ."
      },
      enabled = hasKey,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("حذف المفتاح المحفوظ")
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
    if (savedMessage.isNotBlank()) {
      Text(text = savedMessage, style = MaterialTheme.typography.bodyMedium)
    }
    Text(
      text = "تنبيه: مفتاحك مفتاح شخصي — لا تشاركه مع أحد، ويمكنك حذفه من هنا في أي وقت.",
      style = MaterialTheme.typography.bodySmall,
    )
  }
}
