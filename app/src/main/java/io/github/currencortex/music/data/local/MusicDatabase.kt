package io.github.currencortex.music.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class SearchHistoryEntity(@PrimaryKey val keyword: String, val usedAt: Long)
@Entity data class QueueSnapshotEntity(@PrimaryKey val id: Int = 1, val payload: String)
@Entity data class CachedSong(@PrimaryKey val id: Long, val payload: String)
@Entity data class CachedLyrics(@PrimaryKey val id: Long, val payload: String)
@Dao interface MusicDao {
    @Query("SELECT * FROM SearchHistoryEntity ORDER BY usedAt DESC LIMIT 20") fun history(): Flow<List<SearchHistoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun remember(value: SearchHistoryEntity)
    @Query("DELETE FROM SearchHistoryEntity WHERE keyword = :keyword") suspend fun deleteHistory(keyword: String)
    @Query("DELETE FROM SearchHistoryEntity") suspend fun clearHistory()
    @Query("SELECT * FROM QueueSnapshotEntity WHERE id = 1") suspend fun queue(): QueueSnapshotEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveQueue(value: QueueSnapshotEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun cacheSong(value: CachedSong)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun cacheLyrics(value: CachedLyrics)
    @Query("SELECT * FROM CachedLyrics WHERE id = :id") suspend fun lyrics(id: Long): CachedLyrics?
    @Query("DELETE FROM CachedLyrics") suspend fun clearLyrics()
}
@Database(entities = [SearchHistoryEntity::class, QueueSnapshotEntity::class, CachedSong::class, CachedLyrics::class], version = 1, exportSchema = true)
abstract class MusicDatabase : RoomDatabase() { abstract fun music(): MusicDao }
