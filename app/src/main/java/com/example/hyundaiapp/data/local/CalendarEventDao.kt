package com.example.hyundaiapp.data.local

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

    @Query("DELETE FROM calendar_events WHERE endMillis > :fromMillis AND startMillis < :toMillis")
    abstract suspend fun deleteOverlapping(fromMillis: Long, toMillis: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(events: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events")
    abstract suspend fun deleteAll()

    /**
     * 동기화 범위 안의 캐시를 서버 응답으로 통째로 바꾼다.
     * 서버는 취소된 일정을 보내지 않으므로, 지우고 다시 넣어야 취소된 일정이 사라진다.
     */
    @Transaction
    open suspend fun replaceRange(fromMillis: Long, toMillis: Long, events: List<CalendarEventEntity>) {
        deleteOverlapping(fromMillis, toMillis)
        insertAll(events)
    }
}
