package eu.kanade.tachiyomi.extension.all.nhentai

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds

@Source
abstract class NHentai :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private var displayFullTitle = preferences.getString(TITLE_PREF, "full") == "full"

    private val nhLang: String
        get() = when (lang) {
            "en" -> "english"
            "ja" -> "japanese"
            "zh" -> "chinese"
            else -> ""
        }

    private val shortenTitleRegex = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")
    private fun String.shortenTitle() = replace(shortenTitleRegex, "").trim()

    override fun OkHttpClient.Builder.configureClient() = rateLimit(4, 1.seconds)

    override fun Headers.Builder.configureHeaders() = setRandomUserAgent(filterInclude = listOf("chrome"))

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = if (nhLang.isBlank()) {
            "$baseUrl/search/".toHttpUrl().newBuilder()
                .addQueryParameter("q", "\"\"")
                .addQueryParameter("sort", "popular")
                .addQueryParameter("page", page.toString())
                .build()
        } else {
            "$baseUrl/language/$nhLang/popular".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .build()
        }
        return getMangasPage(url)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = (if (nhLang.isBlank()) "$baseUrl/" else "$baseUrl/language/$nhLang/")
            .toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return getMangasPage(url)
    }

    private suspend fun getMangasPage(url: HttpUrl): MangasPage {
        val response = client.get(url)
        val requestPath = response.request.url.encodedPath
        val document = response.asJsoup()
        if (requestPath.contains("/login/") && document.select(".fa-sign-in").isNotEmpty()) {
            throw Exception("Log in via WebView to view favorites")
        }

        return MangasPage(
            document.select(GALLERY_SELECTOR).map(::mangaFromElement),
            document.selectFirst(NEXT_PAGE_SELECTOR) != null,
        )
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")?.attr("href").orEmpty())
        title = element.select("a > div").text().replace("\"", "").let {
            if (displayFullTitle) it.trim() else it.shortenTitle()
        }
        thumbnail_url = element.selectFirst(".cover img")?.let { image ->
            if (image.hasAttr("data-src")) image.attr("abs:data-src") else image.attr("abs:src")
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val normalizedQuery = query.trim()
        val id = when {
            normalizedQuery.startsWith(PREFIX_ID_SEARCH) -> normalizedQuery.removePrefix(PREFIX_ID_SEARCH).toIntOrNull()
            normalizedQuery.toIntOrNull() != null -> normalizedQuery.toInt()
            else -> null
        }
        if (normalizedQuery.startsWith(PREFIX_ID_SEARCH) || normalizedQuery.toIntOrNull() != null) {
            val manga = id?.let { getMangaByUrl("$baseUrl/g/$it/".toHttpUrl()) }
            return MangasPage(listOfNotNull(manga), false)
        }

        val filterList = if (filters.isEmpty()) getFilterList(null) else filters
        val selectedPresetName = filterList.firstInstanceOrNull<LoadPresetFilter>()?.selectedPresetName
        val selectedPreset = selectedPresetName?.let { name ->
            getFilterPresets().firstOrNull { it.name == name }
        }
        val advancedQuery = listOfNotNull(
            selectedPreset?.query?.takeIf(String::isNotBlank),
            combineQuery(filterList).takeIf(String::isNotBlank),
        ).distinct().joinToString(" ")
        saveFilterPresetIfRequested(filterList, advancedQuery)
        val favoriteFilter = filterList.firstInstanceOrNull<FavoriteFilter>()
        val offsetPage = filterList.firstInstanceOrNull<OffsetPageFilter>()
            ?.state?.toIntOrNull()?.plus(page) ?: page

        val url = if (favoriteFilter?.state == true) {
            val favoriteQuery = listOf(query.trim(), advancedQuery).filter(String::isNotBlank).joinToString(" ")
            "$baseUrl/favorites/".toHttpUrl().newBuilder()
                .addQueryParameter("q", favoriteQuery)
                .addQueryParameter("page", offsetPage.toString())
                .build()
        } else {
            val languageQuery = nhLang.takeIf(String::isNotBlank)?.let { "language:$it" }.orEmpty()
            val searchQuery = listOf(query.trim(), languageQuery, advancedQuery)
                .filter(String::isNotBlank)
                .joinToString(" ")
                // A blank query returns a 404 on nhentai; an empty quoted term lists all galleries.
                .ifBlank { "\"\"" }
            val builder = "$baseUrl/search/".toHttpUrl().newBuilder()
                .addQueryParameter("q", searchQuery)
                .addQueryParameter("page", offsetPage.toString())
            filterList.firstInstanceOrNull<SortFilter>()?.let { builder.addQueryParameter("sort", it.toUriPart()) }
            builder.build()
        }

        return getMangasPage(url)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "g") return null
        val id = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        return fetchManga("$baseUrl/g/$id/".toHttpUrl())
    }

    private suspend fun fetchManga(url: HttpUrl): SManga {
        val response = client.get(url)
        val mangaPath = response.request.url.encodedPath
        return response.asJsoup().toManga().apply { this.url = mangaPath }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val mangaPath = response.request.url.encodedPath
        val document = response.asJsoup()
        val data = document.getHentaiData()
        // Details and the single chapter are parsed from the same gallery page.
        val updatedManga = document.toManga(data).apply { url = mangaPath }
        val chapter = SChapter.create().apply {
            name = "Chapter"
            scanlator = NHUtils.getGroups(data)
            date_upload = data.uploadDate * 1000
            setUrlWithoutDomain(mangaPath)
        }
        return SMangaUpdate(updatedManga, listOf(chapter))
    }

    private fun Document.toManga() = toManga(getHentaiData())

    private fun Document.toManga(data: Hentai) = SManga.create().apply {
        val fullTitle = data.title.english ?: data.title.japanese ?: data.title.pretty.orEmpty()
        title = if (displayFullTitle) fullTitle else (data.title.pretty ?: fullTitle.shortenTitle())
        thumbnail_url = "https://${getCdnUrls(thumbnail = true).random()}/galleries/${data.mediaId}/1t.${data.images.pages.first().extension}"
        status = SManga.COMPLETED
        artist = NHUtils.getArtists(data)
        author = NHUtils.getGroups(data) ?: artist
        description = buildString {
            append("Full English and Japanese titles:\n")
            append(fullTitle).append('\n')
            append(data.title.japanese.orEmpty())
            append("\n\nPages: ").append(data.images.pages.size).append('\n')
            append("Favorited by: ").append(data.numFavorites).append('\n')
            append(NHUtils.getTagDescription(data))
        }
        genre = NHUtils.getTags(data)
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
        val data = document.getHentaiData()
        val cdnUrls = document.getCdnUrls(thumbnail = false)
        return data.images.pages.mapIndexed { index, image ->
            Page(
                index,
                imageUrl = "https://${cdnUrls.random()}/galleries/${data.mediaId}/${index + 1}.${image.extension}",
            )
        }
    }

    private fun Document.getHentaiData(): Hentai {
        // Try primary selector: script containing JSON.parse
        var script = selectFirst(HENTAI_SELECTOR)?.data()

        // Fallback 1: Any script tag with JSON.parse
        if (script == null) {
            script = selectFirst("script:contains(JSON.parse)")?.data()
        }

        // Fallback 2: Script tag containing media_id
        if (script == null) {
            script = selectFirst("script:contains(media_id)")?.data()
        }

        if (script == null) throw Exception("Gallery data was not found")

        // Try primary regex: JSON.parse("...")
        var data = DATA_REGEX.find(script)?.groupValues?.get(1)

        // Fallback regex: match raw JSON object with media_id
        if (data == null) {
            val fallbackRegex = Regex("""\{[^}]*"media_id"\s*:\s*\d+\}[^}]*\}")
            val fallbackMatch = fallbackRegex.find(script)
            data = fallbackMatch?.groupValues?.get(0)
        }

        if (data == null) throw Exception("Gallery data could not be parsed")
        return data.parseSourceData()
    }

    private fun Document.getCdnUrls(thumbnail: Boolean): List<String> {
        val regex = if (thumbnail) THUMB_CDN_REGEX else IMAGE_CDN_REGEX
        val data = regex.find(body().html())?.groupValues?.get(1)
            ?: throw Exception("Image servers were not found")
        return data.parseSourceData<List<String>>()
    }

    private inline fun <reified T> String.parseSourceData(): T = parseAs(
        transform = { json ->
            UNICODE_ESCAPE_REGEX.replace(json) {
                it.groupValues[1].toInt(16).toChar().toString()
            }
        },
    )

    private fun combineQuery(filters: FilterList): String = buildString {
        filters.filterIsInstance<AdvSearchEntryFilter>().forEach { filter ->
            filter.state.split(',')
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach { tag ->
                    val quoteValue = filter.name != "Pages" && filter.name != "Uploaded"
                    if (tag.startsWith('-')) append('-')
                    append(filter.name, ':')
                    if (quoteValue) append('"')
                    append(tag.removePrefix("-"))
                    if (quoteValue) append('"')
                    append(' ')
                }
        }
    }.trim()

    private fun getFilterPresets(): List<FilterPreset> = preferences.getString(FILTER_PRESETS_PREF, null)
        ?.parseAs<List<FilterPreset>>()
        .orEmpty()

    private fun saveFilterPresetIfRequested(filters: FilterList, query: String) {
        val saveFilter = filters.firstInstanceOrNull<SavePresetFilter>() ?: return
        if (!saveFilter.state) return

        val nameFilter = filters.firstInstanceOrNull<SavePresetNameFilter>()
        val name = nameFilter?.state?.trim().orEmpty()
        if (name.isNotBlank() && query.isNotBlank()) {
            val existingPresets = getFilterPresets()
            val hasPreset = existingPresets.any { it.name.equals(name, ignoreCase = true) }
            val presets = if (hasPreset) {
                existingPresets.map {
                    if (it.name.equals(name, ignoreCase = true)) FilterPreset(name, query) else it
                }
            } else {
                existingPresets + FilterPreset(name, query)
            }
            preferences.edit().putString(FILTER_PRESETS_PREF, presets.toJsonString()).apply()
            nameFilter?.state = ""
        }
        saveFilter.state = false
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        AdvSearchNote("Separate tags with commas (,)"),
        AdvSearchNote("Prepend with dash (-) to exclude"),
        TagFilter(),
        CategoryFilter(),
        GroupFilter(),
        ArtistFilter(),
        ParodyFilter(),
        CharactersFilter(),
        AdvSearchNote("Uploaded valid units are h, d, w, m, y."),
        AdvSearchNote("Example: (>20d)"),
        UploadedFilter(),
        AdvSearchNote("Filter by pages, for example: (>20)"),
        PagesFilter(),
        Filter.Separator(),
        AdvSearchNote("Choose a preset to combine filters, or enter a name, enable saving, and search."),
        LoadPresetFilter(getFilterPresets().map { it.name }),
        SavePresetNameFilter(),
        SavePresetFilter(),
        Filter.Separator(),
        SortFilter(),
        OffsetPageFilter(),
        AdvSearchNote("Sort is ignored if favorites only"),
        FavoriteFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = TITLE_PREF
            title = TITLE_PREF
            entries = arrayOf("Full Title", "Short Title")
            entryValues = arrayOf("full", "short")
            summary = "%s"
            setDefaultValue("full")
            setOnPreferenceChangeListener { _, newValue ->
                displayFullTitle = newValue == "full"
                true
            }
        }.also(screen::addPreference)

        screen.addRandomUAPreference()
    }

    private companion object {
        const val PREFIX_ID_SEARCH = "id:"
        const val TITLE_PREF = "Display manga title as:"
        const val FILTER_PRESETS_PREF = "filter_presets"
        const val GALLERY_SELECTOR = "#content .container:not(.index-popular) .gallery"
        const val NEXT_PAGE_SELECTOR = "#content > section.pagination > a.next"
        const val HENTAI_SELECTOR = "script:contains(JSON.parse)"
        // Primary regex: JSON.parse("...")
// Falls back to raw JSON object matching if primary fails
        val DATA_REGEX = Regex("""JSON\.parse\(\s*"(.*)"\s*\)""")
        // Fallback regex for raw JSON containing media_id
        val FALLBACK_REGEX = Regex("""\{[^}]*"media_id"\s*:\s*\d+\}[^}]*\}")
        val THUMB_CDN_REGEX = Regex("""thumb_cdn_urls:\s*(\[.*])""")
        val IMAGE_CDN_REGEX = Regex("""image_cdn_urls:\s*(\[.*])""")
        val UNICODE_ESCAPE_REGEX = Regex("""\\u([0-9A-Fa-f]{4})""")
    }
}
