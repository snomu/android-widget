package io.github.snomu.termwidget.data

import kotlinx.serialization.json.Json

object Jsonx {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
