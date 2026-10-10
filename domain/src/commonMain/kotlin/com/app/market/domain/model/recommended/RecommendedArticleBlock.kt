package com.app.market.domain.model.recommended

import com.app.market.domain.model.market.MarketAppInfo

/** One ordered content block from Xiaomi Market's `topicItemList`. */
sealed interface RecommendedArticleBlock {
    data class Banner(
        val imageUrl: String,
        val width: Int = 0,
        val height: Int = 0,
        // 低清预览地址，高清 imageUrl 加载期间作占位
        val previewImageUrl: String = imageUrl,
    ) : RecommendedArticleBlock

    /** Rich text is kept as HTML so inline text and image order is not lost. */
    data class RichText(
        val html: String,
        val imageUrls: List<String> = emptyList(),
    ) : RecommendedArticleBlock

    data class App(val value: MarketAppInfo) : RecommendedArticleBlock

    data class Image(
        val imageUrl: String,
        val width: Int = 0,
        val height: Int = 0,
    ) : RecommendedArticleBlock
}
