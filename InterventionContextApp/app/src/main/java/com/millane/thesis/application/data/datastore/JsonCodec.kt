package com.millane.thesis.application.data.datastore

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object JsonCodec {
    // ignoreUnknownKeys is important when adding fields later
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    inline fun <reified T> encode(value: T): String =
        json.encodeToString(value)

    inline fun <reified T> decode(raw: String): T =
        json.decodeFromString(raw)
}