@file:Suppress(
    "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE",
    "ASSIGNED_VALUE_IS_NEVER_READ"
)
package ru.example.lab08

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

// Единственный экземпляр DataStore на уровне файла (не создавать при каждой рекомпозиции!)
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

// Ключи DataStore
private val NAME_KEY = stringPreferencesKey("name")
private val LARGE_TEXT_KEY = booleanPreferencesKey("large_text_enabled")
private val FONT_SIZE_KEY = floatPreferencesKey("font_size")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LabScreen()
                }
            }
        }
    }
}

@Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@Composable
fun LabScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    // --- Состояния, сохраняемые при повороте экрана ---
    var name by rememberSaveable { mutableStateOf("") }
    var noteText by rememberSaveable { mutableStateOf("") }
    var largeTextEnabled by rememberSaveable { mutableStateOf(false) }
    var fontSize by rememberSaveable { mutableFloatStateOf(16f) }

    // --- Служебные состояния ---
    var isError by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    // Флаг «загрузка уже была» — rememberSaveable, чтобы пережить поворот
    var hasLoadedOnce by rememberSaveable { mutableStateOf(false) }

    // --- Однократная загрузка сохранённых данных (DataStore + файл) ---
    LaunchedEffect(Unit) {
        if (hasLoadedOnce) return@LaunchedEffect
        try {
            val prefs = context.settingsDataStore.data.first()
            name = prefs[NAME_KEY] ?: ""
            largeTextEnabled = prefs[LARGE_TEXT_KEY] ?: false
            fontSize = prefs[FONT_SIZE_KEY] ?: 16f

            noteText = withContext(Dispatchers.IO) {
                val noteFile = File(context.filesDir, "notes.txt")
                if (noteFile.exists()) {
                    noteFile.inputStream()
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }
                } else ""
            }

            statusMessage = context.getString(R.string.status_ready)
            isError = false
        } catch (_: IOException) {
            statusMessage = context.getString(R.string.status_settings_error)
            isError = true
        }
        hasLoadedOnce = true
    }

    // --- Диалог подтверждения удаления заметки ---
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_confirmation_title)) },
            text = { Text(stringResource(R.string.delete_confirmation_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val file = File(context.filesDir, "notes.txt")
                                    if (file.exists()) file.delete()
                                }
                                noteText = ""
                                isError = false
                                statusMessage = context.getString(R.string.status_note_deleted)
                            } catch (_: IOException) {
                                isError = true
                                statusMessage = context.getString(R.string.status_note_error)
                            }
                        }
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // --- Основной интерфейс ---
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Поле имени
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            enabled = hasLoadedOnce,
            label = { Text(stringResource(R.string.name_label)) },
            modifier = Modifier.fillMaxWidth()
        )

        // 2. Кнопка сохранения настроек (имя + флаг + размер шрифта)
        Button(
            enabled = hasLoadedOnce,
            onClick = {
                scope.launch {
                    try {
                        context.settingsDataStore.edit { prefs ->
                            prefs[NAME_KEY] = name.trim()
                            prefs[LARGE_TEXT_KEY] = largeTextEnabled
                            prefs[FONT_SIZE_KEY] = fontSize
                        }
                        isError = false
                        statusMessage = context.getString(R.string.status_saved_settings)
                    } catch (_: IOException) {
                        isError = true
                        statusMessage = context.getString(R.string.status_save_error)
                    }
                }
            }
        ) {
            Text(stringResource(R.string.save_button))
        }

        HorizontalDivider()

        // 3. Переключатель «Крупный текст»
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.large_text_switch))
            Switch(
                checked = largeTextEnabled,
                onCheckedChange = { largeTextEnabled = it },
                enabled = hasLoadedOnce
            )
        }

        // 4. Slider, зависимый от Switch
        Text(
            text = stringResource(R.string.font_size_label),
            fontSize = if (largeTextEnabled) (fontSize * 1.5f).sp else fontSize.sp
        )
        Slider(
            value = fontSize,
            onValueChange = { fontSize = it },
            valueRange = 12f..32f,
            enabled = largeTextEnabled && hasLoadedOnce, // недоступен, если Switch выключен
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()

        // 5. Многострочная заметка
        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            enabled = hasLoadedOnce,
            label = { Text(stringResource(R.string.note_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            maxLines = 6
        )

        // 6. Кнопка сохранения заметки в файл (через use)
        Button(
            enabled = hasLoadedOnce,
            onClick = {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            File(context.filesDir, "notes.txt")
                                .outputStream()
                                .bufferedWriter(Charsets.UTF_8)
                                .use { it.write(noteText) }
                        }
                        isError = false
                        statusMessage = context.getString(R.string.status_note_saved)
                    } catch (_: IOException) {
                        isError = true
                        statusMessage = context.getString(R.string.status_note_error)
                    }
                }
            }
        ) {
            Text(stringResource(R.string.save_note_button))
        }

        // 7. Кнопка удаления заметки с подтверждением
        Button(
            enabled = hasLoadedOnce && noteText.isNotEmpty(),
            onClick = { showDeleteDialog = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(stringResource(R.string.delete_note_button))
        }

        // 8. Строка состояния
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}