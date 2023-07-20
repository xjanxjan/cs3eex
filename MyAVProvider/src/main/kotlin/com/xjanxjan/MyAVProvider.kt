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

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val home = app.get("$mainUrl/api/DramaList/List?page=$page${request.data}")
            .parsedSafe<Responses>()?.data
            ?.mapNotNull { media ->
                media.toSearchResponse()
            } ?: throw ErrorLoadingException("Invalid Json reponse")
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }
