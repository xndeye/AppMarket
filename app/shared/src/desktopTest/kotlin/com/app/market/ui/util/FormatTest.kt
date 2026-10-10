package com.app.market.ui.util

import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.MarketAppInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test
    fun recommendedSummaryUsesCategoryAndInstallCount() {
        val app = MarketAppInfo(
            appId = 30_755_227L,
            packageName = "com.example.beauty",
            displayName = "获奖应用",
            publisherName = "开发者",
            versionName = "1.0",
            versionCode = 1L,
            icon = "",
            apkSize = 0L,
            ratingScore = 0.0,
            source = AppSource.OPPO,
            category = "经营策略",
            downloadCount = 21_923_487L,
        )

        assertEquals("经营策略 | 2192.3万次安装", app.recommendedAppSummary())
    }

    @Test
    fun recommendedSummaryFallsBackWhenStoreMetadataIsAbsent() {
        val app = MarketAppInfo(
            appId = 1L,
            packageName = "com.example.app",
            displayName = "应用",
            publisherName = "开发者",
            versionName = "1.0",
            versionCode = 1L,
            icon = "",
            apkSize = 0L,
            ratingScore = 0.0,
        )

        assertEquals("开发者", app.recommendedAppSummary())
    }
}
