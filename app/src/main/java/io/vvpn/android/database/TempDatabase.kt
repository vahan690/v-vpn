package io.vvpn.android.database

import androidx.room.Room
import androidx.room.RoomDatabase
import io.vvpn.android.SagerNet.Companion.app
import io.vvpn.android.database.preference.KeyValuePair
import io.vvpn.android.ktx.runOnDefaultDispatcher

@androidx.room.Database(entities = [KeyValuePair::class], version = 1)
abstract class TempDatabase : RoomDatabase() {

    companion object {
        @Suppress("EXPERIMENTAL_API_USAGE")
        private val instance by lazy {
            Room.inMemoryDatabaseBuilder(app, TempDatabase::class.java)
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration(true)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .setQueryExecutor { runOnDefaultDispatcher { it.run() } }
                .build()
        }

        val profileCacheDao get() = instance.profileCacheDao()

    }

    abstract fun profileCacheDao(): KeyValuePair.Dao
}