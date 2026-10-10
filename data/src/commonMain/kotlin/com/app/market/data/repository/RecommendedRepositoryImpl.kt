package com.app.market.data.repository

import com.app.market.data.remote.xiaomi.XiaomiApi
import com.app.market.domain.model.recommended.RecommendedArticle
import com.app.market.domain.model.recommended.RecommendedFeedPage
import com.app.market.domain.repository.AccountRepository
import com.app.market.domain.repository.ProfileRepository
import com.app.market.domain.repository.RecommendedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 推荐页金米奖仓库，网络请求和解析统一由 commonMain 中的 [XiaomiApi] 实现。
 * The platform supplies the device [profileStore] and account [cookies] (empty = anonymous on desktop).
 */
internal class RecommendedRepositoryImpl(
    private val api: XiaomiApi,
    private val profileStore: ProfileRepository,
    private val cookies: AccountRepository,
) : RecommendedRepository {

    override suspend fun goldMiFeed(page: Int, pageSize: Int): RecommendedFeedPage = withContext(Dispatchers.Default) {
        api.goldMiFeed(page, pageSize, profileStore.load(), cookies.cookie())
    }

    override suspend fun recommendedArticle(rId: String): RecommendedArticle = withContext(Dispatchers.Default) {
        api.recommendedArticle(rId, profileStore.load(), cookies.cookie())
    }
}
