package com.getupandgetlit.dingshihai.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.getupandgetlit.dingshihai.data.entity.RuntimeStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuntimeStateDao {
    @Query("SELECT * FROM runtime_state WHERE id = 1")
    fun observeRuntimeState(): Flow<RuntimeStateEntity?>

    @Query("SELECT * FROM runtime_state WHERE id = 1")
    suspend fun getRuntimeState(): RuntimeStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RuntimeStateEntity)

    @Query("DELETE FROM runtime_state")
    suspend fun clear()
}

