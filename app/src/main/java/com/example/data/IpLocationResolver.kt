package com.example.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Resolves the device's current location from its public IP address.
 *
 * This returns ONLY a location string (for example "Egypt (EG)"), never a
 * currency: the assistant itself determines the currency for that location
 * from its own knowledge and returns it to the app in structured fields.
 * There is deliberately no country or currency table in the app code.
 *
 * This runs only when the user explicitly asks the assistant to set the
 * default currency by residence, never at first launch and never in the
 * background. The request exposes the device's public IP to ipapi.co,
 * which is the price of an IP-based lookup.
 */
object IpLocationResolver {

  suspend fun resolveCountryName(): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val connection = URL("https://ipapi.co/json/").openConnection() as HttpURLConnection
        try {
          connection.connectTimeout = 8000
          connection.readTimeout = 8000
          connection.setRequestProperty("Accept", "application/json")
          if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("ipapi HTTP ${connection.responseCode}")
          }
          val body = connection.inputStream.bufferedReader().use { it.readText() }
          val json = JSONObject(body)
          val name = json.optString("country_name", "").trim()
          val code = json.optString("country_code", "").trim()
          if (name.isBlank()) {
            throw IOException("ipapi response lacks country_name")
          }
          if (code.isNotBlank()) "$name ($code)" else name
        } finally {
          connection.disconnect()
        }
      }
    }
}
