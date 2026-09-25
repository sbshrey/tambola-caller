package io.github.sbshrey.tambola.game.data

import android.content.Context
import androidx.room.*
import io.github.sbshrey.tambola.domain.Round
import io.github.sbshrey.tambola.domain.RoundCodec
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rounds")
data class SavedRound(@PrimaryKey val id: String, val createdAt: Long, val completed: Boolean, val payload: String)

@Dao
interface RoundDao {
    @Query("SELECT * FROM rounds ORDER BY createdAt DESC") fun observe(): Flow<List<SavedRound>>
    @Query("SELECT * FROM rounds WHERE completed = 0 ORDER BY createdAt DESC LIMIT 1") suspend fun active(): SavedRound?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(round: SavedRound)
    @Query("DELETE FROM rounds") suspend fun deleteAll()
}

@Database(entities = [SavedRound::class], version = 1, exportSchema = true)
abstract class GameDatabase : RoomDatabase() {
    abstract fun rounds(): RoundDao
    companion object {
        fun open(context: Context): GameDatabase = Room.databaseBuilder(context.applicationContext, GameDatabase::class.java, "tambola-together.db").build()
    }
}

class LocalGameRepository(private val database: GameDatabase) {
    private val dao = database.rounds()
    val history = dao.observe()
    suspend fun restore(): Round? = dao.active()?.let { RoundCodec.decode(it.payload).pause() }
    suspend fun save(round: Round) = dao.save(SavedRound(round.id, round.createdAt, round.finished, RoundCodec.encode(round)))
    suspend fun replaceActive(previous: Round?, next: Round) = database.withTransaction {
        previous?.takeIf { !it.finished }?.let { save(it.cancel()) }
        save(next)
    }
    suspend fun deleteAll() = dao.deleteAll()
}
