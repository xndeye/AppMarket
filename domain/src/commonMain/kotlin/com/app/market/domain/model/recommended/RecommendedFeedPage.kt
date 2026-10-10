package com.app.market.domain.model.recommended

/** One page of Golden Mi Award feed entries plus whether the server reports more pages to load. */
data class RecommendedFeedPage(
    val items: List<RecommendedFeaturedItem>,
    val hasMore: Boolean,
)
