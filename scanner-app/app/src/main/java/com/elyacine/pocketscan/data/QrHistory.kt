package com.elyacine.pocketscan.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class QrResult(val value: String, val kind: String, val time: Long)

/** The last scanned codes, newest first. */
class QrHistory(context: Context) {

    private val prefs = context.getSharedPreferences("qr_history", Context.MODE_PRIVATE)

    fun list(): List<QrResult> {
        val array = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            QrResult(o.getString("value"), o.optString("kind", "Text"), o.optLong("time"))
        }
    }

    fun add(result: QrResult) = save(listOf(result) + list().take(MAX - 1))

    fun clear() = save(emptyList())

    private fun save(items: List<QrResult>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("value", it.value).put("kind", it.kind).put("time", it.time)) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "items"
        const val MAX = 50
    }
}
