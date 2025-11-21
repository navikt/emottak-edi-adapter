package no.nav.emottak.edi.adapter.util

import kotlinx.serialization.json.Json

object JsonUtil {
    fun extendJson(jsonString: String, extraFields: Map<String, String>): String {
        val jsonMap = Json.decodeFromString<MutableMap<String, String>>(jsonString)
        jsonMap.putAll(extraFields)
        return Json.encodeToString(jsonMap)
    }
}
