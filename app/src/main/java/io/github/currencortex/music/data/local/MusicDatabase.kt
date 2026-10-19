package io.github.currencortex.music.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class SearchHistoryEntity(@PrimaryKey val keyword: String, val usedAt: Long)
@Entity data class QueueSnapshotEntity(@PrimaryKey val id: Int = 1, val payload: String)
@Entity data class CachedSong(@PrimaryKey val id: Long, val payload: String)
@Entity(primaryKeys = ["id", "scope"]) data class CachedLyrics(val id: Long, val payload: String,
    @ColumnInfo(defaultValue = "'netease'") val scope: String = "netease")
@Dao interface MusicDao {
    @Query("SELECT * FROM SearchHistoryEntity ORDER BY usedAt DESC LIMIT 20") fun history(): Flow<List<SearchHistoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun remember(value: SearchHistoryEntity)
    @Query("DELETE FROM SearchHistoryEntity WHERE keyword = :keyword") suspend fun deleteHistory(keyword: String)
    @Query("DELETE FROM SearchHistoryEntity") suspend fun clearHistory()
    @Query("SELECT * FROM QueueSnapshotEntity WHERE id = 1") suspend fun queue(): QueueSnapshotEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveQueue(value: QueueSnapshotEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun cacheSong(value: CachedSong)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun cacheLyrics(value: CachedLyrics)
    @Query("SELECT * FROM CachedLyrics WHERE id = :id AND scope = :scope") suspend fun lyrics(id: Long, scope: String = "netease"): CachedLyrics?
    @Query("DELETE FROM CachedLyrics") suspend fun clearLyrics()
}
@Database(entities = [SearchHistoryEntity::class, QueueSnapshotEntity::class, CachedSong::class, CachedLyrics::class], version = 2, exportSchema = true)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun music(): MusicDao
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE CachedLyrics_scoped (id INTEGER NOT NULL, payload TEXT NOT NULL, scope TEXT NOT NULL DEFAULT 'netease', PRIMARY KEY(id, scope))")
                // Old releases mixed providers in this table. Keep the rows, but never
                // mislabel them as native lyrics; either selected source will refetch.
                db.execSQL("INSERT INTO CachedLyrics_scoped (id, payload, scope) SELECT id, payload, 'legacy' FROM CachedLyrics")
                db.execSQL("DROP TABLE CachedLyrics")
                db.execSQL("ALTER TABLE CachedLyrics_scoped RENAME TO CachedLyrics")
            }
        }
    }
}
