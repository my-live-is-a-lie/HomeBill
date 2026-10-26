package com.example.data.remote

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.model.UtilityType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiBillScannerTest {

  @Test
  fun `GeminiApiClient encodes captured Bitmap and parses structured JSON with automatic expense classification`() =
    runTest {
      var capturedModel: String? = null
      var capturedRequest: GenerateContentRequest? = null

      val fakeService =
        object : GeminiApiService {
          override suspend fun generateContent(
            model: String,
            apiKey: String,
            request: GenerateContentRequest,
          ): GenerateContentResponse {
            capturedModel = model
            capturedRequest = request
            val mockStructuredJson =
              """
              {
                "date": "2026-10-20",
                "storeName": "الشركة السعودية للكهرباء",
                "totalAmount": 342.80,
                "taxAmount": 44.71,
                "subtotalAmount": 298.09,
                "currency": "ر.س",
                "invoiceNumber": "SEC-998821",
                "expenseCategory": "electricity",
                "detectedUtilityType": "electricity",
                "suggestedCategoryName": "فاتورة الكهرباء",
                "classificationReason": "تم التصنيف كـ كهرباء بناءً على اسم الجهة (الشركة السعودية للكهرباء) ووحدة الاستهلاك ك.و.س",
                "consumptionValue": 1420.0,
                "consumptionUnit": "ك.و.س",
                "previousReading": 18000.0,
                "currentReading": 19420.0,
                "rawExtractedText": "الشركة السعودية للكهرباء - فاتورة رقم SEC-998821 - الإجمالي 342.80 ر.س",
                "sheetRows": [
                  { "label": "استهلاك الكهرباء", "value": "1420", "unit": "ك.و.س" },
                  { "label": "ضريبة القيمة المضافة", "value": "44.71", "unit": "ر.س" },
                  { "label": "الإجمالي المستحق", "value": "342.80", "unit": "ر.س" }
                ]
              }
              """.trimIndent()
            return GenerateContentResponse(
              candidates =
                listOf(
                  GeminiCandidate(
                    content = GeminiContent(parts = listOf(GeminiPart(text = mockStructuredJson)))
                  )
                )
            )
          }
        }

      val client =
        GeminiApiClient(
          service = fakeService,
          apiKeyProvider = { "test-gemini-api-key" },
        )
      val scanner = GeminiBillScanner(apiClient = client)

      val capturedBitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
      capturedBitmap.eraseColor(Color.WHITE)

      // 1) Verify raw structured JSON DTO extraction & automatic classification
      val jsonDto = scanner.extractStructuredBillJson(capturedBitmap).getOrThrow()
      assertEquals("2026-10-20", jsonDto.date)
      assertEquals("الشركة السعودية للكهرباء", jsonDto.storeName)
      assertEquals("electricity", jsonDto.resolvedExpenseType)
      assertEquals(342.80, jsonDto.totalAmount, 0.001)
      assertEquals(44.71, jsonDto.taxAmount, 0.001)

      // Verify multimodal request payload and JSON schema configuration
      assertNotNull(capturedModel)
      val req = checkNotNull(capturedRequest)
      assertEquals("application/json", req.generationConfig?.responseMimeType)
      val requiredKeys =
        req.generationConfig
          ?.responseSchema
          ?.get("required")
          ?.jsonArray
          ?.map { it.jsonPrimitive.content }
          .orEmpty()
      assertTrue(
        requiredKeys.containsAll(
          listOf("date", "storeName", "totalAmount", "expenseCategory", "classificationReason")
        )
      )

      val imagePart = req.contents.first().parts.firstOrNull { it.inlineData != null }
      assertNotNull(imagePart)
      assertEquals("image/jpeg", imagePart?.inlineData?.mimeType)
      assertTrue(imagePart?.inlineData?.data?.isNotBlank() == true)

      // 2) Verify ScannedBillDraft mapping
      val draft = scanner.scanBillBitmap(capturedBitmap).getOrThrow()
      assertEquals(UtilityType.ELECTRICITY, draft.detectedUtilityType)
      assertEquals("فاتورة الكهرباء", draft.suggestedCategoryName)
      assertTrue(draft.classificationReason.contains("كهرباء"))
      assertEquals("الشركة السعودية للكهرباء", draft.storeName)
      assertEquals("2026-10-20", draft.billDateIso)
      assertEquals("2026-10", draft.billingMonth)
      assertEquals(342.80, draft.totalAmount, 0.001)
      assertEquals(3, draft.sheetRows.size)
      assertTrue(draft.structuredJsonPreview.contains("\"expenseCategory\": \"electricity\""))
    }

  @Test
  fun `GeminiApiClient fails over on HTTP 503 and classifies supermarket receipt as SHOPPING from storeName`() =
    runTest {
      var callCount = 0
      val fakeService =
        object : GeminiApiService {
          override suspend fun generateContent(
            model: String,
            apiKey: String,
            request: GenerateContentRequest,
          ): GenerateContentResponse {
            callCount++
            if (callCount == 1) {
              throw HttpException(
                Response.error<GenerateContentResponse>(
                  503,
                  """{"error":{"code":503,"message":"Model overloaded"}}"""
                    .toResponseBody("application/json".toMediaType()),
                )
              )
            }
            val storeReceiptJson =
              """
              {
                "date": "2026-10-24",
                "storeName": "أسواق التميمي",
                "totalAmount": 245.50,
                "taxAmount": 32.02,
                "subtotalAmount": 213.48,
                "currency": "ر.س",
                "invoiceNumber": "POS-7712",
                "expenseCategory": "other",
                "detectedUtilityType": "other",
                "suggestedCategoryName": "فاتورة منزلية",
                "classificationReason": "",
                "consumptionValue": 0,
                "consumptionUnit": "",
                "previousReading": 0,
                "currentReading": 0,
                "rawExtractedText": "أسواق التميمي - المجموع 245.50 ر.س",
                "sheetRows": []
              }
              """.trimIndent()
            return GenerateContentResponse(
              candidates =
                listOf(
                  GeminiCandidate(
                    content = GeminiContent(parts = listOf(GeminiPart(text = storeReceiptJson)))
                  )
                )
            )
          }
        }

      val client =
        GeminiApiClient(
          service = fakeService,
          apiKeyProvider = { "test-gemini-api-key" },
        )
      val scanner = GeminiBillScanner(apiClient = client)
      val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)

      val draft = scanner.scanBillBitmap(bitmap).getOrThrow()
      assertEquals(2, callCount)
      assertEquals("أسواق التميمي", draft.storeName)
      assertEquals(UtilityType.SHOPPING, draft.detectedUtilityType)
      assertEquals("فواتير التسوق", draft.suggestedCategoryName)
      assertEquals("صنف", draft.consumptionUnit)
      assertTrue(draft.classificationReason.contains("تسوق"))
      assertEquals("2026-10-24", draft.billDateIso)
      assertEquals(245.50, draft.totalAmount, 0.001)
      assertTrue(draft.sheetRows.any { it.value == "أسواق التميمي" })
      assertTrue(draft.sheetRows.any { it.label.contains("إجمالي") })
    }

  @Test
  fun `classifyDraftExpense uses Gemini text classification based on edited storeName and content`() =
    runTest {
      var capturedPromptText = ""
      val fakeService =
        object : GeminiApiService {
          override suspend fun generateContent(
            model: String,
            apiKey: String,
            request: GenerateContentRequest,
          ): GenerateContentResponse {
            capturedPromptText = request.contents.first().parts.first().text.orEmpty()
            val classificationJson =
              """
              {
                "expenseCategory": "water",
                "suggestedCategoryName": "فاتورة الماء",
                "classificationReason": "تم التصنيف كـ مياه بناءً على اسم شركة المياه الوطنية واستهلاك م³",
                "defaultUnit": "م³"
              }
              """.trimIndent()
            return GenerateContentResponse(
              candidates =
                listOf(
                  GeminiCandidate(
                    content = GeminiContent(parts = listOf(GeminiPart(text = classificationJson)))
                  )
                )
            )
          }
        }

      val client =
        GeminiApiClient(
          service = fakeService,
          apiKeyProvider = { "test-gemini-api-key" },
        )
      val scanner = GeminiBillScanner(apiClient = client)

      val initialDraft =
        ScannedBillDraft(
          detectedUtilityType = UtilityType.OTHER,
          suggestedCategoryName = "فاتورة منزلية",
          storeName = "شركة المياه الوطنية",
          billDateIso = "2026-10-22",
          billingMonth = "2026-10",
          billDateEpochMillis = 1792627200000L,
          totalAmount = 94.0,
          currency = "ر.س",
          consumptionValue = 28.0,
          consumptionUnit = "وحدة",
          previousReading = 400.0,
          currentReading = 428.0,
          invoiceNumber = "NWC-551",
          providerName = "شركة المياه الوطنية",
          rawExtractedText = "استهلاك المياه والصرف الصحي 28 م³",
          sheetRows = emptyList(),
        )

      val updated = scanner.classifyDraftExpense(initialDraft).getOrThrow()
      assertTrue(capturedPromptText.contains("شركة المياه الوطنية"))
      assertEquals(UtilityType.WATER, updated.detectedUtilityType)
      assertEquals("فاتورة الماء", updated.suggestedCategoryName)
      assertEquals("م³", updated.consumptionUnit)
      assertTrue(updated.classificationReason.contains("مياه"))
    }
}
