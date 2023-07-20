package com.xjanxjan

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.helper.GogoHelper
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class MyAVProvider : MainAPI() {
    override var mainUrl = "https://myav.com/en"
    override var name = "MyAVProvider"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(
        TvType.NSFW,
    )

    override val mainPage = mainPageOf(
        "new?page=" to "Recent update",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}$page").document
        val home = document.select("div.list-drama div.item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val title = this.selectFirst("span.title")?.text()?.trim() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.pic img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search.html?keyword=$query").document
        return document.select("div.list-drama div.item").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.barContentInfo a")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.barContentInfo img")?.attr("src"))
        val tags = document.select("div.barContentInfo p:contains(Genres:) a").map { it.text().removePrefix(",").trim() }

        val year = document.selectFirst("div.barContentInfo p.type.Releasea")?.text()?.trim()?.toIntOrNull()
        val status = getStatus(document.selectFirst("div.barContentInfo p:contains(Status:)")?.ownText()?.trim())
        val description = document.selectFirst("div.barContentInfo p.des")?.nextElementSiblings()?.select("p")?.text()

        val episodes = document.select("ul.listing li").map {
            val name = it.selectFirst("a")?.attr("title")
            val link = fixUrlNull(it.selectFirst("a")?.attr("href"))
            val epNum = Regex("Episode\\s(\\d+)").find("$name")?.groupValues?.getOrNull(1)?.toIntOrNull()
            newEpisode(link) {
                this.name = name
                this.episode = epNum
            }
        }.reversed()

        if (episodes.size == 1) {
            return newMovieLoadResponse(title, url, TvType.Movie, episodes[0].data) {
                posterUrl = poster
                this.year = year
                plot = description
                this.tags = tags
            }
        } else {
            return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes = episodes) {
                posterUrl = poster
                this.year = year
                showStatus = status
                plot = description
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document
        val server = document.selectFirst("select#selectServer option")?.attr("value")
        val iframe = app.get(httpsify(server ?: return false))
        val iframeDoc = iframe.document

        argamap({
            iframeDoc.select(".list-server-items > .linkserver")
                .forEach { element ->
                    val status = element.attr("data-status") ?: return@forEach
                    if (status != "1") return@forEach
                    val extractorData = element.attr("data-video") ?: return@forEach
                    loadExtractor(extractorData, iframe.url, subtitleCallback, callback)
                }
        }, {
            val iv = "9262859232435825"
            val secretKey = "93422192433952489752342908585752"
            val secretDecryptKey = secretKey
            GogoHelper.extractVidstream(
                iframe.url,
                this.name,
                callback,
                iv,
                secretKey,
                secretDecryptKey,
                isUsingAdaptiveKeys = false,
                isUsingAdaptiveData = true,
                iframeDocument = iframeDoc
            )
        })

        return true
    }

}
