package eu.kanade.tachiyomi.extension.all.nhentai

object NHUtils {
    fun getArtists(data: Hentai): String = data.tags
        .filter { it.type == "artist" }
        .joinToString(", ") { it.name }

    fun getGroups(data: Hentai): String? = data.tags
        .filter { it.type == "group" }
        .joinToString(", ") { it.name }
        .takeIf(String::isNotBlank)

    fun getTagDescription(data: Hentai): String {
        val tags = data.tags.groupBy { it.type }
        return buildString {
            tags["category"]?.joinToString { it.name }?.let { append("Categories: ").append(it).append('\n') }
            tags["parody"]?.joinToString { it.name }?.let { append("Parodies: ").append(it).append('\n') }
            tags["character"]?.joinToString { it.name }?.let { append("Characters: ").append(it).append("\n\n") }
        }
    }

    fun getTags(data: Hentai): String = data.tags
        .filter { it.type == "tag" }
        .joinToString(", ") { it.name }
}
