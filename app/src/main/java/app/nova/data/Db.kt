package app.nova.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "memories")
data class MemoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val kind: String, // fact | summary | note
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // user | assistant
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun all(): Flow<List<MemoryItem>>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    suspend fun allNow(): List<MemoryItem>

    @Insert
    suspend fun insert(m: MemoryItem): Long

    @Update
    suspend fun update(m: MemoryItem)

    @Delete
    suspend fun delete(m: MemoryItem)

    @Query("DELETE FROM memories")
    suspend fun clear()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY id ASC")
    fun all(): Flow<List<MessageItem>>

    @Query("SELECT * FROM messages ORDER BY id ASC")
    suspend fun allNow(): List<MessageItem>

    @Query("SELECT * FROM messages ORDER BY id DESC LIMIT :n")
    suspend fun last(n: Int): List<MessageItem>

    @Insert
    suspend fun insert(m: MessageItem): Long

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Database(entities = [MemoryItem::class, MessageItem::class], version = 1, exportSchema = false)
abstract class NovaDb : RoomDatabase() {
    abstract fun memories(): MemoryDao
    abstract fun messages(): MessageDao
}
