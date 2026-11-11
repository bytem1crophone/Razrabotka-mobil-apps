package ru.example.lab10

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

    // Состояния для полей ввода
    var groupName by remember { mutableStateOf("ИС-21") }
    var student1Name by remember { mutableStateOf("Иван") }
    var student2Name by remember { mutableStateOf("Анна") }

    var text by remember {
        mutableStateOf("Нажмите кнопку. База: school.db, версия 3.")
    }

    fun runDb(block: (SchoolDb) -> String) {
        scope.launch {
            text = withContext(Dispatchers.IO) {
                val helper = SchoolDb(context)
                try {
                    block(helper)
                } catch (e: Exception) {
                    "Ошибка: ${e.javaClass.simpleName}: ${e.message}"
                } finally {
                    helper.close()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "ЛР10: SQLite, JOIN, транзакции, миграции",
            style = MaterialTheme.typography.titleMedium
        )

        // Поля ввода для пользовательских данных
        OutlinedTextField(
            value = groupName,
            onValueChange = { groupName = it },
            label = { Text("Название группы") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = student1Name,
            onValueChange = { student1Name = it },
            label = { Text("Имя студента 1") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = student2Name,
            onValueChange = { student2Name = it },
            label = { Text("Имя студента 2") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = {
            runDb {
                // Передаем в базу то, что ввел пользователь
                it.seed(groupName, student1Name, student2Name)
                "Данные сохранены. Всего студентов в базе: ${it.studentCount()}"
            }
        }) {
            Text("1. Сохранить группу и двух студентов")
        }

        Button(onClick = {
            runDb { it.joinText() }
        }) {
            Text("2. Показать INNER JOIN")
        }

        Button(onClick = {
            runDb { it.transactionSuccess() }
        }) {
            Text("3. Транзакция: успешная")
        }

        Button(onClick = {
            runDb { it.transactionRollback() }
        }) {
            Text("4. Транзакция: откат")
        }

        Button(onClick = {
            runDb { it.invalidForeignKey() }
        }) {
            Text("5. Проверить внешний ключ")
        }

        Button(onClick = {
            runDb {
                "Студентов: ${it.studentCount()}\n" +
                        "Групп: ${it.groupCount()}\n" +
                        "Схема student:\n${it.schemaText()}"
            }
        }) {
            Text("6. Показать количество и схему")
        }

        Button(onClick = {
            runDb { it.deleteGroupRestrictDemo() }
        }) {
            Text("7. Проверить ON DELETE RESTRICT")
        }

        Text(
            text = text,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}