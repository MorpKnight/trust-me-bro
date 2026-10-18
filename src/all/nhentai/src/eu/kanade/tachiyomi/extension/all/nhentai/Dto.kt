package eu.kanade.tachiyomi.extension.all.nhentai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Hentai(
    private val id: Int,
    val images: Images,
    @SerialName("media_id") val mediaId: String,
    val tags: List<Tag>,
    val title: Title,
    @SerialName("upload_date") val uploadDate: Long,
    @SerialName("num_favorites") val numFavorites: Long,
)

@Serializable
class Title(
    val english: String? = null,
    val japanese: String? = null,
    val pretty: String? = null,
)

@Serializable
class Images(
    val pages: List<Image>,
)

@Serializable
class Image(
    @SerialName("t") private val type: String,
) {
    val extension: String
        get() = when (type) {
            "w" -> "webp"
            "p" -> "png"
            "g" -> "gif"
            else -> "jpg"
        }
}

@Serializable
class Tag(
    val name: String,
    val type: String,
)
