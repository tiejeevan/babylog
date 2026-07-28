package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.data.database.BabyCareDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full local ZIP backup/restore for Room DB, memories media, and key SharedPreferences.
 */
object FullBackupManager {
    private const val TAG = "FullBackupManager"
    const val FORMAT_VERSION = 1
    val DB_NAME = BabyCareDatabase.DATABASE_NAME
    private const val MANIFEST = "manifest.json"
    private const val DB_DIR = "database/"
    private const val PREFS_DIR = "prefs/"
    private const val FILES_DIR = "files/"
    private const val PREF_CARE_SYNC = "care_sync_prefs"
    private const val PREF_SLEEP_SOUND = "sleep_sound_prefs"

    sealed class BackupResult {
        data class Success(
            val message: String,
            val requiresAppRestart: Boolean = false
        ) : BackupResult()

        data class Failure(val message: String) : BackupResult()
    }

    fun suggestedBackupFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "BabyCareLive_backup_$stamp.zip"
    }

    suspend fun createBackup(context: Context, outputUri: Uri): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                val app = context.applicationContext
                val db = BabyCareDatabase.getDatabase(app)
                checkpointWal(db)

                val filesDir = app.filesDir
                val memoriesDir = File(filesDir, "memories")
                val dbFile = app.getDatabasePath(DB_NAME)
                if (!dbFile.exists()) {
                    return@withContext BackupResult.Failure("Database file not found.")
                }

                var memoryFileCount = 0
                app.contentResolver.openOutputStream(outputUri)?.use { rawOut ->
                    ZipOutputStream(BufferedOutputStream(rawOut)).use { zip ->
                        // Manifest last so we know counts — write placeholder after collecting
                        val memoryFiles = collectFiles(memoriesDir)
                        memoryFileCount = memoryFiles.size

                        val manifest = JSONObject().apply {
                            put("formatVersion", FORMAT_VERSION)
                            put("appVersionName", BuildConfig.VERSION_NAME)
                            put("appVersionCode", BuildConfig.VERSION_CODE)
                            put("dbVersion", db.openHelper.readableDatabase.version)
                            put("createdAtMillis", System.currentTimeMillis())
                            put("filesDirAbsolute", filesDir.absolutePath)
                            put("memoryFileCount", memoryFileCount)
                            put("databaseName", DB_NAME)
                        }
                        putZipString(zip, MANIFEST, manifest.toString(2))

                        putZipFile(zip, "$DB_DIR$DB_NAME", dbFile)
                        listOf("-wal", "-shm").forEach { suffix ->
                            val side = File(dbFile.path + suffix)
                            if (side.exists() && side.length() > 0L) {
                                putZipFile(zip, "$DB_DIR$DB_NAME$suffix", side)
                            }
                        }

                        putZipString(
                            zip,
                            "$PREFS_DIR$PREF_CARE_SYNC.json",
                            dumpPrefs(app, PREF_CARE_SYNC).toString(2)
                        )
                        putZipString(
                            zip,
                            "$PREFS_DIR$PREF_SLEEP_SOUND.json",
                            dumpPrefs(app, PREF_SLEEP_SOUND).toString(2)
                        )

                        for (file in memoryFiles) {
                            val relative = file.relativeTo(filesDir).path.replace('\\', '/')
                            putZipFile(zip, "$FILES_DIR$relative", file)
                        }

                        // Profile photo outside memories/, if any
                        includeExtraProfilePhoto(app, zip, filesDir, memoriesDir)
                    }
                } ?: return@withContext BackupResult.Failure("Could not open backup destination.")

                BackupResult.Success(
                    message = "Backup saved ($memoryFileCount media files)."
                )
            } catch (e: Exception) {
                Log.e(TAG, "createBackup failed", e)
                BackupResult.Failure(e.message ?: "Backup failed.")
            }
        }

    suspend fun restoreBackup(context: Context, inputUri: Uri): BackupResult =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val staging = File(app.cacheDir, "backup_restore_${System.currentTimeMillis()}")
            val safety = File(app.cacheDir, "backup_safety_${System.currentTimeMillis()}")
            try {
                if (!staging.mkdirs()) {
                    return@withContext BackupResult.Failure("Could not create staging directory.")
                }

                app.contentResolver.openInputStream(inputUri)?.use { rawIn ->
                    unzipToDirectory(rawIn, staging)
                } ?: return@withContext BackupResult.Failure("Could not open backup file.")

                val manifestFile = File(staging, MANIFEST)
                if (!manifestFile.exists()) {
                    return@withContext BackupResult.Failure("Invalid backup: missing manifest.")
                }
                val manifest = JSONObject(manifestFile.readText())
                val formatVersion = manifest.optInt("formatVersion", -1)
                if (formatVersion != FORMAT_VERSION) {
                    return@withContext BackupResult.Failure(
                        "Unsupported backup format version: $formatVersion"
                    )
                }

                val stagedDb = File(staging, "$DB_DIR$DB_NAME")
                if (!stagedDb.exists()) {
                    return@withContext BackupResult.Failure("Invalid backup: missing database.")
                }

                val oldFilesDir = manifest.optString("filesDirAbsolute", "")
                val liveDb = app.getDatabasePath(DB_NAME)
                val liveMemories = File(app.filesDir, "memories")

                // Safety copy of current data before swap
                safety.mkdirs()
                if (liveDb.exists()) {
                    liveDb.copyTo(File(safety, DB_NAME), overwrite = true)
                    listOf("-wal", "-shm").forEach { suffix ->
                        val side = File(liveDb.path + suffix)
                        if (side.exists()) {
                            side.copyTo(File(safety, "$DB_NAME$suffix"), overwrite = true)
                        }
                    }
                }
                if (liveMemories.exists()) {
                    liveMemories.copyRecursively(File(safety, "memories"), overwrite = true)
                }

                try {
                    BabyCareDatabase.closeAndClearInstance()

                    // Replace database files
                    liveDb.parentFile?.mkdirs()
                    deleteDbSidecars(liveDb)
                    stagedDb.copyTo(liveDb, overwrite = true)
                    listOf("-wal", "-shm").forEach { suffix ->
                        val stagedSide = File(staging, "$DB_DIR$DB_NAME$suffix")
                        val liveSide = File(liveDb.path + suffix)
                        if (stagedSide.exists()) {
                            stagedSide.copyTo(liveSide, overwrite = true)
                        } else if (liveSide.exists()) {
                            liveSide.delete()
                        }
                    }

                    // Replace memories
                    if (liveMemories.exists()) {
                        liveMemories.deleteRecursively()
                    }
                    val stagedMemories = File(staging, "files/memories")
                    if (stagedMemories.exists()) {
                        stagedMemories.copyRecursively(liveMemories, overwrite = true)
                    }

                    // Extra files under files/ that are not memories (e.g. profile photo)
                    val stagedFilesRoot = File(staging, "files")
                    if (stagedFilesRoot.exists()) {
                        stagedFilesRoot.walkTopDown().filter { it.isFile }.forEach { stagedFile ->
                            val relative = stagedFile.relativeTo(stagedFilesRoot).path
                            if (!relative.startsWith("memories")) {
                                val dest = File(app.filesDir, relative)
                                dest.parentFile?.mkdirs()
                                stagedFile.copyTo(dest, overwrite = true)
                            }
                        }
                    }

                    // Prefs
                    restorePrefsFromFile(
                        app,
                        PREF_CARE_SYNC,
                        File(staging, "$PREFS_DIR$PREF_CARE_SYNC.json")
                    )
                    restorePrefsFromFile(
                        app,
                        PREF_SLEEP_SOUND,
                        File(staging, "$PREFS_DIR$PREF_SLEEP_SOUND.json")
                    )

                    // Remap absolute media paths if filesDir changed
                    val newDb = BabyCareDatabase.getDatabase(app)
                    if (oldFilesDir.isNotBlank() && oldFilesDir != app.filesDir.absolutePath) {
                        remapAbsolutePaths(newDb, oldFilesDir, app.filesDir.absolutePath)
                    }
                    // Always normalize memory paths that point under .../files/memories to current filesDir
                    remapMemoriesToCurrentFilesDir(newDb, app.filesDir)

                    BabyCareDatabase.closeAndClearInstance()
                } catch (swapError: Exception) {
                    Log.e(TAG, "Restore swap failed; attempting rollback", swapError)
                    rollbackFromSafety(app, safety, liveDb, liveMemories)
                    BabyCareDatabase.closeAndClearInstance()
                    BabyCareDatabase.getDatabase(app)
                    throw swapError
                }

                BackupResult.Success(
                    message = "Restore complete. Restarting app…",
                    requiresAppRestart = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "restoreBackup failed", e)
                BackupResult.Failure(e.message ?: "Restore failed.")
            } finally {
                staging.deleteRecursively()
                safety.deleteRecursively()
            }
        }

    private fun rollbackFromSafety(
        app: Context,
        safety: File,
        liveDb: File,
        liveMemories: File
    ) {
        try {
            BabyCareDatabase.closeAndClearInstance()
            val safeDb = File(safety, DB_NAME)
            if (safeDb.exists()) {
                deleteDbSidecars(liveDb)
                safeDb.copyTo(liveDb, overwrite = true)
                listOf("-wal", "-shm").forEach { suffix ->
                    val side = File(safety, "$DB_NAME$suffix")
                    val liveSide = File(liveDb.path + suffix)
                    if (side.exists()) {
                        side.copyTo(liveSide, overwrite = true)
                    } else if (liveSide.exists()) {
                        liveSide.delete()
                    }
                }
            }
            val safeMemories = File(safety, "memories")
            if (liveMemories.exists()) liveMemories.deleteRecursively()
            if (safeMemories.exists()) {
                safeMemories.copyRecursively(liveMemories, overwrite = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Rollback failed", e)
        }
    }

    private fun deleteDbSidecars(dbFile: File) {
        listOf("-wal", "-shm").forEach { suffix ->
            File(dbFile.path + suffix).delete()
        }
    }

    private fun checkpointWal(db: BabyCareDatabase) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
            cursor.moveToFirst()
        }
    }

    private fun remapAbsolutePaths(
        db: BabyCareDatabase,
        oldPrefix: String,
        newPrefix: String
    ) {
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL(
            "UPDATE memory_items SET localPath = REPLACE(localPath, ?, ?) WHERE localPath LIKE ?",
            arrayOf(oldPrefix, newPrefix, "$oldPrefix%")
        )
        sqlite.execSQL(
            "UPDATE memory_items SET thumbPath = REPLACE(thumbPath, ?, ?) WHERE thumbPath LIKE ?",
            arrayOf(oldPrefix, newPrefix, "$oldPrefix%")
        )
        sqlite.execSQL(
            "UPDATE baby_profile SET photoUri = REPLACE(photoUri, ?, ?) WHERE photoUri LIKE ?",
            arrayOf(oldPrefix, newPrefix, "$oldPrefix%")
        )
    }

    /**
     * Rewrites absolute paths that contain `/files/memories` (or `/files/`) to the current
     * [filesDir], covering backups from other user profiles / devices.
     */
    private fun remapMemoriesToCurrentFilesDir(db: BabyCareDatabase, filesDir: File) {
        val sqlite = db.openHelper.writableDatabase
        val marker = "/files/memories"
        val newMemoriesRoot = File(filesDir, "memories").absolutePath
        sqlite.query("SELECT id, localPath, thumbPath FROM memory_items").use { c ->
            val idIdx = c.getColumnIndexOrThrow("id")
            val localIdx = c.getColumnIndexOrThrow("localPath")
            val thumbIdx = c.getColumnIndexOrThrow("thumbPath")
            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val local = c.getString(localIdx).orEmpty()
                val thumb = c.getString(thumbIdx).orEmpty()
                val newLocal = rewriteUnderMarker(local, marker, newMemoriesRoot)
                val newThumb = rewriteUnderMarker(thumb, marker, newMemoriesRoot)
                if (newLocal != local || newThumb != thumb) {
                    sqlite.execSQL(
                        "UPDATE memory_items SET localPath = ?, thumbPath = ? WHERE id = ?",
                        arrayOf<Any?>(newLocal, newThumb, id)
                    )
                }
            }
        }
        sqlite.query("SELECT id, photoUri FROM baby_profile").use { c ->
            val idIdx = c.getColumnIndexOrThrow("id")
            val photoIdx = c.getColumnIndexOrThrow("photoUri")
            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val photo = c.getString(photoIdx) ?: continue
                val filesMarker = "/files/"
                val rewritten = rewriteUnderMarker(photo, filesMarker, filesDir.absolutePath + "/")
                if (rewritten != photo) {
                    sqlite.execSQL(
                        "UPDATE baby_profile SET photoUri = ? WHERE id = ?",
                        arrayOf<Any?>(rewritten, id)
                    )
                }
            }
        }
    }

    private fun rewriteUnderMarker(path: String, marker: String, newRoot: String): String {
        if (path.isBlank()) return path
        val idx = path.indexOf(marker)
        if (idx < 0) return path
        val suffix = path.substring(idx + marker.length).trimStart('/')
        return if (suffix.isEmpty()) newRoot.trimEnd('/')
        else File(newRoot.trimEnd('/'), suffix).absolutePath
    }

    private fun includeExtraProfilePhoto(
        app: Context,
        zip: ZipOutputStream,
        filesDir: File,
        memoriesDir: File
    ) {
        try {
            val db = BabyCareDatabase.getDatabase(app)
            db.openHelper.readableDatabase.query(
                "SELECT photoUri FROM baby_profile WHERE id = 1 LIMIT 1"
            ).use { c ->
                if (!c.moveToFirst()) return
                val photoUri = c.getString(0) ?: return
                if (!photoUri.startsWith("/") && !photoUri.startsWith("file:")) return
                val path = photoUri.removePrefix("file://")
                val file = File(path)
                if (!file.exists() || !file.absolutePath.startsWith(filesDir.absolutePath)) return
                if (file.absolutePath.startsWith(memoriesDir.absolutePath)) return
                val relative = file.relativeTo(filesDir).path.replace('\\', '/')
                putZipFile(zip, "$FILES_DIR$relative", file)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not include profile photo in backup", e)
        }
    }

    private fun dumpPrefs(context: Context, name: String): JSONObject {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val entries = JSONObject()
        for ((key, value) in prefs.all) {
            if (value == null) continue
            val item = JSONObject()
            when (value) {
                is String -> {
                    item.put("type", "string")
                    item.put("value", value)
                }
                is Boolean -> {
                    item.put("type", "boolean")
                    item.put("value", value)
                }
                is Int -> {
                    item.put("type", "int")
                    item.put("value", value)
                }
                is Long -> {
                    item.put("type", "long")
                    item.put("value", value)
                }
                is Float -> {
                    item.put("type", "float")
                    item.put("value", value.toDouble())
                }
                else -> {
                    item.put("type", "string")
                    item.put("value", value.toString())
                }
            }
            entries.put(key, item)
        }
        return JSONObject().put("entries", entries)
    }

    private fun restorePrefsFromFile(context: Context, name: String, file: File) {
        if (!file.exists()) return
        val root = JSONObject(file.readText())
        val entries = root.optJSONObject("entries") ?: return
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val editor = prefs.edit().clear()
        val keys = entries.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = entries.getJSONObject(key)
            when (item.getString("type")) {
                "string" -> editor.putString(key, item.getString("value"))
                "boolean" -> editor.putBoolean(key, item.getBoolean("value"))
                "int" -> editor.putInt(key, item.getInt("value"))
                "long" -> editor.putLong(key, item.getLong("value"))
                "float" -> editor.putFloat(key, item.getDouble("value").toFloat())
            }
        }
        editor.commit()
    }

    private fun collectFiles(dir: File): List<File> {
        if (!dir.exists()) return emptyList()
        return dir.walkTopDown().filter { it.isFile }.toList()
    }

    private fun putZipString(zip: ZipOutputStream, entryName: String, content: String) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun putZipFile(zip: ZipOutputStream, entryName: String, file: File) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    private fun unzipToDirectory(input: InputStream, destDir: File) {
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (!outFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator) &&
                    outFile.canonicalPath != destDir.canonicalPath
                ) {
                    throw SecurityException("Zip path traversal: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
