package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SharedList
import com.example.data.model.SharedListItem
import com.example.data.model.SharedNote
import com.example.ui.viewmodel.BabyCareViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListsScreen(
    viewModel: BabyCareViewModel,
    onNavigateBack: () -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val selectedListId by viewModel.selectedListSyncId.collectAsStateWithLifecycle()
    val listItems by viewModel.selectedListItems.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showNoteEditor by remember { mutableStateOf<SharedNote?>(null) }
    var createNewNote by remember { mutableStateOf(false) }
    var showNewListDialog by remember { mutableStateOf(false) }
    var newListTitle by remember { mutableStateOf("") }
    var newItemText by remember { mutableStateOf("") }

    val selectedList = lists.find { it.syncId == selectedListId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notes & Lists") },
                navigationIcon = {
                    if (selectedListId != null && tabIndex == 1) {
                        IconButton(onClick = { viewModel.selectList(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (selectedList != null && tabIndex == 1) {
                        IconButton(onClick = {
                            val body = buildString {
                                appendLine(selectedList.title)
                                listItems.forEach {
                                    appendLine("${if (it.isChecked) "☑" else "☐"} ${it.text}")
                                }
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, body)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share list"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share list")
                        }
                    }
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("notes_lists_dismiss")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (tabIndex == 0) {
                        createNewNote = true
                        showNoteEditor = SharedNote()
                    } else if (selectedListId == null) {
                        showNewListDialog = true
                    } else {
                        // add item via field below
                    }
                },
                modifier = Modifier.testTag("notes_add_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("notes_lists_screen")
        ) {
            SecondaryTabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0; viewModel.selectList(null) },
                    text = { Text("Notes") }
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("Lists") }
                )
            }

            when (tabIndex) {
                0 -> NotesTab(
                    notes = notes,
                    onOpen = { showNoteEditor = it; createNewNote = false },
                    onShare = { note ->
                        val text = "${note.title}\n\n${note.body}"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            putExtra(Intent.EXTRA_SUBJECT, note.title)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share note"))
                    },
                    onDelete = { viewModel.deleteNote(it) }
                )
                else -> {
                    if (selectedList == null) {
                        ListsTab(
                            lists = lists,
                            onOpen = { viewModel.selectList(it.syncId) },
                            onDelete = { viewModel.deleteList(it) }
                        )
                    } else {
                        ListDetailTab(
                            list = selectedList,
                            items = listItems,
                            newItemText = newItemText,
                            onNewItemTextChange = { newItemText = it },
                            onAddItem = {
                                viewModel.addListItem(selectedList.syncId, newItemText)
                                newItemText = ""
                            },
                            onToggle = { viewModel.toggleListItem(it) },
                            onDeleteItem = { viewModel.deleteListItem(it) }
                        )
                    }
                }
            }
        }
    }

    showNoteEditor?.let { note ->
        NoteEditorDialog(
            note = note,
            isNew = createNewNote,
            onDismiss = { showNoteEditor = null },
            onSave = { title, body ->
                viewModel.saveNote(
                    existing = if (createNewNote) null else note,
                    title = title,
                    body = body,
                    pinnedDateMillis = note.pinnedDateMillis
                )
                showNoteEditor = null
            }
        )
    }

    if (showNewListDialog) {
        AlertDialog(
            onDismissRequest = { showNewListDialog = false },
            title = { Text("New list") },
            text = {
                OutlinedTextField(
                    value = newListTitle,
                    onValueChange = { newListTitle = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newListTitle.isNotBlank()) {
                        viewModel.createList(newListTitle.trim())
                        newListTitle = ""
                        showNewListDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewListDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NotesTab(
    notes: List<SharedNote>,
    onOpen: (SharedNote) -> Unit,
    onShare: (SharedNote) -> Unit,
    onDelete: (SharedNote) -> Unit
) {
    if (notes.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No notes yet", fontWeight = FontWeight.SemiBold)
            Text(
                "Capture shopping ideas, doctor questions, and more — synced nearby.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        items(notes, key = { it.syncId }) { note ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(note) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        note.title.ifBlank { "Untitled" },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        note.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onShare(note) }) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
                IconButton(onClick = { onDelete(note) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
private fun ListsTab(
    lists: List<SharedList>,
    onOpen: (SharedList) -> Unit,
    onDelete: (SharedList) -> Unit
) {
    if (lists.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
            Spacer(modifier = Modifier.height(8.dp))
            Text("No shopping lists yet", fontWeight = FontWeight.SemiBold)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        items(lists, key = { it.syncId }) { list ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(list) }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(list.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                IconButton(onClick = { onDelete(list) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
private fun ListDetailTab(
    list: SharedList,
    items: List<SharedListItem>,
    newItemText: String,
    onNewItemTextChange: (String) -> Unit,
    onAddItem: () -> Unit,
    onToggle: (SharedListItem) -> Unit,
    onDeleteItem: (SharedListItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(list.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newItemText,
                onValueChange = onNewItemTextChange,
                label = { Text("Add item") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            TextButton(onClick = onAddItem, enabled = newItemText.isNotBlank()) {
                Text("Add")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items, key = { it.syncId }) { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = item.isChecked, onCheckedChange = { onToggle(item) })
                    Text(
                        item.text,
                        modifier = Modifier.weight(1f),
                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                        color = if (item.isChecked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    IconButton(onClick = { onDeleteItem(item) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete item")
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteEditorDialog(
    note: SharedNote,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(note.title) }
    var body by remember { mutableStateOf(note.body) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New note" else "Edit note") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Body") },
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title.trim(), body.trim()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
