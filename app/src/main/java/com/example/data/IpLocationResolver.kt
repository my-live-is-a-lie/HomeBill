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
 * background. The request exposes the device's public IP to the lookup
 * provider, which is the price of an IP-based lookup.
 *
 * Two providers are tried in order, because a single provider is a
 * single point of failure: ipapi.co throttles shared IPs (mobile carriers
 * put many subscribers behind a few public addresses), so its free quota
 * is routinely exhausted by other users of the same address. When it
 * fails for any reason, ipwho.is is tried instead. Both answer over
 * HTTPS with a country name and code in the same display format.
 */
object IpLocationResolver {

  suspend fun resolveCountryName(): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching { resolveFromIpapi() }
        .recoverCatching { resolveFromIpwho() }
    }

  private fun resolveFromIpapi(): String {
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
      return if (code.isNotBlank()) "$name ($code)" else name
    } finally {
      connection.disconnect()
    }
  }

  private fun resolveFromIpwho(): String {
    val connection = URL("https://ipwho.is/").openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = 8000
      connection.readTimeout = 8000
      connection.setRequestProperty("Accept", "application/json")
      if (connection.responseCode != HttpURLConnection.HTTP_OK) {
        throw IOException("ipwho HTTP ${connection.responseCode}")
      }
      val body = connection.inputStream.bufferedReader().use { it.readText() }
      val json = JSONObject(body)
      if (!json.optBoolean("success", false)) {
        throw IOException("ipwho lookup unsuccessful")
      }
      val name = json.optString("country", "").trim()
      val code = json.optString("country_code", "").trim()
      if (name.isBlank()) {
        throw IOException("ipwho response lacks country")
      }
      return if (code.isNotBlank()) "$name ($code)" else name
    } finally {
      connection.disconnect()
    }
  }
}
