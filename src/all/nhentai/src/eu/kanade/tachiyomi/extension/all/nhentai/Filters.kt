package eu.kanade.tachiyomi.extension.all.nhentai

import eu.kanade.tachiyomi.source.model.Filter

private const val NO_PRESET = "None"

internal class AdvSearchNote(note: String) : Filter.Header(note)

internal open class AdvSearchEntryFilter(name: String) : Filter.Text(name)

internal class TagFilter : AdvSearchEntryFilter("Tags")
internal class CategoryFilter : AdvSearchEntryFilter("Categories")
internal class GroupFilter : AdvSearchEntryFilter("Groups")
internal class ArtistFilter : AdvSearchEntryFilter("Artists")
internal class ParodyFilter : AdvSearchEntryFilter("Parodies")
internal class CharactersFilter : AdvSearchEntryFilter("Characters")
internal class UploadedFilter : AdvSearchEntryFilter("Uploaded")
internal class PagesFilter : AdvSearchEntryFilter("Pages")
internal class OffsetPageFilter : Filter.Text("Offset results by # pages")
internal class FavoriteFilter : Filter.CheckBox("Show favorites only", false)

internal class LoadPresetFilter(presetNames: List<String>) :
    Filter.Select<String>(
        "Load saved filter preset",
        (listOf(NO_PRESET) + presetNames).toTypedArray(),
    ) {
    private val presetNames = presetNames.toList()

    val selectedPresetName: String?
        get() = presetNames.getOrNull(state - 1)
}

internal class SavePresetNameFilter : Filter.Text("Preset name to save")

internal class SavePresetFilter : Filter.CheckBox("Save or overwrite this preset", false)

internal open class UriPartFilter(
    displayName: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
    fun toUriPart() = options[state].second
}

internal class SortFilter :
    UriPartFilter(
        "Sort By",
        arrayOf(
            "Popular: All Time" to "popular",
            "Popular: Month" to "popular-month",
            "Popular: Week" to "popular-week",
            "Popular: Today" to "popular-today",
            "Recent" to "date",
        ),
    )
