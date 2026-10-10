package com.app.market.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.domain.model.market.GameCatalog
import com.app.market.domain.model.market.GameFilters
import com.app.market.domain.model.market.GameOption
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.domain.model.market.hasSameConditions
import com.app.market.domain.model.market.initialFilters
import com.app.market.resources.Res
import com.app.market.resources.cancel
import com.app.market.resources.games_filters
import com.app.market.resources.games_apk_size
import com.app.market.resources.games_load_more
import com.app.market.resources.games_rating
import com.app.market.resources.games_reset
import com.app.market.resources.games_status
import com.app.market.resources.games_unlimited
import com.app.market.resources.games_view_results
import com.app.market.resources.install
import com.app.market.resources.nav_games
import com.app.market.resources.no_results
import com.app.market.resources.open
import com.app.market.resources.reserve
import com.app.market.resources.retry
import com.app.market.resources.update
import com.app.market.ui.component.AppAsyncImage
import com.app.market.ui.component.AppButton
import com.app.market.ui.component.AppButtonText
import com.app.market.ui.component.AppRow
import com.app.market.ui.component.AppTextButton
import com.app.market.ui.component.CardSegmentContainer
import com.app.market.ui.component.LoadingBox
import com.app.market.ui.component.MainTabScaffold
import com.app.market.ui.component.PageVerticalPadding
import com.app.market.ui.model.AppActionKind
import com.app.market.ui.model.actionKind
import com.app.market.viewmodel.GamesViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.RangeSlider
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun GamesTab(viewModel: GamesViewModel, bottomPadding: Dp, onOpenDetail: (MarketAppInfo) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentState by rememberUpdatedState(state)
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showFilters by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.loadCategories() }
    LaunchedEffect(state.generation) { listState.scrollToItem(0) }
    LaunchedEffect(listState, viewModel) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= 0 && lastVisible >= info.totalItemsCount - 3 &&
                    currentState.hasMore && !currentState.loading && !currentState.loadingMore &&
                    currentState.listError.isBlank()
        }.distinctUntilChanged().collect { if (it) viewModel.loadMore() }
    }

    MainTabScaffold(title = stringResource(Res.string.nav_games)) { topPadding, backdropModifier, scrollBehavior ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().then(backdropModifier).scrollEndHaptic().overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = topPadding + PageVerticalPadding,
                bottom = bottomPadding + PageVerticalPadding,
            ),
        ) {
            val catalog = state.catalog
            val query = state.query
            if (state.catalogError.isNotBlank()) {
                item(key = "catalog_error") {
                    Column(Modifier.padding(horizontal = 28.dp)) {
                        Text(state.catalogError, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        AppTextButton(stringResource(Res.string.retry), viewModel::loadCategories, enabled = !state.catalogLoading)
                    }
                }
            }
            if (catalog != null && query != null) {
                item(key = "categories") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(catalog.categories, key = { it.value }) { category ->
                            val selected = category.value == query.category
                            Card(
                                modifier = Modifier.width(72.dp).semantics { this.selected = selected },
                                onClick = { viewModel.selectCategory(category.value) },
                                showIndication = true,
                                colors = CardDefaults.defaultColors(
                                    color = if (selected) MiuixTheme.colorScheme.tertiaryContainer
                                    else MiuixTheme.colorScheme.surface,
                                ),
                                insideMargin = PaddingValues(8.dp),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    AppAsyncImage(
                                        url = category.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                    )
                                    Text(
                                        category.label,
                                        style = MiuixTheme.textStyles.body2,
                                        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "sort_filters") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TabRowWithContour(
                            tabs = catalog.sorts.map { it.label },
                            selectedTabIndex = catalog.sorts.indexOfFirst { it.value == query.sort }.coerceAtLeast(0),
                            onTabSelected = { viewModel.selectSort(catalog.sorts[it].value) },
                            modifier = Modifier.weight(1f),
                            minWidth = 48.dp,
                            maxWidth = 72.dp,
                        )
                        AppButton(
                            onClick = { showFilters = true },
                            minWidth = 0.dp,
                            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                color = Color.Transparent,
                            ),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Filter,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(18.dp),
                            )
                            AppButtonText(
                                text = stringResource(Res.string.games_filters),
                                modifier = Modifier.padding(start = 4.dp),
                                color = if (query.filters.hasSameConditions(catalog.initialFilters()))
                                    MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            if ((state.catalogLoading && catalog == null) || state.loading) {
                item(key = "loading") { LoadingBox() }
            }
            if (query != null && !state.loading && !state.loadingMore && !state.hasMore &&
                state.listError.isBlank() && state.games.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(Res.string.no_results),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 28.dp),
                    )
                }
            }
            itemsIndexed(state.games, key = { _, app -> app.appId }) { index, app ->
                CardSegmentContainer(isFirst = index == 0, isLast = index == state.games.lastIndex) {
                    val kind = app.actionKind()
                    AppRow(
                        app = app,
                        actionText = stringResource(when (kind) {
                            AppActionKind.INSTALL -> Res.string.install
                            AppActionKind.UPDATE -> Res.string.update
                            AppActionKind.OPEN -> Res.string.open
                            AppActionKind.RESERVE -> Res.string.reserve
                        }),
                        actionKind = kind,
                        downloadState = downloadStates[app.packageName],
                        onOpenDetail = { onOpenDetail(app) },
                        onAction = { viewModel.onAction(app) },
                        onResumeDownload = { viewModel.download(app) },
                        onInstallDownloaded = viewModel::installDownloaded,
                        onCancel = viewModel::cancelDownload,
                        showSearchTags = true,
                        grouped = true,
                    )
                }
            }
            if (state.listError.isNotBlank()) {
                item(key = "list_error") {
                    Column(Modifier.padding(horizontal = 28.dp)) {
                        Text(state.listError, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        AppTextButton(stringResource(Res.string.retry), viewModel::retry)
                    }
                }
            }
            if (state.loadingMore) {
                item(key = "loading_more") { LoadingBox() }
            } else if (state.hasMore && state.listError.isBlank()) {
                item(key = "more") {
                    AppTextButton(
                        stringResource(Res.string.games_load_more), viewModel::loadMore,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
        }
    }

    val catalog = state.catalog
    val query = state.query
    if (catalog != null && query != null) {
        GameFiltersSheet(showFilters, catalog, query.filters, { showFilters = false }, {
            viewModel.applyFilters(it)
            showFilters = false
        })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameFiltersSheet(
    show: Boolean,
    catalog: GameCatalog,
    filters: GameFilters,
    onDismiss: () -> Unit,
    onApply: (GameFilters) -> Unit,
) {
    var draft by remember(show, filters) { mutableStateOf(filters) }
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.7f }
    OverlayBottomSheet(
        show = show,
        title = stringResource(Res.string.games_filters),
        endAction = { AppTextButton(stringResource(Res.string.games_reset), { draft = catalog.initialFilters() }) },
        onDismissRequest = onDismiss,
        enableNestedScroll = true,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .scrollEndHaptic().overScrollVertical(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                catalog.filters.forEach { filter ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val title = when (filter.key) {
                            "status" -> stringResource(Res.string.games_status)
                            "apk_size" -> stringResource(Res.string.games_apk_size)
                            else -> filter.label
                        }
                        Text(title, style = MiuixTheme.textStyles.body1)
                        val value = when (filter.key) {
                            "status" -> ""
                            "apk_size" -> draft.apkSize
                            "released_at" -> draft.releasedAt
                            "run_environment" -> draft.runEnvironment
                            "tap_feature" -> draft.tapFeature
                            else -> error("Unsupported TapTap filter: ${filter.key}")
                        }
                        val options = if (filter.key != "status" && filter.options.none { it.value.isEmpty() })
                            listOf(GameOption(stringResource(Res.string.games_unlimited), "", "")) + filter.options
                        else filter.options
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            options.forEach { option ->
                                val selected = if (filter.key == "status") option.value in draft.statuses else value == option.value
                                AppButton(
                                    text = option.label,
                                    modifier = Modifier.semantics { this.selected = selected },
                                    minWidth = 0.dp,
                                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    colors = if (selected) ButtonDefaults.buttonColors(
                                        color = MiuixTheme.colorScheme.tertiaryContainer,
                                        contentColor = MiuixTheme.colorScheme.primary,
                                    ) else ButtonDefaults.buttonColors(),
                                    onClick = {
                                        draft = when (filter.key) {
                                            "status" -> draft.copy(statuses = if (selected)
                                                draft.statuses - option.value else draft.statuses + option.value)
                                            "apk_size" -> draft.copy(apkSize = option.value)
                                            "released_at" -> draft.copy(releasedAt = option.value)
                                            "run_environment" -> draft.copy(runEnvironment = option.value)
                                            "tap_feature" -> draft.copy(tapFeature = option.value)
                                            else -> error("Unsupported TapTap filter: ${filter.key}")
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                val rating = catalog.rating
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(Res.string.games_rating), style = MiuixTheme.textStyles.body1)
                        Text("${draft.ratingMin} – ${draft.ratingMax}", style = MiuixTheme.textStyles.body2)
                    }
                    RangeSlider(
                        value = draft.ratingMin.toFloat()..draft.ratingMax.toFloat(),
                        onValueChange = { range ->
                            val min = rating.min + ((range.start - rating.min) / rating.step).roundToInt() * rating.step
                            val max = rating.min + ((range.endInclusive - rating.min) / rating.step).roundToInt() * rating.step
                            draft = draft.copy(
                                ratingMin = min.coerceIn(rating.min, rating.max),
                                ratingMax = max.coerceIn(min.coerceIn(rating.min, rating.max), rating.max),
                            )
                        },
                        valueRange = rating.min.toFloat()..rating.max.toFloat(),
                        showKeyPoints = true,
                        keyPoints = (rating.min..rating.max step rating.step).map(Int::toFloat),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton(stringResource(Res.string.cancel), onDismiss, modifier = Modifier.weight(1f))
                AppButton(
                    stringResource(Res.string.games_view_results), { onApply(draft) },
                    modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary(),
                )
            }
        }
    }
}
