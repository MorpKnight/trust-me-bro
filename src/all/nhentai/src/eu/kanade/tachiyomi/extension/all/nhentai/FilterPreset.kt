package eu.kanade.tachiyomi.extension.all.nhentai

import kotlinx.serialization.Serializable

@Serializable
internal class FilterPreset(
    val name: String,
    val query: String,
)
