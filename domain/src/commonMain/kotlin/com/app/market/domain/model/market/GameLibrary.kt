package com.app.market.domain.model.market

data class GameOption(val label: String, val value: String, val icon: String)

data class GameFilter(
    val key: String,
    val label: String,
    val options: List<GameOption>,
)

data class GameRatingRange(val min: Int, val max: Int, val step: Int)

data class GameCatalog(
    val categories: List<GameOption>,
    val sorts: List<GameOption>,
    val filters: List<GameFilter>,
    val rating: GameRatingRange,
    val selectedStatuses: List<String>,
)

data class GameFilters(
    val statuses: List<String>,
    val apkSize: String,
    val releasedAt: String,
    val runEnvironment: String,
    val tapFeature: String,
    val ratingMin: Int,
    val ratingMax: Int,
)

fun GameCatalog.initialFilters(): GameFilters = GameFilters(
    statuses = selectedStatuses,
    apkSize = "",
    releasedAt = "",
    runEnvironment = "",
    tapFeature = "",
    ratingMin = rating.min,
    ratingMax = rating.max,
)

data class GameQuery(val category: String, val sort: String, val filters: GameFilters)

fun GameFilters.hasSameConditions(other: GameFilters): Boolean =
    copy(statuses = statuses.sorted()) == other.copy(statuses = other.statuses.sorted())

data class GamePage(
    val items: List<MarketAppInfo>,
    val hasMore: Boolean,
    val nextPage: String,
    val sessionId: String,
)
