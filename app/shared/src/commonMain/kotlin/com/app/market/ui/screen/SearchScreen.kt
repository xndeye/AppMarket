package com.app.market.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.domain.model.download.DownloadState
import com.app.market.domain.model.market.AppSource
import com.app.market.domain.model.market.MarketAppInfo
import com.app.market.resources.Res
import com.app.market.resources.clear_history
import com.app.market.resources.nav_search
import com.app.market.resources.no_results
import com.app.market.resources.open
import com.app.market.resources.reserve
import com.app.market.resources.search_hint
import com.app.market.resources.search_history
import com.app.market.resources.update
import com.app.market.ui.component.AppRow
import com.app.market.ui.component.LoadingBox
import com.app.market.ui.component.MarketScaffold
import com.app.market.ui.component.PageVerticalPadding
import com.app.market.ui.component.deferredTopPadding
import com.app.market.ui.model.AppActionKind
import com.app.market.ui.navigation.PagerNavigationSpringSpec
import com.app.market.ui.util.appSourceLabel
import com.app.market.ui.util.installActionText
import com.app.market.viewmodel.SearchUiState
import com.app.market.viewmodel.SearchViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    initialKeyword: String?,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentState by rememberUpdatedState(state)
    val downloadStates = viewModel.downloadStates.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val sourceOptions = AppSource.entries
    val selectedSource = state.sources.firstOrNull() ?: AppSource.Default.first()
    val selectedSourceIndex = sourceOptions.indexOf(selectedSource)
    val pagerState = rememberPagerState(initialPage = selectedSourceIndex) { sourceOptions.size }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        // 进入页面时同步偏好，之后由停稳的分页提交来源，避免异步写入反向拉动页面。
        pagerState.scrollToPage(selectedSourceIndex)
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { page -> viewModel.selectSource(sourceOptions[page]).join() }
    }

    // 新搜索完成后重新判断底部位置；不观察 loadingMore，避免失败后在底部自动反复重试。
    LaunchedEffect(listState, viewModel) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= 0 && info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3 &&
                    !currentState.loading && currentState.hasMore && currentState.errorMessage.isBlank()
        }
            .distinctUntilChanged()
            .collect { atBottom -> if (atBottom) viewModel.loadMore() }
    }
    // 新搜索或清空输入时回到顶部，同关键词重搜也会更新代次。
    LaunchedEffect(state.searchEpoch) {
        if (state.searchEpoch > 0) listState.scrollToItem(0)
    }

    var searchExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissSearchInput: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    LaunchedEffect(initialKeyword) {
        val keyword = initialKeyword?.trim().orEmpty()
        if (keyword.isNotEmpty()) {
            viewModel.searchWith(keyword)
            dismissSearchInput()
        } else {
            searchExpanded = true
            // InputField 展开后再请求焦点，兼容其 Android 8 焦点处理
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    MarketScaffold(
        title = stringResource(Res.string.nav_search),
        onBack = onBack,
        bottomContent = { scrollBehavior ->
            val dynamicTopPadding = remember(scrollBehavior) {
                { PageVerticalPadding * (1f - scrollBehavior.state.collapsedFraction) }
            }
            Column(modifier = Modifier.deferredTopPadding(dynamicTopPadding)) {
                InputField(
                    query = state.keyword,
                    onQueryChange = viewModel::setKeyword,
                    onSearch = {
                        viewModel.runSearch()
                        dismissSearchInput()
                    },
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    label = stringResource(Res.string.search_hint),
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                )
                TabRowWithContour(
                    tabs = sourceOptions.map { appSourceLabel(it) },
                    selectedTabIndex = pagerState.currentPage,
                    onTabSelected = { index ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                page = index,
                                animationSpec = PagerNavigationSpringSpec,
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = TabRowDefaults.tabRowColors(
                        backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                        selectedBackgroundColor = MiuixTheme.colorScheme.surface,
                    ),
                    minWidth = 88.dp,
                    maxWidth = 116.dp,
                    height = 40.dp,
                )
            }
        },
    ) { innerPadding, backdropModifier, scrollBehavior ->
        val contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = innerPadding.calculateTopPadding() + PageVerticalPadding,
            bottom = innerPadding.calculateBottomPadding() + PageVerticalPadding,
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().then(backdropModifier),
            overscrollEffect = null,
        ) { page ->
            // 共用的搜索状态只属于当前来源，其他页面等待切换后的搜索结果。
            if (sourceOptions[page] != selectedSource) {
                LoadingBox(Modifier.fillMaxSize().padding(contentPadding))
                return@HorizontalPager
            }
            Crossfade(
                targetState = state.loading && state.results.isEmpty(),
                modifier = Modifier.fillMaxSize(),
                label = "search",
            ) { fullScreenLoading ->
                if (fullScreenLoading) {
                    LoadingBox(Modifier.fillMaxSize().padding(contentPadding))
                } else {
                    SearchResultsList(
                        state = state,
                        listState = listState,
                        downloadStates = downloadStates,
                        contentPadding = contentPadding,
                        scrollBehavior = scrollBehavior,
                        viewModel = viewModel,
                        onSearchHistory = { item ->
                            viewModel.searchWith(item)
                            dismissSearchInput()
                        },
                        onOpenDetail = onOpenDetail,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    state: SearchUiState,
    listState: LazyListState,
    downloadStates: State<Map<String, DownloadState>>,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    viewModel: SearchViewModel,
    onSearchHistory: (String) -> Unit,
    onOpenDetail: (MarketAppInfo) -> Unit,
) {
    val installText = installActionText()
    val updateText = stringResource(Res.string.update)
    val openText = stringResource(Res.string.open)
    val reserveText = stringResource(Res.string.reserve)
    val noResults = stringResource(Res.string.no_results)
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = contentPadding,
    ) {
        // Compact spinner only for a re-search (results present) — a fresh search uses the centered
        // full-screen one; the gate also prevents a stray spinner mid-Crossfade (loading true, no results).
        if (state.loading && state.results.isNotEmpty()) item(key = "loading") { LoadingBox() }
        if (state.errorMessage.isNotEmpty()) {
            item(key = "error") {
                Text(
                    state.errorMessage,
                    color = MiuixTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        } else if (state.showNoResults) {
            item(key = "empty") {
                Text(
                    noResults,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        if (state.results.isEmpty() && state.keyword.isBlank() && state.history.isNotEmpty()) {
            item(key = "history") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(Res.string.search_history),
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(Res.string.clear_history),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.clickable(
                                interactionSource = null,
                                indication = null,
                                onClick = viewModel::clearHistory,
                            ),
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.history.forEach { item ->
                            val selected = state.selectedHistory == item
                            Box(
                                modifier = Modifier
                                    .squircleSurface(
                                        color = MiuixTheme.colorScheme.surfaceContainer,
                                        cornerRadius = 14.dp,
                                    )
                                    .combinedClickable(
                                        onClick = { onSearchHistory(item) },
                                        onLongClick = { viewModel.selectHistory(item) },
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(item, style = MiuixTheme.textStyles.body1)
                                    AnimatedVisibility(
                                        visible = selected,
                                        enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                                        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Close,
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            modifier = Modifier
                                                .clickable(
                                                    interactionSource = null,
                                                    indication = null,
                                                    onClick = { viewModel.removeHistory(item) },
                                                )
                                                .padding(start = 8.dp)
                                                .size(13.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        items(state.results, key = { it.app.packageName }) { item ->
            val app = item.app
            val packageName = app.packageName
            val downloadState by remember(packageName) {
                derivedStateOf { downloadStates.value[packageName] }
            }
            AppRow(
                app = app,
                modifier = Modifier.animateItem(placementSpec = null),
                actionText = when (item.actionKind) {
                    AppActionKind.INSTALL -> installText
                    AppActionKind.UPDATE -> updateText
                    AppActionKind.OPEN -> openText
                    AppActionKind.RESERVE -> reserveText
                },
                actionKind = item.actionKind,
                downloadState = downloadState,
                onOpenDetail = { onOpenDetail(app) },
                onAction = { viewModel.onAction(item) },
                onResumeDownload = { viewModel.download(app, item.actionKind == AppActionKind.UPDATE) },
                onInstallDownloaded = viewModel::installDownloaded,
                onCancel = viewModel::cancelDownload,
                showSearchTags = true,
                showSourceTag = state.sources.size > 1,
            )
        }

        if (state.loadingMore) {
            item(key = "loadmore") { LoadingBox() }
        }
    }
}
