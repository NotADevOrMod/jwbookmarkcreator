@file:OptIn(ExperimentalMaterial3Api::class)

package com.scripturebookmarks

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scripturebookmarks.bible.BibleBooks
import com.scripturebookmarks.bible.Book
import com.scripturebookmarks.bible.LinkBuilder
import com.scripturebookmarks.bible.Reference
import com.scripturebookmarks.bible.ScriptureParser
import com.scripturebookmarks.data.AppDatabase
import com.scripturebookmarks.data.ArticleEntity
import com.scripturebookmarks.data.BookmarkEntity
import com.scripturebookmarks.data.toReference
import com.scripturebookmarks.ui.ScriptureTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SharedArticle(val title: String, val url: String)

class BookmarksViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).bookmarks()

    val bookmarks = dao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val articles = dao.allArticles()
        .map { list -> list.groupBy { it.bookmarkId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun add(r: Reference, note: String, article: SharedArticle? = null) = viewModelScope.launch {
        val id = dao.insert(
            BookmarkEntity(
                book = r.book.number, chapter = r.chapter,
                verseStart = r.verseStart, verseEnd = r.verseEnd, note = note.trim(),
            )
        )
        if (article != null) dao.insertArticle(ArticleEntity(bookmarkId = id, title = article.title, url = article.url))
    }

    fun attach(bookmarkId: Long, a: SharedArticle) = viewModelScope.launch {
        dao.insertArticle(ArticleEntity(bookmarkId = bookmarkId, title = a.title, url = a.url))
    }

    fun delete(b: BookmarkEntity) = viewModelScope.launch { dao.delete(b) }
    fun removeArticle(a: ArticleEntity) = viewModelScope.launch { dao.deleteArticle(a) }
}

class MainActivity : ComponentActivity() {
    private val vm: BookmarksViewModel by viewModels()
    private var shared by mutableStateOf<SharedArticle?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { ScriptureTheme { App(vm, shared) { shared = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = Regex("""https?://\S+""").find(text)?.value ?: return
        val rest = text.replace(url, "").trim()
        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() }
            ?: rest.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            ?: url
        shared = SharedArticle(title, url)
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
    }
}

// Tries the JW Library link first, then falls back to the web link.
fun openReference(context: Context, r: Reference) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LinkBuilder.appLink(r))))
    } catch (e: ActivityNotFoundException) {
        openUrl(context, LinkBuilder.webLink(r))
    }
}

@Composable
fun App(vm: BookmarksViewModel, shared: SharedArticle?, onSharedDone: () -> Unit) {
    val list by vm.bookmarks.collectAsStateWithLifecycle()
    val articles by vm.articles.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var newForShared by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scripture Bookmarks") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Text("+", fontSize = 28.sp) }
        },
    ) { pad ->
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text(
                    "No bookmarks yet.\nTap + to add a scripture,\nor share an article to this app.",
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { b ->
                    BookmarkCard(
                        b,
                        articles[b.id].orEmpty(),
                        onOpen = { openReference(context, b.toReference()) },
                        onDelete = { vm.delete(b) },
                        onOpenArticle = { openUrl(context, it.url) },
                        onRemoveArticle = { vm.removeArticle(it) },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddScreen(onDismiss = { showAdd = false }, onSave = { r, n -> vm.add(r, n); showAdd = false })
    }

    if (shared != null) {
        if (newForShared) {
            AddScreen(
                onDismiss = { newForShared = false },
                onSave = { r, n -> vm.add(r, n, shared); newForShared = false; onSharedDone() },
            )
        } else {
            AttachDialog(
                article = shared,
                bookmarks = list,
                onPick = { vm.attach(it.id, shared); onSharedDone() },
                onNew = { newForShared = true },
                onDismiss = onSharedDone,
            )
        }
    }
}

@Composable
fun BookmarkCard(
    b: BookmarkEntity,
    articles: List<ArticleEntity>,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onOpenArticle: (ArticleEntity) -> Unit,
    onRemoveArticle: (ArticleEntity) -> Unit,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                b.toReference().display,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (b.note.isNotBlank()) Text(b.note, Modifier.padding(top = 4.dp))
            articles.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        a.title,
                        Modifier.weight(1f).clickable { onOpenArticle(a) }.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { onRemoveArticle(a) }) { Text("✕") }
                }
            }
            Row { TextButton(onClick = onDelete) { Text("Delete") } }
        }
    }
}

@Composable
fun AttachDialog(
    article: SharedArticle,
    bookmarks: List<BookmarkEntity>,
    onPick: (BookmarkEntity) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save article to…") },
        text = {
            Column {
                Text(article.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(article.url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                if (bookmarks.isEmpty()) {
                    Text("No bookmarks yet. Create one to attach this article to.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(bookmarks, key = { it.id }) { b ->
                            Text(
                                b.toReference().display,
                                Modifier.fillMaxWidth().clickable { onPick(b) }.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onNew) { Text("New bookmark") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun Tile(text: String, container: Color, content: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Box(Modifier.padding(4.dp), contentAlignment = Alignment.Center) {
            Text(text, textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2)
        }
    }
}

@Composable
fun BookGrid(onBook: (Book) -> Unit) {
    val cs = MaterialTheme.colorScheme
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Hebrew-Aramaic Scriptures", style = MaterialTheme.typography.titleSmall, color = cs.primary)
        }
        items(BibleBooks.all.filter { it.number <= 39 }) { b ->
            Tile(b.name, cs.primaryContainer, cs.onPrimaryContainer) { onBook(b) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Christian Greek Scriptures",
                style = MaterialTheme.typography.titleSmall,
                color = cs.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(BibleBooks.all.filter { it.number >= 40 }) { b ->
            Tile(b.name, cs.secondaryContainer, cs.onSecondaryContainer) { onBook(b) }
        }
    }
}

@Composable
fun ChapterGrid(count: Int, onChapter: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    LazyVerticalGrid(
        columns = GridCells.Adaptive(64.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items((1..count).toList()) { n ->
            Tile(n.toString(), cs.primaryContainer, cs.onPrimaryContainer) { onChapter(n) }
        }
    }
}

@Composable
fun TypeTab(value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = value, onValueChange = onChange,
            label = { Text("e.g. John 3:16") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Abbreviations work too: 1 Cor 13:4-7, ps 23, jude 5",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun PickTab(
    book: Book?,
    chapter: Int?,
    vStart: String,
    vEnd: String,
    onBook: (Book) -> Unit,
    onChapter: (Int) -> Unit,
    onClearBook: () -> Unit,
    onClearChapter: () -> Unit,
    onVStart: (String) -> Unit,
    onVEnd: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(onClick = onClearBook, label = { Text(book?.name ?: "Book") })
            AssistChip(
                onClick = onClearChapter,
                label = { Text(chapter?.let { "Chapter $it" } ?: "Chapter") },
                enabled = book != null,
            )
        }
        when {
            book == null -> BookGrid(onBook)
            chapter == null -> ChapterGrid(book.chapters, onChapter)
            else -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Which verse?", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vStart, onValueChange = onVStart,
                        label = { Text("Verse") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = vEnd, onValueChange = onVEnd,
                        label = { Text("To (optional)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun AddScreen(onDismiss: () -> Unit, onSave: (Reference, String) -> Unit) {
    var tab by remember { mutableIntStateOf(1) }
    var typed by remember { mutableStateOf("") }
    var book by remember { mutableStateOf<Book?>(null) }
    var chapter by remember { mutableStateOf<Int?>(null) }
    var vStart by remember { mutableStateOf("") }
    var vEnd by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val ref: Reference? = if (tab == 0) {
        ScriptureParser.parse(typed)
    } else {
        val bk = book
        val ch = chapter
        val s = vStart.toIntOrNull()
        val e = vEnd.toIntOrNull()
        if (bk == null || ch == null || s == null || s < 1 || (vEnd.isNotBlank() && (e == null || e <= s))) null
        else Reference(bk, ch, s, e)
    }

    val preview = when {
        ref != null -> ref.display
        tab == 0 && typed.isBlank() -> "Type a reference like John 3:16"
        tab == 0 -> "Not a valid reference"
        book == null -> "Choose a book"
        chapter == null -> "Choose a chapter"
        vStart.isBlank() -> "Enter a verse"
        else -> "Not a valid reference"
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                TopAppBar(
                    title = { Text("Add scripture") },
                    navigationIcon = {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }) { Text("Type", Modifier.padding(14.dp)) }
                    Tab(selected = tab == 1, onClick = { tab = 1 }) { Text("Pick", Modifier.padding(14.dp)) }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (tab == 0) {
                        TypeTab(typed) { typed = it }
                    } else {
                        PickTab(
                            book, chapter, vStart, vEnd,
                            onBook = { book = it; chapter = null },
                            onChapter = { chapter = it },
                            onClearBook = { book = null; chapter = null },
                            onClearChapter = { chapter = null },
                            onVStart = { vStart = it.filter(Char::isDigit) },
                            onVEnd = { vEnd = it.filter(Char::isDigit) },
                        )
                    }
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (ref != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { ref?.let { onSave(it, note) } },
                        enabled = ref != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save bookmark") }
                }
            }
        }
    }
}
