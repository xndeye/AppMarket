package com.app.market.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.market.domain.model.installer.InstallerMode
import com.app.market.resources.Res
import com.app.market.resources.cancel
import com.app.market.resources.installer_delete_after_install
import com.app.market.resources.installer_delete_after_install_summary
import com.app.market.resources.installer_mode_default
import com.app.market.resources.installer_mode_default_summary
import com.app.market.resources.installer_mode_root
import com.app.market.resources.installer_mode_root_summary
import com.app.market.resources.installer_mode_shizuku
import com.app.market.resources.installer_mode_shizuku_summary
import com.app.market.resources.installer_mode_third_party
import com.app.market.resources.installer_mode_third_party_summary
import com.app.market.resources.installer_no_user_action
import com.app.market.resources.installer_no_user_action_summary
import com.app.market.resources.installer_none
import com.app.market.resources.installer_pick
import com.app.market.resources.installer_save_to_downloads
import com.app.market.resources.installer_save_to_downloads_summary
import com.app.market.resources.installer_section
import com.app.market.ui.component.AppTextButton
import com.app.market.ui.component.CardSegmentContainer
import com.app.market.ui.component.MarketScaffold
import com.app.market.ui.component.PageVerticalPadding
import com.app.market.viewmodel.InstallerSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun InstallerSettingsScreen(
    viewModel: InstallerSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedInstaller = state.installerCandidates.firstOrNull {
        it.packageName == state.thirdPartyInstallerPackage
    }
    val deleteAfterInstall = state.mode == InstallerMode.THIRD_PARTY
    val showNoUserAction = state.mode == InstallerMode.STANDARD && state.userActionNotRequiredConfigurable

    MarketScaffold(
        title = stringResource(Res.string.installer_section),
        onBack = onBack
    ) { innerPadding, backdropModifier, scrollBehavior ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(backdropModifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
                top = innerPadding.calculateTopPadding() + PageVerticalPadding,
                bottom = innerPadding.calculateBottomPadding() + PageVerticalPadding,
            ),
        ) {
            item(key = "mode_standard") {
                CardSegmentContainer(isFirst = true, isLast = false, horizontalPadding = 0.dp) {
                    InstallerModeRow(
                        label = stringResource(Res.string.installer_mode_default),
                        summary = stringResource(Res.string.installer_mode_default_summary),
                        selected = state.mode == InstallerMode.STANDARD,
                        onClick = { viewModel.setMode(InstallerMode.STANDARD) },
                    )
                }
            }
            item(key = "mode_root") {
                CardSegmentContainer(isFirst = false, isLast = false, horizontalPadding = 0.dp) {
                    InstallerModeRow(
                        label = stringResource(Res.string.installer_mode_root),
                        summary = stringResource(Res.string.installer_mode_root_summary),
                        selected = state.mode == InstallerMode.ROOT,
                        onClick = { viewModel.setMode(InstallerMode.ROOT) },
                    )
                }
            }
            item(key = "mode_shizuku") {
                CardSegmentContainer(isFirst = false, isLast = false, horizontalPadding = 0.dp) {
                    InstallerModeRow(
                        label = stringResource(Res.string.installer_mode_shizuku),
                        summary = stringResource(Res.string.installer_mode_shizuku_summary),
                        selected = state.mode == InstallerMode.SHIZUKU,
                        onClick = { viewModel.setMode(InstallerMode.SHIZUKU) },
                    )
                }
            }
            item(key = "mode_third_party") {
                CardSegmentContainer(isFirst = false, isLast = true, horizontalPadding = 0.dp) {
                    InstallerModeRow(
                        label = stringResource(Res.string.installer_mode_third_party),
                        summary = selectedInstaller?.label
                            ?: stringResource(Res.string.installer_mode_third_party_summary),
                        selected = state.mode == InstallerMode.THIRD_PARTY,
                        onClick = viewModel::showThirdPartyInstallerPicker,
                    )
                }
            }
            item(key = "options_spacing") { Spacer(Modifier.height(20.dp)) }
            item(key = "save") {
                CardSegmentContainer(isFirst = true, isLast = !showNoUserAction, horizontalPadding = 0.dp) {
                    SwitchPreference(
                        title = stringResource(
                            if (deleteAfterInstall) {
                                Res.string.installer_delete_after_install
                            } else {
                                Res.string.installer_save_to_downloads
                            }
                        ),
                        summary = stringResource(
                            if (deleteAfterInstall) {
                                Res.string.installer_delete_after_install_summary
                            } else {
                                Res.string.installer_save_to_downloads_summary
                            }
                        ),
                        checked = if (deleteAfterInstall) !state.saveToDownloads else state.saveToDownloads,
                        onCheckedChange = { checked ->
                            viewModel.setSaveToDownloads(if (deleteAfterInstall) !checked else checked)
                        },
                    )
                }
            }
            if (showNoUserAction) {
                item(key = "no_user_action") {
                    CardSegmentContainer(isFirst = false, isLast = true, horizontalPadding = 0.dp) {
                        SwitchPreference(
                            title = stringResource(Res.string.installer_no_user_action),
                            summary = stringResource(Res.string.installer_no_user_action_summary),
                            checked = state.userActionNotRequiredEnabled,
                            onCheckedChange = viewModel::setUserActionNotRequiredEnabled,
                        )
                    }
                }
            }
        }
    }

    WindowDialog(
        show = state.showInstallerPicker,
        title = stringResource(Res.string.installer_pick),
        onDismissRequest = viewModel::dismissThirdPartyInstallerPicker,
        insideMargin = DpSize(0.dp, 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.installerCandidates.isEmpty()) {
                Text(
                    text = stringResource(Res.string.installer_none),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                ) {
                    items(
                        items = state.installerCandidates,
                        key = { it.packageName },
                    ) { candidate ->
                        RadioButtonPreference(
                            title = candidate.label,
                            summary = candidate.packageName,
                            selected = candidate.packageName == state.thirdPartyInstallerPackage,
                            radioButtonLocation = RadioButtonLocation.End,
                            insideMargin = PaddingValues(24.dp, 16.dp),
                            onClick = { viewModel.selectThirdPartyInstaller(candidate) },
                        )
                    }
                }
            }
            AppTextButton(
                text = stringResource(Res.string.cancel),
                onClick = viewModel::dismissThirdPartyInstallerPicker,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun InstallerModeRow(
    label: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    RadioButtonPreference(
        title = label,
        summary = summary,
        selected = selected,
        onClick = onClick,
        radioButtonLocation = RadioButtonLocation.End,
    )
}
