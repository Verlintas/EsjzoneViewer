package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.breakyuna.esjzone.ui.designsystem.AccountSummary
import com.breakyuna.esjzone.ui.designsystem.accountContentWidth
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.BookmarkCoverStore
import com.breakyuna.esjzone.database.entity.Bookmark as LocalBookmark
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.ui.component.AppNovelCover
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.ChapterStateHolder
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.product.EmptyState
import com.breakyuna.esjzone.ui.product.ErrorState
import com.breakyuna.esjzone.ui.product.LoadingSkeleton
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Device-only chapter bookmarks; opening one delegates to the reader shell. */
object BookmarksPage : AppDestination {
    private fun readResolve(): Any = BookmarksPage
    override val key: String = "BookmarksPage"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val model = rememberAppViewModel { BookmarksPageModel() }
        val state by model.state.collectAsState()
        LaunchedEffect(Unit) { model.load() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.bookmarks), style = AppTypography.titleLarge) },
                    navigationIcon = { BackIconButton { navigator?.pop() } }
                )
            }
        ) { padding ->
            when (val current = state) {
                BookmarksPageModel.State.Loading -> LoadingSkeleton(Modifier.fillMaxWidth().padding(padding), stringResource(R.string.bookmarks))
                is BookmarksPageModel.State.Error -> ErrorState(
                    title = stringResource(R.string.load_client_error),
                    message = stringResource(R.string.history_local_load_failed),
                    onRetry = model::retry,
                    modifier = Modifier.fillMaxSize().accountContentWidth().padding(padding)
                )
                is BookmarksPageModel.State.Result -> if (current.bookmarks.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.bookmarks_empty),
                        message = stringResource(R.string.bookmarks_description),
                        modifier = Modifier.fillMaxSize().accountContentWidth().padding(padding)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().accountContentWidth().padding(padding),
                        contentPadding = PaddingValues(AppSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                    ) {
                        item(key = "bookmark-header") {
                            AccountSummary(
                                Icons.Filled.Bookmark,
                                stringResource(R.string.bookmarks),
                                stringResource(R.string.bookmarks_description)
                            )
                        }
                        items(
                            current.bookmarks,
                            key = { "bookmark:${it.chapterUrl}" },
                            contentType = { "bookmark" }
                        ) { bookmark ->
                            BookmarkCard(
                                bookmark = bookmark,
                                coverUrl = model.coverUrlFor(bookmark),
                                onOpen = {
                                    val chapter = Chapter(bookmark.chapterName, bookmark.chapterUrl, false)
                                    val cleanId = BookmarkCoverStore.cleanNovelId(bookmark.novelId, bookmark.chapterUrl)
                                    navigator?.pushIfNotCurrent(
                                        ChapterPage(
                                            novelId = cleanId,
                                            chapter = chapter,
                                            history = ChapterStateHolder(chapter),
                                            novelName = bookmark.novelName,
                                            novelUrl = "/detail/$cleanId.html",
                                            novelCoverUrl = model.coverUrlFor(bookmark)
                                        )
                                    )
                                },
                                onDelete = { model.delete(bookmark) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(
    bookmark: LocalBookmark,
    coverUrl: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.standard)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onOpen)
            .semantics { role = Role.Button }
            .padding(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        AppNovelCover(
            coverUrl = coverUrl,
            title = bookmark.novelName.ifBlank { bookmark.novelId },
            modifier = Modifier.size(width = 56.dp, height = 76.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(bookmark.novelName.ifBlank { bookmark.novelId }, style = AppTypography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(bookmark.chapterName, style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.bookmark_remove))
        }
    }
}

private class BookmarksPageModel : AppStateViewModel<BookmarksPageModel.State>(State.Loading) {
    sealed class State {
        data object Loading : State()
        data class Result(val bookmarks: List<LocalBookmark>) : State()
        data object Error : State()
    }

    private var loadStarted = false
    val resolvedCovers = mutableStateMapOf<String, String>()

    fun load() {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                PresentationAccess.database.bookmarkDao().observeAll().collect { bookmarks ->
                    mutableState.value = State.Result(bookmarks)
                    ensureCovers(bookmarks)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadStarted = false
                mutableState.value = State.Error
                AppLogger.e("BookmarksPageModel", "Failed to load local bookmarks", e)
            }
        }
    }

    private suspend fun ensureCovers(bookmarks: List<LocalBookmark>) {
        for (bookmark in bookmarks) {
            val cleanId = BookmarkCoverStore.cleanNovelId(bookmark.novelId, bookmark.chapterUrl)
            val uri = BookmarkCoverStore.getCoverUri(bookmark.novelId, bookmark.chapterUrl)
            if (uri != null) {
                withContext(Dispatchers.Main) {
                    resolvedCovers[cleanId] = uri
                }
            } else {
                // If the cover file wasn't created yet, copy from existing Coil cache or download store (0 network)
                val copied = BookmarkCoverStore.saveCoverFromCacheOrDownload(
                    novelId = bookmark.novelId,
                    chapterUrl = bookmark.chapterUrl
                )
                if (copied != null) {
                    withContext(Dispatchers.Main) {
                        resolvedCovers[cleanId] = copied
                    }
                }
            }
        }
    }

    fun coverUrlFor(bookmark: LocalBookmark): String {
        val cleanId = BookmarkCoverStore.cleanNovelId(bookmark.novelId, bookmark.chapterUrl)
        return resolvedCovers[cleanId] ?: BookmarkCoverStore.getCoverUri(cleanId, bookmark.chapterUrl).orEmpty()
    }

    fun retry() {
        loadStarted = false
        mutableState.value = State.Loading
        load()
    }

    fun delete(bookmark: LocalBookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                PresentationAccess.database.bookmarkDao().delete(bookmark)
                BookmarkCoverStore.cleanupIfUnused(bookmark.novelId, bookmark.chapterUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("BookmarksPageModel", "Failed to delete local bookmark", e)
            }
        }
    }
}
