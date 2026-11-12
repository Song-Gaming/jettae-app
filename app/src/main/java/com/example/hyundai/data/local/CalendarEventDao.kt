package com.example.hyundai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CalendarEventDao {

    @Query("SELECT * FROM calendar_events WHERE endMillis > :fromMillis ORDER BY startMillis, allDay DESC")
    abstract fun observeEndingAfter(fromMillis: Long): Flow<List<CalendarEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(events: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events")
    abstract suspend fun deleteAll()

    /**
     * 캐시를 서버 응답으로 통째로 바꾼다. 동기화 범위가 앞으로의 일정 전부라 범위 밖에 남길 것이 없다.
     * 지우고 다시 넣어야 취소된 일정과 이미 끝난 일정이 사라진다.
     */
    @Transaction
    open suspend fun replaceAll(events: List<CalendarEventEntity>) {
        deleteAll()
        insertAll(events)
    }
}
