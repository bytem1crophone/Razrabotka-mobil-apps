package ru.example.lab07

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Проверяет реальные потоки ContentResolver; выбор через SAF проверяется отдельно. */
@RunWith(AndroidJUnit4::class)
class DocumentRepositoryTest {
    private lateinit var repository: DocumentRepository
    private lateinit var directory: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repository = DocumentRepository(context)
        directory = File(context.cacheDir, "repository-test-${System.nanoTime()}")
        check(directory.mkdirs())
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun emptyFileReturnsEmptyPreview() = runBlocking {
        val file = createDocument("")

        assertEquals("", repository.readPreview(Uri.fromFile(file)))
    }

    @Test
    fun readsCyrillicAsUtf8() = runBlocking {
        val text = "Лабораторная работа № 7\nСъешь ещё этих мягких французских булок."
        val file = createDocument(text)

        assertEquals(text, repository.readPreview(Uri.fromFile(file)))
    }

    @Test
    fun longFileReturnsExactlyFirst500Characters() = runBlocking {
        val text = "Кириллица и UTF-8\n".repeat(90)
        val file = createDocument(text)

        val preview = repository.readPreview(Uri.fromFile(file))

        assertEquals(500, preview.length)
        assertEquals(text.take(500), preview)
    }

    @Test
    fun exportWritesUtf8AndTruncatesPreviousContent() = runBlocking {
        val file = createDocument("Предыдущее длинное содержимое. ".repeat(100))
        val note = "Новая заметка: ёжик, чай, мир.\nВторая строка."

        repository.exportNote(Uri.fromFile(file), note)

        assertArrayEquals(note.toByteArray(Charsets.UTF_8), file.readBytes())
    }

    @Test
    fun exportingEmptyNoteClearsTheFile() = runBlocking {
        val file = createDocument("Старый текст")

        repository.exportNote(Uri.fromFile(file), "")

        assertEquals(0L, file.length())
    }

    @Test
    fun missingDocumentRaisesIOException() = runBlocking {
        val missingUri = Uri.fromFile(File(directory, "missing.txt"))

        try {
            repository.readPreview(missingUri)
            fail("Чтение отсутствующего документа должно завершаться IOException")
        } catch (_: IOException) {
            // Ожидаемая ошибка, которую интерфейс должен показать пользователю.
        }
    }

    @Test
    fun verifyReadableAcceptsExistingEmptyDocument() = runBlocking {
        repository.verifyReadable(Uri.fromFile(createDocument("")))
    }

    @Test
    fun verifyReadableRejectsMissingDocument() = runBlocking {
        val missingUri = Uri.fromFile(File(directory, "missing.txt"))

        try {
            repository.verifyReadable(missingUri)
            fail("Проверка отсутствующего документа должна завершаться IOException")
        } catch (_: IOException) {
            // Неуспешное восстановление URI не должно считаться успехом.
        }
    }

    private fun createDocument(text: String): File =
        File(directory, "document.txt").apply { writeText(text, Charsets.UTF_8) }
}
