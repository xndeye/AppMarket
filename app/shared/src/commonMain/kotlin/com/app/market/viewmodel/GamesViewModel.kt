package com.app.market.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.market.domain.model.market.GameCatalog
import com.app.market.domain.model.market.GameFilters
import com.app.market.domain.model.market.GameQuery
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.hasSameConditions
import com.app.market.domain.model.market.initialFilters
import com.app.market.domain.model.market.isDownloadBlocked
import com.app.market.domain.model.market.isReservation
import com.app.market.domain.repository.DownloadRepository
import com.app.market.domain.repository.PackageRepository
import com.app.market.domain.repository.TapTapRepository
import com.app.market.platform.UiPlatform
import com.app.market.resources.Res
import com.app.market.resources.download_failed
import com.app.market.ui.model.AppActionKind
import com.app.market.ui.model.actionKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

data class GamesUiState(
    val catalog: GameCatalog?,
    val query: GameQuery?,
    val games: List<MarketAppInfo>,
    val catalogLoading: Boolean,
    val loading: Boolean,
    val loadingMore: Boolean,
    val hasMore: Boolean,
    val catalogError: String,
    val listError: String,
    val generation: Long,
)

class GamesViewModel(
    private val tapTap: TapTapRepository,
    private val packages: PackageRepository,
    private val downloads: DownloadRepository,
    private val uiPlatform: UiPlatform,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        GamesUiState(
            catalog = tapTap.cachedGameCategories,
            query = null,
            games = emptyList(),
            catalogLoading = false,
            loading = false,
            loadingMore = false,
            hasMore = false,
            catalogError = "",
            listError = "",
            generation = 0L,
        )
    )
    val uiState = _uiState.asStateFlow()
    val downloadStates = downloads.states
    private val pendingDownloads = mutableSetOf<String>()
    private var catalogJob: Job? = null
    private var listJob: Job? = null
    private var nextPage = ""
    private var sessionId = ""

    init {
        viewModelScope.launch {
            packages.changes.collect { change ->
                _uiState.update { state ->
                    state.copy(games = state.games.map { app ->
                        if (app.packageName != change.packageName) app else app.copy(
                            installedVersionCode = change.installedVersionCode ?: 0L,
                            installedVersionName = change.installedVersionName,
                        )
                    })
                }
            }
        }
    }

    fun onAction(app: MarketAppInfo) {
        if (app.actionKind() != AppActionKind.OPEN) {
            download(app)
            return
        }
        if (!packages.openApp(app.packageName) && !packages.openLink(app.openLink)) {
            uiPlatform.showToast(app.displayName)
        }
    }

    fun download(app: MarketAppInfo) {
        if (app.isDownloadBlocked() || app.isReservation() || !pendingDownloads.add(app.packageName)) return
        viewModelScope.launch {
            try {
                runCatchingCancellable {
                    if (app.actionKind() == AppActionKind.UPDATE) tapTap.downloadUpdateMeta(app)
                    else tapTap.downloadMeta(app)
                }.onSuccess { downloads.start(it) }
                    .onFailure { uiPlatform.showToast(it.message ?: getString(Res.string.download_failed)) }
            } finally {
                pendingDownloads.remove(app.packageName)
            }
        }
    }

    fun installDownloaded(packageName: String) = downloads.install(packageName)
    fun cancelDownload(packageName: String) = downloads.cancel(packageName)

    fun loadCategories() {
        if (catalogJob?.isActive == true) return
        val cached = _uiState.value.catalog
        if (_uiState.value.query == null && cached != null) selectInitialQuery(cached)
        _uiState.update { it.copy(catalogLoading = true, catalogError = "") }
        catalogJob = viewModelScope.launch {
            val result = runCatchingCancellable { tapTap.getGameCategories() }
            _uiState.update {
                it.copy(catalogLoading = false, catalogError = result.exceptionOrNull()?.let { error -> error.message ?: error.toString() }.orEmpty())
            }
            result.onSuccess { catalog ->
                _uiState.update { it.copy(catalog = catalog) }
                if (_uiState.value.query == null) selectInitialQuery(catalog)
            }
        }
    }

    private fun selectInitialQuery(catalog: GameCatalog) {
        selectQuery(GameQuery(catalog.categories.first().value, catalog.sorts.first().value, catalog.initialFilters()))
    }

    fun selectCategory(value: String) {
        val query = _uiState.value.query ?: return
        if (query.category != value) selectQuery(query.copy(category = value))
    }

    fun selectSort(value: String) {
        val query = _uiState.value.query ?: return
        if (query.sort != value) selectQuery(query.copy(sort = value))
    }

    fun applyFilters(filters: GameFilters) {
        val query = _uiState.value.query ?: return
        if (!query.filters.hasSameConditions(filters)) selectQuery(query.copy(filters = filters))
    }

    private fun selectQuery(query: GameQuery) {
        listJob?.cancel()
        nextPage = ""
        sessionId = ""
        _uiState.update {
            it.copy(
                query = query,
                games = emptyList(),
                loading = true,
                loadingMore = false,
                hasMore = false,
                listError = "",
                generation = it.generation + 1,
            )
        }
        loadPage()
    }

    fun retry() {
        val state = _uiState.value
        if (state.loading || state.loadingMore) return
        val query = state.query ?: return
        if (nextPage.isBlank()) selectQuery(query) else loadMore()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore || !state.hasMore) return
        _uiState.update { it.copy(loadingMore = true, listError = "") }
        loadPage()
    }

    private fun loadPage() {
        val query = _uiState.value.query ?: return
        val generation = _uiState.value.generation
        val cursor = nextPage
        val session = sessionId
        listJob = viewModelScope.launch {
            val result = runCatchingCancellable { tapTap.getCategoryGames(query, cursor, session) }
            coroutineContext.ensureActive()
            if (_uiState.value.generation != generation) return@launch
            result.onSuccess { page ->
                nextPage = page.nextPage
                sessionId = page.sessionId
                _uiState.update {
                    it.copy(games = (it.games + page.items).distinctBy(MarketAppInfo::appId), hasMore = page.hasMore)
                }
            }
            _uiState.update {
                it.copy(loading = false, loadingMore = false, listError = result.exceptionOrNull()?.let { error -> error.message ?: error.toString() }.orEmpty())
            }
        }
    }
}
