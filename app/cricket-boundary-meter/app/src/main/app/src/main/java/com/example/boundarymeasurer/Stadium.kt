package com.example.boundarymeasurer

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "stadiums")
data class Stadium(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

@Dao
interface StadiumDao {
    @Query("SELECT * FROM stadiums")
    fun getAllStadiums(): Flow<List<Stadium>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStadium(stadium: Stadium)

    @Delete
    suspend fun deleteStadium(stadium: Stadium)
}

@Database(entities = [Stadium::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stadiumDao(): StadiumDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stadium_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
