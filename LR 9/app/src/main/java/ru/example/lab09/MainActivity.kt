package ru.example.lab09

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LabScreen()
                }
            }
        }
    }
}

@Composable
fun LabScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf("") }
    var rows by remember { mutableStateOf(emptyList<Task>()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    fun refresh(add: Boolean = false) {
        if (busy) return
        busy = true
        val input = title.trim()

        scope.launch {
            try {
                rows = withContext(Dispatchers.IO) {
                    TasksDb(context).use { db ->
                        if (add && input.isNotEmpty()) {
                            db.add(input)
                        }
                        db.read()
                    }
                }
                if (add) title = ""
                status = "Записей: ${rows.size}"
            } catch (e: Exception) {
                status = "Ошибка базы данных: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh(add = false)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название задачи") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { refresh(add = true) },
                enabled = !busy && title.isNotBlank()
            ) {
                Text("Добавить")
            }
        }

        Text(text = status, style = MaterialTheme.typography.bodyMedium)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(rows, key = { it.id }) { task ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ID: ${task.id} | ${task.title}",
                            modifier = Modifier.weight(1f)
                        )

                        Checkbox(
                            checked = task.done,
                            onCheckedChange = { isChecked ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        TasksDb(context).use { it.updateDone(task.id, isChecked) }
                                    }
                                    refresh(add = false)
                                }
                            }
                        )

                        TextButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        TasksDb(context).use { it.delete(task.id) }
                                    }
                                    refresh(add = false)
                                }
                            }
                        ) {
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}