package com.nerve.android.lifelog

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.nerve.android.util.Logger

class UploadQueue(context: Context) {
  private val helper = object : SQLiteOpenHelper(context, "lifelog.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL("""
        CREATE TABLE upload_queue (
          chunk_id TEXT PRIMARY KEY,
          device_id TEXT NOT NULL,
          recorded_at_ms INTEGER NOT NULL,
          duration_ms INTEGER NOT NULL,
          path TEXT NOT NULL,
          status TEXT NOT NULL DEFAULT 'pending',
          attempts INTEGER NOT NULL DEFAULT 0,
          last_error TEXT,
          enqueued_at INTEGER NOT NULL
        )
      """.trimIndent())
      db.execSQL("CREATE INDEX idx_status_recorded ON upload_queue(status, recorded_at_ms)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}
  }
  private val db: SQLiteDatabase get() = try {
    helper.writableDatabase
  } catch (e: Exception) {
    Logger.error("UploadQueue", "db_open_fail", mapOf("reason" to e.message), e)
    throw e
  }

  fun enqueue(c: Chunk) {
    val v = ContentValues().apply {
      put("chunk_id", c.chunkId); put("device_id", c.deviceId)
      put("recorded_at_ms", c.recordedAtMs); put("duration_ms", c.durationMs)
      put("path", c.path); put("status", "pending")
      put("attempts", 0); put("enqueued_at", System.currentTimeMillis())
    }
    db.insertWithOnConflict("upload_queue", null, v, SQLiteDatabase.CONFLICT_IGNORE)
  }

  fun nextPending(includeFailed: Boolean = false): Chunk? {
    val where = if (includeFailed) "status IN ('pending','failed')" else "status = 'pending'"
    db.rawQuery(
      "SELECT chunk_id, device_id, recorded_at_ms, duration_ms, path FROM upload_queue WHERE $where ORDER BY recorded_at_ms ASC LIMIT 1",
      null
    ).use { c ->
      if (!c.moveToFirst()) return null
      return Chunk(c.getString(0), c.getString(1), c.getLong(2), c.getLong(3), c.getString(4))
    }
  }

  fun pendingCount(): Int {
    db.rawQuery("SELECT COUNT(*) FROM upload_queue WHERE status='pending'", null).use { c ->
      return if (c.moveToFirst()) c.getInt(0) else 0
    }
  }

  fun failedCount(): Int {
    db.rawQuery("SELECT COUNT(*) FROM upload_queue WHERE status='failed'", null).use { c ->
      return if (c.moveToFirst()) c.getInt(0) else 0
    }
  }

  fun getAttempts(chunkId: String): Int {
    db.rawQuery("SELECT attempts FROM upload_queue WHERE chunk_id=?", arrayOf(chunkId)).use { c ->
      return if (c.moveToFirst()) c.getInt(0) else 0
    }
  }

  fun markUploading(chunkId: String) { setStatus(chunkId, "uploading", null) }

  fun markDone(chunkId: String) {
    db.delete("upload_queue", "chunk_id=?", arrayOf(chunkId))
  }

  fun markFailed(chunkId: String, err: String) {
    Logger.warn("UploadQueue", "chunk_mark_failed", mapOf("chunkId" to chunkId, "reason" to err))
    db.execSQL(
      "UPDATE upload_queue SET status='failed', attempts=attempts+1, last_error=? WHERE chunk_id=?",
      arrayOf(err, chunkId)
    )
  }

  /** Reset failed -> pending for manual retry. */
  fun retryFailed() {
    db.execSQL("UPDATE upload_queue SET status='pending' WHERE status='failed'")
  }

  private fun setStatus(chunkId: String, status: String, err: String?) {
    val v = ContentValues().apply { put("status", status); err?.let { put("last_error", it) } }
    db.update("upload_queue", v, "chunk_id=?", arrayOf(chunkId))
  }

  fun clear() { db.execSQL("DELETE FROM upload_queue") }
  fun close() { helper.close() }
}
