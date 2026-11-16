package ru.example.lab11

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.example.lab11.ui.theme.Lab11Theme

// Модель данных
data class Item(val id: Int, val title: String, val status: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Lab11Theme {
                LabScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScreen(modifier: Modifier = Modifier) {
    // Исходные данные (добавил статусы для групп)
    var rows by remember {
        mutableStateOf(
            List(30) {
                val status = if (it % 2 == 0) "В работе" else "Выполнено"
                Item(it, "Задание ${it + 1}", status)
            }
        )
    }

    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Int>()) }

    // Состояния для меню и сортировки
    var expanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Все") }
    var isSortedByName by remember { mutableStateOf(false) }

    // Состояние для диалога подтверждения удаления
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 1. Фильтрация по поиску и категории
    var visible = rows.filter {
        it.title.contains(query, ignoreCase = true) &&
                (selectedCategory == "Все" || it.status == selectedCategory)
    }

    // 2. Сортировка по названию
    if (isSortedByName) {
        visible = visible.sortedBy { it.title }
    }

    Column(modifier.fillMaxSize().padding(16.dp)) {

        // Заголовок и счетчик
        Text(
            text = "Каталог заданий",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Найдено: ${visible.size} из ${rows.size}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Панель управления (Меню и Сортировка)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Выпадающее меню категорий
            Box {
                Button(onClick = { expanded = true }) {
                    Text("Категория: $selectedCategory")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(text = { Text("Все") }, onClick = { selectedCategory = "Все"; expanded = false })
                    DropdownMenuItem(text = { Text("В работе") }, onClick = { selectedCategory = "В работе"; expanded = false })
                    DropdownMenuItem(text = { Text("Выполнено") }, onClick = { selectedCategory = "Выполнено"; expanded = false })
                }
            }

            // Кнопка сортировки
            Button(onClick = { isSortedByName = !isSortedByName }) {
                Text(if (isSortedByName) "А-Я" else "Без сортировки")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Поиск
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Поиск по названию") },
            modifier = Modifier.fillMaxWidth()
        )

        // Кнопка удаления (вызывает диалог)
        Button(
            enabled = selected.isNotEmpty(),
            onClick = { showDeleteDialog = true },
            modifier = Modifier.padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Удалить выбранные (${selected.size})")
        }

        if (visible.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ничего не найдено", style = MaterialTheme.typography.bodyLarge)
            }
        }

        // Сетка с карточками
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(visible, key = { it.id }) { item ->
                Card(
                    modifier = Modifier
                        .padding(4.dp)
                        .fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.id in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + item.id else selected - item.id
                            }
                        )
                        Column {
                            Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = item.status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    // Диалог подтверждения удаления (все скобки на месте)
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Подтверждение") },
            text = { Text("Вы уверены, что хотите удалить ${selected.size} элемент(ов)?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        rows = rows.filterNot { it.id in selected }
                        selected = emptySet()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}