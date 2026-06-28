package com.google.ai.edge.gallery.customtasks.hermesagent.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "HermesMemory"
private const val DB_NAME = "hermes_memory.db"
private const val DB_VERSION = 1

data class MemoryEntry(
  val id: Long = 0,
  val key: String,
  val content: String,
  val category: String,
  val importance: Float = 0.5f,
  val accessCount: Int = 0,
  val createdAt: Long = System.currentTimeMillis(),
  val lastAccessedAt: Long = System.currentTimeMillis(),
  val metadata: Map<String, String> = emptyMap()
)

class HermesMemory(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL("""
      CREATE TABLE memories (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        key TEXT NOT NULL,
        content TEXT NOT NULL,
        category TEXT NOT NULL DEFAULT 'fact',
        importance REAL DEFAULT 0.5,
        access_count INTEGER DEFAULT 0,
        created_at INTEGER NOT NULL,
        last_accessed_at INTEGER NOT NULL,
        metadata TEXT DEFAULT '{}'
      )
    """)
    db.execSQL("CREATE INDEX idx_memories_key ON memories(key)")
    db.execSQL("CREATE INDEX idx_memories_category ON memories(category)")
    db.execSQL("CREATE INDEX idx_memories_importance ON memories(importance DESC)")
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP TABLE IF EXISTS memories")
    onCreate(db)
  }

  suspend fun store(
    key: String,
    content: String,
    category: String = "fact",
    importance: Float = 0.5f,
    metadata: Map<String, String> = emptyMap()
  ): Long = withContext(Dispatchers.IO) {
    val values = ContentValues().apply {
      put("key", key)
      put("content", content)
      put("category", category)
      put("importance", importance)
      put("created_at", System.currentTimeMillis())
      put("last_accessed_at", System.currentTimeMillis())
      put("metadata", JSONObject(metadata).toString())
    }
    val id = writableDatabase.insert("memories", null, values)
    Log.d(TAG, "Stored memory: key=$key, id=$id")
    id
  }

  suspend fun recall(
    query: String,
    category: String? = null,
    maxResults: Int = 10
  ): List<MemoryEntry> = withContext(Dispatchers.IO) {
    val results = mutableListOf<MemoryEntry>()
    val selectionParts = mutableListOf<String>()
    val selectionArgs = mutableListOf<String>()

    if (query.isNotBlank()) {
      selectionParts.add("(key LIKE ? OR content LIKE ?)")
      selectionArgs.add("%$query%")
      selectionArgs.add("%$query%")
    }
    if (category != null) {
      selectionParts.add("category = ?")
      selectionArgs.add(category)
    }

    val selection = if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null

    val cursor = readableDatabase.query(
      "memories",
      null,
      selection,
      if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null,
      null,
      null,
      "importance DESC, last_accessed_at DESC",
      maxResults.toString()
    )

    cursor.use {
      while (it.moveToNext()) {
        val id = it.getLong(it.getColumnIndexOrThrow("id"))
        val key = it.getString(it.getColumnIndexOrThrow("key"))
        val content = it.getString(it.getColumnIndexOrThrow("content"))
        val cat = it.getString(it.getColumnIndexOrThrow("category"))
        val importance = it.getFloat(it.getColumnIndexOrThrow("importance"))
        val accessCount = it.getInt(it.getColumnIndexOrThrow("access_count"))
        val createdAt = it.getLong(it.getColumnIndexOrThrow("created_at"))
        val lastAccessed = it.getLong(it.getColumnIndexOrThrow("last_accessed_at"))
        val metadataStr = it.getString(it.getColumnIndexOrThrow("metadata"))

        val metadata = try {
          val json = JSONObject(metadataStr)
          json.keys().asSequence().associateWith { json.getString(it) }
        } catch (e: Exception) { emptyMap() }

        results.add(MemoryEntry(id, key, content, cat, importance, accessCount, createdAt, lastAccessed, metadata))
      }
    }

    // Update access counts for retrieved memories
    results.forEach { entry ->
      updateAccessCount(entry.id)
    }

    Log.d(TAG, "Recalled ${results.size} memories for query: $query")
    results
  }

  private fun updateAccessCount(id: Long) {
    val values = ContentValues().apply {
      put("access_count", "access_count + 1")
      put("last_accessed_at", System.currentTimeMillis())
    }
    writableDatabase.update("memories", values, "id = ?", arrayOf(id.toString()))
  }

  suspend fun update(
    id: Long,
    content: String? = null,
    importance: Float? = null,
    metadata: Map<String, String>? = null
  ) = withContext(Dispatchers.IO) {
    val values = ContentValues()
    content?.let { values.put("content", it) }
    importance?.let { values.put("importance", it) }
    metadata?.let { values.put("metadata", JSONObject(it).toString()) }
    if (values.size() > 0) {
      writableDatabase.update("memories", values, "id = ?", arrayOf(id.toString()))
    }
  }

  suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
    writableDatabase.delete("memories", "id = ?", arrayOf(id.toString()))
  }

  suspend fun deleteByKey(key: String) = withContext(Dispatchers.IO) {
    writableDatabase.delete("memories", "key = ?", arrayOf(key))
  }

  suspend fun getAll(category: String? = null, limit: Int = 100): List<MemoryEntry> = withContext(Dispatchers.IO) {
    val results = mutableListOf<MemoryEntry>()
    val selection = if (category != null) "category = ?" else null
    val args = if (category != null) arrayOf(category) else null

    val cursor = readableDatabase.query(
      "memories", null, selection, args, null, null,
      "last_accessed_at DESC", limit.toString()
    )

    cursor.use {
      while (it.moveToNext()) {
        val id = it.getLong(it.getColumnIndexOrThrow("id"))
        val key = it.getString(it.getColumnIndexOrThrow("key"))
        val content = it.getString(it.getColumnIndexOrThrow("content"))
        val cat = it.getString(it.getColumnIndexOrThrow("category"))
        val importance = it.getFloat(it.getColumnIndexOrThrow("importance"))
        val accessCount = it.getInt(it.getColumnIndexOrThrow("access_count"))
        val createdAt = it.getLong(it.getColumnIndexOrThrow("created_at"))
        val lastAccessed = it.getLong(it.getColumnIndexOrThrow("last_accessed_at"))
        results.add(MemoryEntry(id, key, content, cat, importance, accessCount, createdAt, lastAccessed))
      }
    }
    results
  }

  suspend fun getStats(): Map<String, Any> = withContext(Dispatchers.IO) {
    val cursor = readableDatabase.rawQuery("SELECT COUNT(*) as count, category FROM memories GROUP BY category", null)
    val stats = mutableMapOf<String, Any>()
    var totalCount = 0
    cursor.use {
      while (it.moveToNext()) {
        val count = it.getInt(0)
        val category = it.getString(1)
        stats[category] = count
        totalCount += count
      }
    }
    stats["total"] = totalCount
    stats
  }

  suspend fun prune(maxPerCategory: Int = 500, minImportance: Float = 0.1f) = withContext(Dispatchers.IO) {
    writableDatabase.execSQL("""
      DELETE FROM memories WHERE id NOT IN (
        SELECT id FROM memories
        WHERE importance >= $minImportance
        ORDER BY importance DESC, last_accessed_at DESC
        LIMIT $maxPerCategory
      )
    """)
  }

  override fun close() {
    writableDatabase.close()
    readableDatabase.close()
  }
}
