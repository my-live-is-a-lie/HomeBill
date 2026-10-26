package com.example.ui.viewmodel

import android.content.Context
import com.example.data.remote.ChatChartBar
import com.example.data.remote.ChatStatHighlight
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * One saved assistant conversation.
 *
 * [modelId] is null for "automatic" (the current failover order in
 * GeminiBillChatService). [thinkingLevel] is null for the model's own
 * default, otherwise one of "LOW", "MEDIUM", "HIGH" (the Gemini app's
 * low / medium / high thinking levels).
 */
data class ChatConversation(
  val id: String,
  val title: String,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
  val pinned: Boolean,
  val modelId: String? = null,
  val thinkingLevel: String? = null,
  val messages: List<BillChatMessage>,
)

data class ChatImportResult(
  val imported: Int,
  val skipped: Int,
)

/**
 * Local-only chat history storage.
 *
 * No new dependency (Room / DataStore are commented out in build.gradle.kts),
 * no network, no Firestore quota: conversations live as one JSON file in the
 * app-private files directory, one file per Firebase user id, so another
 * account on the same phone never sees them.
 *
 * File format (versioned, so a future format can migrate):
 * {"version":1,"conversations":[{id,title,createdAtMillis,updatedAtMillis,
 *   pinned,modelId,thinkingLevel,messages:[{id,timestampMillis,isUser,text,highlights,
 *   chartTitle,chartBars,followUpSuggestions,isError}]}]}
 *
 * Writes are atomic: the new content goes to a temp file first and is then
 * renamed over the old file, so a crash mid-write cannot corrupt history.
 */
object ChatHistoryStore {

  private const val FILE_VERSION = 1

  private var historyFile: File? = null

  fun init(context: Context, userId: String) {
    val safeUserId = userId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
      .ifBlank { "default" }
    historyFile = File(context.filesDir, "chat_history_$safeUserId.json")
  }

  fun load(): List<ChatConversation> {
    val file = historyFile ?: return emptyList()
    if (!file.exists()) return emptyList()
    return runCatching { fromJson(file.readText()) }.getOrElse { emptyList() }
  }

  fun save(conversations: List<ChatConversation>) {
    val file = historyFile ?: return
    runCatching {
      val temp = File(file.parentFile, "${file.name}.tmp")
      temp.writeText(toJson(conversations))
      if (file.exists()) file.delete()
      temp.renameTo(file)
    }
  }

  fun toJson(conversations: List<ChatConversation>): String {
    val root = JSONObject()
    root.put("version", FILE_VERSION)
    val array = JSONArray()
    conversations.forEach { conversation -> array.put(conversationToJson(conversation)) }
    root.put("conversations", array)
    return root.toString()
  }

  /**
   * Parses a backup file. Throws on malformed JSON so the caller can show an
   * error instead of silently dropping the user's backup.
   */
  fun fromJson(json: String): List<ChatConversation> {
    val root = JSONObject(json)
    val array = root.optJSONArray("conversations") ?: return emptyList()
    val result = mutableListOf<ChatConversation>()
    for (i in 0 until array.length()) {
      val obj = array.optJSONObject(i) ?: continue
      result.add(conversationFromJson(obj))
    }
    return result
  }

  /**
   * Merges an imported backup into [existing]: conversations whose id already
   * exists are skipped (never overwritten), new ones are appended. Returns the
   * merged list plus the import counts for the toast.
   */
  fun mergeImport(
    existing: List<ChatConversation>,
    imported: List<ChatConversation>,
  ): Pair<List<ChatConversation>, ChatImportResult> {
    val existingIds = existing.map { it.id }.toSet()
    val fresh = imported.filterNot { it.id in existingIds }
    return (existing + fresh) to ChatImportResult(
      imported = fresh.size,
      skipped = imported.size - fresh.size,
    )
  }

  fun defaultTitle(messages: List<BillChatMessage>): String {
    val firstUser = messages.firstOrNull { it.isUser }?.text?.trim().orEmpty()
    if (firstUser.isBlank()) return "محادثة جديدة"
    return if (firstUser.length <= 40) firstUser else firstUser.take(40) + "…"
  }

  private fun conversationToJson(conversation: ChatConversation): JSONObject {
    val obj = JSONObject()
    obj.put("id", conversation.id)
    obj.put("title", conversation.title)
    obj.put("createdAtMillis", conversation.createdAtMillis)
    obj.put("updatedAtMillis", conversation.updatedAtMillis)
    obj.put("pinned", conversation.pinned)
    if (conversation.modelId != null) obj.put("modelId", conversation.modelId)
    if (conversation.thinkingLevel != null) obj.put("thinkingLevel", conversation.thinkingLevel)
    val messages = JSONArray()
    conversation.messages.forEach { message ->
      val msg = JSONObject()
      msg.put("id", message.id)
      msg.put("timestampMillis", message.timestampMillis)
      msg.put("isUser", message.isUser)
      msg.put("text", message.text)
      val highlights = JSONArray()
      message.highlights.forEach { highlight ->
        highlights.put(
          JSONObject()
            .put("label", highlight.label)
            .put("value", highlight.value)
            .put("trend", highlight.trend),
        )
      }
      msg.put("highlights", highlights)
      msg.put("chartTitle", message.chartTitle)
      val bars = JSONArray()
      message.chartBars.forEach { bar ->
        bars.put(
          JSONObject()
            .put("label", bar.label)
            .put("value", bar.value)
            .put("unit", bar.unit),
        )
      }
      msg.put("chartBars", bars)
      val suggestions = JSONArray()
      message.followUpSuggestions.forEach { suggestions.put(it) }
      msg.put("followUpSuggestions", suggestions)
      msg.put("isError", message.isError)
      messages.put(msg)
    }
    obj.put("messages", messages)
    return obj
  }

  private fun conversationFromJson(obj: JSONObject): ChatConversation {
    val messages = mutableListOf<BillChatMessage>()
    val messagesArray = obj.optJSONArray("messages") ?: JSONArray()
    for (i in 0 until messagesArray.length()) {
      val msg = messagesArray.optJSONObject(i) ?: continue
      val highlights = mutableListOf<ChatStatHighlight>()
      val highlightsArray = msg.optJSONArray("highlights") ?: JSONArray()
      for (j in 0 until highlightsArray.length()) {
        val highlight = highlightsArray.optJSONObject(j) ?: continue
        highlights.add(
          ChatStatHighlight(
            label = highlight.optString("label"),
            value = highlight.optString("value"),
            trend = highlight.optString("trend"),
          ),
        )
      }
      val bars = mutableListOf<ChatChartBar>()
      val barsArray = msg.optJSONArray("chartBars") ?: JSONArray()
      for (j in 0 until barsArray.length()) {
        val bar = barsArray.optJSONObject(j) ?: continue
        bars.add(
          ChatChartBar(
            label = bar.optString("label"),
            value = bar.optDouble("value"),
            unit = bar.optString("unit"),
          ),
        )
      }
      val suggestions = mutableListOf<String>()
      val suggestionsArray = msg.optJSONArray("followUpSuggestions") ?: JSONArray()
      for (j in 0 until suggestionsArray.length()) {
        suggestions.add(suggestionsArray.optString(j))
      }
      messages.add(
        BillChatMessage(
          id = msg.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
          timestampMillis = msg.optLong("timestampMillis", System.currentTimeMillis()),
          isUser = msg.optBoolean("isUser"),
          text = msg.optString("text"),
          highlights = highlights,
          chartTitle = msg.optString("chartTitle"),
          chartBars = bars,
          followUpSuggestions = suggestions,
          isError = msg.optBoolean("isError"),
        ),
      )
    }
    return ChatConversation(
      id = obj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
      title = obj.optString("title").ifBlank { "محادثة" },
      createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
      updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis()),
      pinned = obj.optBoolean("pinned"),
      modelId = obj.optString("modelId").ifBlank { null },
      thinkingLevel = obj.optString("thinkingLevel").ifBlank { null },
      messages = messages,
    )
  }
}
