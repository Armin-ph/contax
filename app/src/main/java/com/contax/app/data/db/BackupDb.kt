package com.contax.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "backups")
data class BackupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val fileName: String? = null,
    val uri: String? = null,
    val format: String,
    val destination: String,
    val encryption: String,
    val sizeKb: Double,
    val contactCount: Int,
    val isAuto: Boolean,
    val createdAt: Long
)

@Dao
interface BackupDao {
    @Insert
    suspend fun insert(e: BackupEntity): Long

    @Query("SELECT * FROM backups ORDER BY createdAt DESC")
    suspend fun all(): List<BackupEntity>

    @Query("SELECT * FROM backups WHERE id = :id")
    suspend fun byId(id: Long): BackupEntity?

    @Query("DELETE FROM backups WHERE id = :id")
    suspend fun delete(id: Long)

    /** همه بکاپ‌های خودکار، جدید به قدیمی — برای هرس نسخه‌های قدیمی */
    @Query("SELECT * FROM backups WHERE isAuto = 1 ORDER BY createdAt DESC")
    suspend fun autoAll(): List<BackupEntity>

    @Query("SELECT MAX(createdAt) FROM backups")
    suspend fun lastCreatedAt(): Long?
}

@Database(entities = [BackupEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun backupDao(): BackupDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "contax.db"
                ).build().also { instance = it }
            }
    }
}
