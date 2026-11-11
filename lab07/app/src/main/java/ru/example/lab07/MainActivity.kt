package ru.example.lab07

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.example.lab07.ui.theme.Lab07Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Lab07Theme {
                LabScreen()
            }
        }
    }
}

@Composable
fun LabScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val repository = remember { DocumentRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // В Bundle попадают только небольшая заметка, адрес и идентификаторы.
    var note by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf(HttpsAddress.DEFAULT_ADDRESS) }
    var selectedUri by rememberSaveable {
        mutableStateOf(repository.lastOpenedUri?.toString())
    }
    var exportedUri by rememberSaveable {
        mutableStateOf(repository.lastExportedUri?.toString())
    }
    var pendingNote by rememberSaveable { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    var readFailed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var addressError by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var readRequest by remember { mutableStateOf(0) }

    DisposableEffect(repository) {
        val unsubscribe = repository.observeExportedUri { exportedUri = it?.toString() }
        onDispose { unsubscribe() }
    }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it) }
    }

    // После пересоздания экрана содержимое перечитывается по URI, а не из Bundle.
    LaunchedEffect(selectedUri, readRequest) {
        val uri = selectedUri?.let(Uri::parse) ?: return@LaunchedEffect
        busy = true
        readFailed = false
        try {
            preview = repository.readPreview(uri)
        } catch (_: SecurityException) {
            preview = null
            readFailed = true
            repository.forgetOpenedUri()
            message = resources.getString(R.string.access_revoked)
        } catch (_: IOException) {
            preview = null
            readFailed = true
            message = resources.getString(R.string.read_error)
        } catch (_: IllegalArgumentException) {
            preview = null
            readFailed = true
            message = resources.getString(R.string.document_unavailable)
        } finally {
            busy = false
        }
    }

    // Оба launcher регистрируются безусловно и всегда в одинаковом порядке.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            message = resources.getString(R.string.selection_cancelled)
        } else {
            if (repository.persistReadPermission(uri)) {
                repository.rememberOpenedUri(uri)
                message = resources.getString(R.string.file_selected)
            } else {
                repository.forgetOpenedUri()
                message = resources.getString(R.string.temporary_access)
            }
            selectedUri = uri.toString()
            readRequest++
        }
    }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val snapshot = pendingNote
        pendingNote = null
        if (uri == null) {
            message = resources.getString(R.string.export_cancelled)
        } else if (snapshot != null) {
            scope.launch {
                busy = true
                try {
                    // Начатую запись закрываем и сохраняем URI даже при повороте экрана.
                    val persistent = withContext(Dispatchers.IO + NonCancellable) {
                        repository.exportNote(uri, snapshot)
                        repository.persistReadPermission(uri).also { saved ->
                            if (saved) repository.rememberExportedUri(uri)
                            else repository.forgetExportedUri()
                        }
                    }
                    exportedUri = uri.toString()
                    message = resources.getString(
                        if (persistent) R.string.export_success else R.string.export_temporary
                    )
                } catch (_: SecurityException) {
                    message = resources.getString(R.string.export_access_denied)
                } catch (_: IOException) {
                    message = resources.getString(R.string.export_error)
                } catch (_: IllegalArgumentException) {
                    message = resources.getString(R.string.document_unavailable)
                } finally {
                    busy = false
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.screen_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.screen_description), style = MaterialTheme.typography.bodyMedium)
                message?.let {
                    Text(
                        it,
                        modifier = Modifier.testTag("statusMessage").semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LabSection(stringResource(R.string.file_section)) {
                Button(
                    onClick = {
                        try {
                            picker.launch(arrayOf("text/plain"))
                        } catch (_: ActivityNotFoundException) {
                            message = resources.getString(R.string.picker_unavailable)
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.testTag("pickFileButton")
                ) { Text(stringResource(R.string.choose_file)) }
                Text(stringResource(R.string.preview_hint), style = MaterialTheme.typography.bodySmall)
                SelectionContainer {
                    Text(
                        text = when {
                            readFailed -> stringResource(R.string.preview_unavailable)
                            preview == "" -> stringResource(R.string.empty_file)
                            preview != null -> preview.orEmpty()
                            selectedUri != null -> stringResource(R.string.reading_file)
                            else -> stringResource(R.string.no_file)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("filePreview")
                    )
                }
            }

            LabSection(stringResource(R.string.note_section)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(NOTE_LIMIT) },
                    label = { Text(stringResource(R.string.note_label)) },
                    supportingText = { Text(stringResource(R.string.note_counter, note.length, NOTE_LIMIT)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("noteInput")
                )
                Text(stringResource(R.string.note_hint), style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        pendingNote = note
                        try {
                            exporter.launch("note.txt")
                        } catch (_: ActivityNotFoundException) {
                            pendingNote = null
                            message = resources.getString(R.string.picker_unavailable)
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.testTag("exportNoteButton")
                ) { Text(stringResource(R.string.export_note)) }
                OutlinedButton(
                    onClick = {
                        val uri = exportedUri?.let(Uri::parse) ?: return@OutlinedButton
                        scope.launch {
                            busy = true
                            try {
                                repository.verifyReadable(uri)
                                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "text/plain")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                })
                            } catch (_: ActivityNotFoundException) {
                                message = resources.getString(R.string.editor_unavailable)
                            } catch (_: SecurityException) {
                                repository.forgetExportedUri()
                                exportedUri = null
                                message = resources.getString(R.string.access_revoked)
                            } catch (_: IOException) {
                                message = resources.getString(R.string.saved_file_missing)
                            } catch (_: IllegalArgumentException) {
                                message = resources.getString(R.string.document_unavailable)
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = exportedUri != null && !busy,
                    modifier = Modifier.testTag("openExportButton")
                ) { Text(stringResource(R.string.open_export)) }
            }

            LabSection(stringResource(R.string.website_section)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it.take(2048)
                        addressError = false
                    },
                    label = { Text(stringResource(R.string.address_label)) },
                    isError = addressError,
                    supportingText = {
                        Text(stringResource(if (addressError) R.string.invalid_https else R.string.https_hint))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("addressInput")
                )
                Button(
                    onClick = {
                        val validAddress = HttpsAddress.normalize(address)
                        addressError = validAddress == null
                        if (validAddress == null) {
                            message = resources.getString(R.string.invalid_https)
                        } else {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(validAddress)).apply {
                                    addCategory(Intent.CATEGORY_BROWSABLE)
                                })
                            } catch (_: ActivityNotFoundException) {
                                message = resources.getString(R.string.browser_unavailable)
                            } catch (_: SecurityException) {
                                message = resources.getString(R.string.browser_denied)
                            }
                        }
                    },
                    modifier = Modifier.testTag("openWebsiteButton")
                ) { Text(stringResource(R.string.open_website)) }
            }
        }
    }
}

@Composable
private fun LabSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

private const val NOTE_LIMIT = 4000

