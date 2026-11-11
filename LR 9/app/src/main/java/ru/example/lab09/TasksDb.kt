package ru.example.lab09

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Task(
    val id: Long,
    val title: String,
    val done: Boolean
)

class TasksDb(context: Context) : SQLiteOpenHelper(context, "tasks.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                done INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS tasks")
        onCreate(db)
    }

    fun add(title: String) {
        val db = writableDatabase
        db.execSQL("INSERT INTO tasks (title, done) VALUES (?, 0)", arrayOf(title))
    }

    fun read(): List<Task> {
        val list = mutableListOf<Task>()
        readableDatabase.rawQuery("SELECT id, title, done FROM tasks ORDER BY id DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    Task(
                        id = cursor.getLong(0),
                        title = cursor.getString(1),
                        done = cursor.getInt(2) == 1
                    )
                )
            }
        }
        return list
    }

    fun updateDone(id: Long, done: Boolean) {
        val db = writableDatabase
        val doneInt = if (done) 1 else 0
        db.execSQL("UPDATE tasks SET done = ? WHERE id = ?", arrayOf(doneInt, id))
    }

    fun delete(id: Long) {
        val db = writableDatabase
        db.execSQL("DELETE FROM tasks WHERE id = ?", arrayOf(id))
    }
}