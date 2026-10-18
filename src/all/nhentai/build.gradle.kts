import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NHentai"
    versionCode = 57
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("en", "all").forEach { language ->
        source {
            lang = language
            baseUrl = "https://nhentai.net"

            if (language == "all") {
                id = 7309872737163460316L
            }
        }
    }

    deeplink {
        host("nhentai.net")
        path("/g/.*")
    }
}

dependencies {
    implementation(project(":lib:randomua"))
}
