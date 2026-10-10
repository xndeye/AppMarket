package com.app.market.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.market.domain.model.download.DownloadState
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.SearchPage
import com.app.market.domain.model.market.isDownloadBlocked
import com.app.market.domain.model.market.isReservation
import com.app.market.domain.repository.DownloadRepository
import com.app.market.domain.repository.MarketSourceRepository
import com.app.market.domain.repository.PackageRepository
import com.app.market.domain.repository.SearchHistoryRepository
import com.app.market.domain.repository.UpdatePreferencesRepository
import com.app.market.platform.UiPlatform
import com.app.market.resources.Res
import com.app.market.resources.search_failed
import com.app.market.ui.model.AppActionKind
import com.app.market.ui.model.SearchResultItem
import com.app.market.ui.model.resolveActionKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/** 每个来源独立翻页，各自的结尾位置可能不同。 */
@Immutable
data class SourcePaging(val page: Int = 0, val hasMore: Boolean = false)

@Immutable
data class SearchUiState(
    val keyword: String = "",
    val loading: Boolean = false,
    val errorMessage: String = "",
    val showNoResults: Boolean = false,
    val results: List<SearchResultItem> = emptyList(),
    val history: List<String> = emptyList(),
    /** 当前结果对应的已提交关键词，可以与正在编辑的输入不同。 */
    val activeKeyword: String = "",
    val paging: Map<AppSource, SourcePaging> = emptyMap(),
    val loadingMore: Boolean = false,
    val loadMoreError: String = "",
    /** 新搜索或清空输入时递增，使旧翻页请求失效并重置列表位置。 */
    val searchEpoch: Int = 0,
    val sources: Set<AppSource> = AppSource.Default,
) {
    val hasMore: Boolean get() = paging.values.any { it.hasMore }
}

/** Cap pages fetched per load-more trigger so an all-duplicate tail can't spin indefinitely. */
private const val MAX_PAGES_PER_LOAD = 5

class SearchViewModel(
    private val sources: MarketSourceRepository,
    private val historyStore: SearchHistoryRepository,
    private val updatePrefs: UpdatePreferencesRepository,
    private val packages: PackageRepository,
    private val downloads: DownloadRepository,
    private val uiPlatform: UiPlatform,
    private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState(sources = updatePrefs.searchSources.value))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val downloadStates: StateFlow<Map<String, DownloadState>> = downloads.states
    private val pendingDownloads = mutableSetOf<String>()
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var initialKeywordConsumed = false

    init {
        viewModelScope.launch { reloadHistory() }
        viewModelScope.launch {
            updatePrefs.searchSources.collect { sources ->
                val changed = _uiState.value.sources != sources
                _uiState.update { it.copy(sources = sources) }
                // 源变了旧结果就不作数，按当前关键词重搜
                if (changed) rerunActiveSearch()
            }
        }
        viewModelScope.launch {
            packages.changes.collect { change ->
                _uiState.update { state ->
                    state.copy(
                        results = state.results.map { item ->
                            if (item.app.packageName != change.packageName) item
                            else {
                                val installedVersionCode = change.installedVersionCode ?: 0L
                                val app = item.app.copy(
                                    installedVersionCode = installedVersionCode,
                                    installedVersionName = change.installedVersionName,
                                )
                                item.copy(
                                    app = app,
                                    actionKind = searchActionKind(app, installedVersionCode),
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    fun setKeyword(value: String) {
        // 清空输入时取消请求并重置结果和分页。
        if (value.isBlank()) {
            searchJob?.cancel()
            searchJob = null
            loadMoreJob?.cancel()
            loadMoreJob = null
            _uiState.update {
                it.copy(
                    keyword = value,
                    loading = false,
                    results = emptyList(),
                    showNoResults = false,
                    errorMessage = "",
                    activeKeyword = "",
                    paging = emptyMap(),
                    loadingMore = false,
                    loadMoreError = "",
                    searchEpoch = it.searchEpoch + 1,
                )
            }
        } else {
            _uiState.update { it.copy(keyword = value) }
        }
    }

    fun selectSource(source: AppSource): Job = viewModelScope.launch {
        updatePrefs.setSearchSources(setOf(source))
    }

    fun searchWith(keyword: String) {
        _uiState.update { it.copy(keyword = keyword) }
        runSearch()
    }

    fun searchInitialKeyword(keyword: String) {
        if (initialKeywordConsumed) return
        initialKeywordConsumed = true
        searchWith(keyword)
    }

    fun runSearch() {
        val keyword = _uiState.value.keyword.trim()
        if (keyword.isBlank()) return
        executeSearch(keyword, recordHistory = true)
    }

    private fun rerunActiveSearch() {
        val keyword = _uiState.value.activeKeyword
        if (keyword.isBlank()) return
        executeSearch(keyword, recordHistory = false)
    }

    fun retrySearch() = rerunActiveSearch()

    private fun executeSearch(keyword: String, recordHistory: Boolean) {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        _uiState.update {
            it.copy(
                loading = true,
                loadingMore = false,
                loadMoreError = "",
                errorMessage = "",
                showNoResults = false,
                results = emptyList(),
                activeKeyword = keyword,
                paging = emptyMap(),
                searchEpoch = it.searchEpoch + 1,
            )
        }
        if (recordHistory) {
            // 历史写入独立于 searchJob：清空搜索会取消 searchJob，不应连带丢掉刚搜过的词
            viewModelScope.launch {
                historyStore.add(keyword)
                reloadHistory()
            }
        }
        searchJob = viewModelScope.launch {
            val sources = _uiState.value.sources
            val fetched = fetchPages(keyword, sources.associateWith { 0 })
            // 单源故障不该挡住另一源的结果
            if (fetched.values.none { it.isSuccess }) {
                val message = searchError(firstError(fetched))
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(loading = false, errorMessage = message) }
                return@launch
            }
            val resolved = runCatchingCancellable {
                toItems(mergeApps(emptyList(), orderedApps(fetched)), emptyList())
            }
            if (resolved.isFailure) {
                val message = searchError(resolved.exceptionOrNull())
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(loading = false, errorMessage = message) }
                return@launch
            }
            val items = resolved.getOrThrow()
            currentCoroutineContext().ensureActive()
            _uiState.update {
                it.copy(
                    loading = false,
                    results = items,
                    activeKeyword = keyword,
                    paging = pagingOf(fetched, pages = sources.associateWith { 0 }),
                    showNoResults = items.isEmpty(),
                )
            }
        }
    }

    /**
     * Appends the next page(s), skipping all-duplicate pages so each trigger makes progress. Aborts if a
     * newer search supersedes this one (tracked by [SearchUiState.activeKeyword] + [SearchUiState.searchEpoch]).
     */
    fun loadMore() {
        val snapshot = _uiState.value
        if (snapshot.loading || snapshot.loadingMore || !snapshot.hasMore || snapshot.activeKeyword.isBlank()) return
        val baseKeyword = snapshot.activeKeyword
        val baseEpoch = snapshot.searchEpoch
        _uiState.update { it.copy(loadingMore = true, loadMoreError = "") }
        loadMoreJob = viewModelScope.launch {
            fun superseded() = _uiState.value.let { it.activeKeyword != baseKeyword || it.searchEpoch != baseEpoch }
            var paging = snapshot.paging
            var addedAny = false
            var failureMessage = ""
            var attempts = 0
            while (!addedAny && attempts < MAX_PAGES_PER_LOAD && paging.values.any { it.hasMore } && !superseded()) {
                attempts++
                val nextPages = paging.filterValues { it.hasMore }.mapValues { (_, state) -> state.page + 1 }
                val fetched = fetchPages(baseKeyword, nextPages)
                if (fetched.values.none { it.isSuccess }) {
                    failureMessage = searchError(firstError(fetched))
                    break
                }
                // 失败的源保留原页码，下次触发重试
                paging = paging + pagingOf(fetched, nextPages)
                val incoming = orderedApps(fetched)
                // Dedup + commit under the staleness guard: a superseded search flips it, so its page is dropped.
                val before = _uiState.value
                if (before.activeKeyword != baseKeyword || before.searchEpoch != baseEpoch) break
                val mergedApps = mergeApps(before.results.map { it.app }, incoming)
                addedAny = mergedApps.size > before.results.size
                val result = runCatchingCancellable { toItems(mergedApps, before.results) }
                if (result.isFailure) {
                    failureMessage = searchError(result.exceptionOrNull())
                    break
                }
                val resolved = result.getOrThrow()
                currentCoroutineContext().ensureActive()
                val committedPaging = paging
                _uiState.update { state ->
                    if (state.activeKeyword != baseKeyword || state.searchEpoch != baseEpoch) state
                    else state.copy(results = resolved, paging = committedPaging)
                }
            }
            // 分页失败时保留已有结果与页码，等待用户重试。
            currentCoroutineContext().ensureActive()
            _uiState.update { state ->
                when {
                    state.activeKeyword != baseKeyword || state.searchEpoch != baseEpoch -> state
                    failureMessage.isNotEmpty() -> state.copy(loadingMore = false, loadMoreError = failureMessage)
                    addedAny -> state.copy(loadingMore = false)
                    else -> state.copy(
                        loadingMore = false,
                        paging = state.paging.mapValues { (_, value) -> value.copy(hasMore = false) },
                    )
                }
            }
        }
    }

    /** 各源并发取指定页，单源异常收进 Result 交调用方判定。 */
    private suspend fun fetchPages(keyword: String, pages: Map<AppSource, Int>): Map<AppSource, Result<SearchPage>> =
        coroutineScope {
            pages.map { (source, page) ->
                source to async { runCatchingCancellable { searchSource(source, keyword, page) } }
            }.associate { (source, deferred) -> source to deferred.await() }
        }

    private suspend fun searchSource(source: AppSource, keyword: String, page: Int): SearchPage = when (source) {
        else -> sources.search(source, keyword, page)
    }

    /** 小米在前，让合并结果保持主源的相关性排序。 */
    private fun orderedApps(fetched: Map<AppSource, Result<SearchPage>>): List<MarketAppInfo> =
        AppSource.entries.flatMap { fetched[it]?.getOrNull()?.items.orEmpty() }

    private fun pagingOf(
        fetched: Map<AppSource, Result<SearchPage>>,
        pages: Map<AppSource, Int>,
    ): Map<AppSource, SourcePaging> = fetched.mapNotNull { (source, result) ->
        val page = result.getOrNull() ?: return@mapNotNull null
        source to SourcePaging(pages[source] ?: 0, page.hasMore)
    }.toMap()

    private fun firstError(fetched: Map<AppSource, Result<SearchPage>>): Throwable? =
        fetched.values.firstNotNullOfOrNull { it.exceptionOrNull() }

    private suspend fun searchError(error: Throwable?): String =
        error?.message?.takeIf { it.isNotBlank() } ?: getString(Res.string.search_failed)

    /** 同包名保留高版本；位置由先入者定，避免翻页补进来的条目把列表重排。 */
    private fun mergeApps(existing: List<MarketAppInfo>, incoming: List<MarketAppInfo>): List<MarketAppInfo> {
        val byPackage = LinkedHashMap<String, MarketAppInfo>(existing.size + incoming.size)
        existing.forEach { byPackage[it.packageName] = it }
        incoming.forEach { app ->
            val current = byPackage[app.packageName]
            if (current == null || app.versionCode > current.versionCode) byPackage[app.packageName] = app
        }
        return byPackage.values.toList()
    }

    /** Resolves each app's install/update/open action off-main (cached PackageManager lookup). */
    private suspend fun toItems(
        apps: List<MarketAppInfo>,
        existing: List<SearchResultItem>,
    ): List<SearchResultItem> {
        if (apps.isEmpty()) return emptyList()
        val known = existing.associateBy { it.app.packageName }
        // 只对新增或被替换的条目重查安装状态，翻页不整表重算
        val changed = apps.filter { known[it.packageName]?.app != it }
        if (changed.isEmpty()) return apps.mapNotNull { known[it.packageName] }
        val installed = packages.installedVersionCodes(changed.map { it.packageName })
        val resolved = changed.associate { app ->
            val cachedVersionCode = installed[app.packageName] ?: 0L
            // A stale package-list cache can make a system app that was just upgraded look older
            // than the store result. Verify only would-be updates with a direct PackageManager
            // lookup before exposing the update action.
            val installedVersionCode = if (cachedVersionCode in 1 until app.versionCode) {
                packages.freshInstalledVersionCode(app.packageName) ?: cachedVersionCode
            } else {
                cachedVersionCode
            }
            val resolvedApp = app.copy(installedVersionCode = installedVersionCode)
            app.packageName to SearchResultItem(
                resolvedApp,
                searchActionKind(resolvedApp, installedVersionCode),
            )
        }
        return apps.mapNotNull { resolved[it.packageName] ?: known[it.packageName] }
    }

    private fun searchActionKind(app: MarketAppInfo, installedVersionCode: Long): AppActionKind =
        if (app.isReservation()) AppActionKind.RESERVE
        else if (installedVersionCode > 0L && app.isDownloadBlocked()) AppActionKind.OPEN
        else resolveActionKind(installedVersionCode, app.versionCode)

    fun removeHistory(keyword: String) {
        viewModelScope.launch {
            historyStore.remove(keyword)
            reloadHistory()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyStore.clear()
            reloadHistory()
        }
    }

    fun onAction(item: SearchResultItem) {
        if (item.actionKind == AppActionKind.RESERVE) return
        if (item.actionKind == AppActionKind.OPEN) {
            // OPPO/三星/华为的 openLink 只是商店深链回退，不是已安装应用的启动目标
            val opened = if (item.app.source.capabilities.prefersOpenLinkLaunch) {
                packages.openLink(item.app.openLink) || packages.openApp(item.app.packageName)
            } else {
                packages.openApp(item.app.packageName) || packages.openLink(item.app.openLink)
            }
            if (!opened) {
                uiPlatform.showToast(item.app.displayName)
            }
            return
        }
        download(item.app, item.actionKind == AppActionKind.UPDATE)
    }

    fun download(app: MarketAppInfo, update: Boolean = false) {
        startDownload(app, update)
    }

    private fun startDownload(app: MarketAppInfo, update: Boolean) {
        if (app.isDownloadBlocked()) return
        if (!pendingDownloads.add(app.packageName)) return
        val keyword = _uiState.value.activeKeyword
        // 已确认的下载操作独立于搜索页面；主线程串行维护待处理集合。
        applicationScope.launch(Dispatchers.Main.immediate) {
            try {
                runCatchingCancellable {
                    if (update) {
                        sources.downloadUpdateMeta(app.source, app)
                    } else {
                        sources.downloadMeta(app.source, app, keyword)
                    }
                }
                    .onSuccess { downloads.start(it) }
                    .onFailure { uiPlatform.showToast(it.message ?: "Download failed") }
            } finally {
                pendingDownloads.remove(app.packageName)
            }
        }
    }

    fun installDownloaded(packageName: String) = downloads.install(packageName)
    fun cancelDownload(packageName: String) = downloads.cancel(packageName)
    fun clearDownload(packageName: String) = downloads.clear(packageName)

    private suspend fun reloadHistory() {
        val history = historyStore.load()
        _uiState.update { it.copy(history = history) }
    }
}
