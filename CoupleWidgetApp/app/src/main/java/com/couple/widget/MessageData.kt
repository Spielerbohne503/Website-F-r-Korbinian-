package com.couple.widget

import org.json.JSONObject

data class MessageData(val type: String, val content: String) {

    fun toJson(): String = JSONObject().apply {
        put("type", type)
        put("content", content)
    }.toString()

    companion object {
        fun fromJson(json: String): MessageData? = try {
            val obj = JSONObject(json)
            MessageData(obj.getString("type"), obj.getString("content"))
        } catch (e: Exception) {
            null
        }
    }
}
