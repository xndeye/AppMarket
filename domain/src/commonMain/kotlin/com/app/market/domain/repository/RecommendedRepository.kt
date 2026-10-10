package com.app.market.domain.repository

import com.app.market.domain.model.recommended.RecommendedArticle
import com.app.market.domain.model.recommended.RecommendedFeedPage

/**
 * 通过小米应用商店接口加载推荐页金米奖内容。
 *
 * Implementations live in the platform layer and resolve the device profile + account cookie
 * internally, mirroring [MarketRepository].
 */
interface RecommendedRepository {
    /** Loads one page of Golden Mi Award apps from Xiaomi Market's `zone/goldMiV2` endpoint. */
    suspend fun goldMiFeed(page: Int = 0, pageSize: Int = 9): RecommendedFeedPage

    /** Loads the article for a feed entry: header image + rich-text body + embedded app. */
    suspend fun recommendedArticle(rId: String): RecommendedArticle
}
