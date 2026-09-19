package com.breakyuna.esjzone.ui.tab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.discovery.DiscoveryErrorState

internal fun LazyListScope.randomRecommendationsSection(
    state: HomeTabModel.RandomRecommendationsState,
    title: String,
    changeBatchLabel: String,
    collapseLabel: String,
    onActivate: () -> Unit,
    onChangeBatch: () -> Unit,
    onCollapse: () -> Unit,
    onRetry: () -> Unit,
    onNovelClick: (CoveredNovel) -> Unit
) {
    if (!state.isActivated) {
        item(key = "home-random-trigger", contentType = "home-random-trigger") {
            RandomRecommendationTriggerItem(
                isLoading = state.isLoading,
                onClick = onActivate
            )
        }
        return
    }

    if (state.items.isEmpty()) {
        if (state.isLoading) {
            item(key = "home-random-initial-loading", contentType = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.xxl),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.failure != null) {
            item(key = "home-random-initial-error", contentType = "error") {
                DiscoveryErrorState(
                    message = stringResource(failureMessage(state.failure)),
                    onRetry = onRetry
                )
            }
        }
        return
    }

    item(key = "home-random-divider", contentType = "home-section-divider") {
        HomeSectionDividerItem()
    }
    item(key = "home-random-header", contentType = "home-random-header") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onCollapse
            ) {
                Text(collapseLabel)
            }
            TextButton(
                enabled = !state.isLoading,
                onClick = onChangeBatch
            ) {
                Text(changeBatchLabel)
            }
        }
    }

    val rows = state.items.chunked(HOME_GRID_COLUMNS)
    items(
        count = rows.size,
        key = { rowIndex -> "home-random-row:${novelKey(rows[rowIndex].first())}" },
        contentType = { "home-grid-row" }
    ) { rowIndex ->
        val row = rows[rowIndex]
        NovelGridRow(
            row = row,
            showLatestTitle = false,
            onNovelClick = onNovelClick,
            modifier = Modifier
                .padding(bottom = if (rowIndex < rows.size - 1) AppSpacing.lg else AppSpacing.zero)
                .semantics { contentDescription = "Random recommendation row $rowIndex" }
        )
    }

    if (state.isLoading) {
        item(key = "home-random-loading-more", contentType = "loading") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.lg),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    } else if (state.failure != null) {
        item(key = "home-random-more-error", contentType = "error") {
            DiscoveryErrorState(
                message = stringResource(failureMessage(state.failure)),
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun RandomRecommendationTriggerItem(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = AppShapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.lg)
            .height(44.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = stringResource(R.string.home_random_pull_to_load),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
