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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scripturebookmarks.bible.BibleBooks
import com.scripturebookmarks.bible.LinkBuilder
import com.scripturebookmarks.bible.Reference
import com.scripturebookmarks.bible.ScriptureParser
import com.scripturebookmarks.data.AppDatabase
import com.scripturebookmarks.data.BookmarkEntity
import com.scripturebookmarks.data.toReference
import com.scripturebookmarks.ui.ScriptureTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookmarksViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).bookmarks()

    val bookmarks = dao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(r: Reference, note: String) = viewModelScope.launch {
        dao.insert(
            BookmarkEntity(
                book = r.book.number, chapter = r.chapter,
                verseStart = r.verseStart, verseEnd = r.verseEnd, note = note.trim(),
            )
        )
    }

    fun delete(b: BookmarkEntity) = viewModelScope.launch { dao.delete(b) }
}

class MainActivity : ComponentActivity() {
    private val vm: BookmarksViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ScriptureTheme { App(vm) } }
    }
}

// Tries the JW Library link first, then falls back to the web link.
fun openReference(context: Context, r: Reference) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LinkBuilder.appLink(r))))
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LinkBuilder.webLink(r))))
        } catch (_: ActivityNotFoundException) {
        }
    }
}

@Composable
fun App(vm: BookmarksViewModel) {
    val list by vm.bookmarks.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
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
                Text("No bookmarks yet.\nTap + to add a scripture.", textAlign = TextAlign.Center)
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
                        onOpen = { openReference(context, b.toReference()) },
                        onDelete = { vm.delete(b) },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddDialog(onDismiss = { showAdd = false }, onSave = { r, n -> vm.add(r, n); showAdd = false })
    }
}

@Composable
fun BookmarkCard(b: BookmarkEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                b.toReference().display,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (b.note.isNotBlank()) Text(b.note, Modifier.padding(top = 4.dp))
            Row { TextButton(onClick = onDelete) { Text("Delete") } }
        }
    }
}

@Composable
fun Picker(label: String, options: List<String>, selected: String, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label: $selected") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(i); open = false })
            }
        }
    }
}

@Composable
fun AddDialog(onDismiss: () -> Unit, onSave: (Reference, String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    var bookIdx by remember { mutableIntStateOf(0) }
    var chapter by remember { mutableIntStateOf(1) }
    var vStart by remember { mutableStateOf("1") }
    var vEnd by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val book = BibleBooks.all[bookIdx]

    val ref: Reference? = if (tab == 0) {
        ScriptureParser.parse(typed)
    } else {
        val s = vStart.toIntOrNull()
        val e = vEnd.toIntOrNull()
        if (s == null || s < 1 || (vEnd.isNotBlank() && (e == null || e <= s))) null
        else Reference(book, chapter, s, e)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add scripture") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }) { Text("Type", Modifier.padding(12.dp)) }
                    Tab(selected = tab == 1, onClick = { tab = 1 }) { Text("Pick", Modifier.padding(12.dp)) }
                }
                if (tab == 0) {
                    OutlinedTextField(
                        value = typed, onValueChange = { typed = it },
                        label = { Text("e.g. John 3:16") }, singleLine = true,
                    )
                } else {
                    Picker("Book", BibleBooks.all.map { it.name }, book.name) { bookIdx = it; chapter = 1 }
                    Picker("Chapter", (1..book.chapters).map { it.toString() }, chapter.toString()) { chapter = it + 1 }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = vStart, onValueChange = { vStart = it.filter(Char::isDigit) },
                            label = { Text("Verse") }, singleLine = true, modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = vEnd, onValueChange = { vEnd = it.filter(Char::isDigit) },
                            label = { Text("To (optional)") }, singleLine = true, modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (tab == 1 || typed.isNotBlank()) {
                    Text(
                        ref?.display ?: "Not a valid reference",
                        color = if (ref != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { ref?.let { onSave(it, note) } }, enabled = ref != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
