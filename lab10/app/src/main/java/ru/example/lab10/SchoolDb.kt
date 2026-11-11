package ru.example.lab10

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SchoolDb(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {
    companion object {
        const val DB_NAME = "school.db"
        const val DB_VERSION = 3
        const val DEFAULT_EMAIL = "student@example.ru"
        const val DEFAULT_YEAR = 2026
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `groups`(
                id INTEGER PRIMARY KEY,
                title TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS student(
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                group_id INTEGER NOT NULL,
                email TEXT NOT NULL DEFAULT '$DEFAULT_EMAIL',
                enrolled_year INTEGER NOT NULL DEFAULT $DEFAULT_YEAR,
                FOREIGN KEY(group_id) REFERENCES `groups`(id) ON DELETE RESTRICT
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_student_group_id ON student(group_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE student ADD COLUMN email TEXT NOT NULL DEFAULT '$DEFAULT_EMAIL'"
            )
        }
        if (oldVersion < 3) {
            db.execSQL(
                "ALTER TABLE student ADD COLUMN enrolled_year INTEGER NOT NULL DEFAULT $DEFAULT_YEAR"
            )
        }
    }

    fun seed(groupName: String, student1Name: String, student2Name: String) {
        val db = writableDatabase
        db.insertWithOnConflict(
            "`groups`",
            null,
            ContentValues().apply {
                put("id", 1)
                put("title", groupName)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )

        insertOrReplaceStudent(db, 1, student1Name)
        insertOrReplaceStudent(db, 2, student2Name)
    }

    fun joinText(): String {
        val sql = """
            SELECT s.name, g.title
            FROM student s
            INNER JOIN `groups` g ON s.group_id = g.id
            ORDER BY s.id
        """.trimIndent()

        return writableDatabase.rawQuery(sql, null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add("${c.getString(0)} / ${c.getString(1)}")
                }
            }.joinToString("\n").ifBlank { "Нет строк: сначала создайте данные" }
        }
    }

    fun studentCount(): Int =
        writableDatabase.rawQuery("SELECT COUNT(*) FROM student", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    fun groupCount(): Int =
        writableDatabase.rawQuery("SELECT COUNT(*) FROM `groups`", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    // @Suppress("UseKtx") скрывает совет студии про db.transaction, т.к. методичка требует beginTransaction
    @SuppressLint("Recycle")
    @Suppress("UseKtx")
    fun transactionSuccess(): String {
        val db = writableDatabase
        db.delete("student", "id IN (100, 101)", null)

        val before = studentCount()

        db.beginTransaction()
        try {
            insertStudent(db, 100, "Студент-100")
            insertStudent(db, 101, "Студент-101")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        val after = studentCount()
        return "Успешная транзакция: было $before, стало $after (+2)"
    }

    @SuppressLint("Recycle")
    @Suppress("UseKtx")
    fun transactionRollback(): String {
        val db = writableDatabase
        db.delete("student", "id = 200", null)

        val before = studentCount()

        db.beginTransaction()
        try {
            insertStudent(db, 200, "Студент-200")
            throw IllegalStateException("Искусственная ошибка после первой вставки")
        } catch (_: Exception) {
            // Исключение проглочено специально для демонстрации отката
        } finally {
            db.endTransaction()
        }

        val after = studentCount()

        // Проверяем наличие строки 200 напрямую, без лишних параметров
        val row200 = writableDatabase.rawQuery(
            "SELECT 1 FROM student WHERE id = 200", null
        ).use { it.moveToFirst() }

        return "Откат: было $before, стало $after; строка id=200 существует=$row200"
    }

    fun invalidForeignKey(): String {
        return try {
            insertStudent(writableDatabase, 300, "Студент-300", 999)
            "ОШИБКА: вставка с group_id=999 прошла, хотя не должна"
        } catch (e: Exception) {
            "Ожидаемо отклонено: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    fun schemaText(): String {
        return writableDatabase.rawQuery("PRAGMA table_info(student)", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add("${c.getString(1)} : ${c.getString(2)}")
                }
            }.joinToString("\n")
        }
    }

    fun deleteGroupRestrictDemo(): String {
        return try {
            writableDatabase.delete("`groups`", "id = ?", arrayOf("1"))
            "Удаление группы id=1 прошло"
        } catch (e: Exception) {
            "Удаление группы id=1 отклонено: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun insertOrReplaceStudent(db: SQLiteDatabase, id: Int, name: String) {
        val cols = getStudentColumns(db)
        val cv = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("group_id", 1)
            if ("email" in cols) put("email", DEFAULT_EMAIL)
            if ("enrolled_year" in cols) put("enrolled_year", DEFAULT_YEAR)
        }
        db.insertWithOnConflict("student", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun insertStudent(db: SQLiteDatabase, id: Int, name: String, groupId: Int = 1) {
        val cv = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("group_id", groupId)
            put("email", DEFAULT_EMAIL)
            put("enrolled_year", DEFAULT_YEAR)
        }
        db.insertOrThrow("student", null, cv)
    }

    // Убрали параметр table, так как функция нужна только для таблицы student
    private fun getStudentColumns(db: SQLiteDatabase): Set<String> =
        db.rawQuery("PRAGMA table_info(student)", null).use { c ->
            buildSet {
                while (c.moveToNext()) {
                    add(c.getString(1))
                }
            }
        }
}