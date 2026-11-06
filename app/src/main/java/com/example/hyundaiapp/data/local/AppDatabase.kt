package com.example.hyundaiapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** 앱 로컬 DB. 방문 로그·즐겨찾기 테이블도 이후 여기에 추가한다 */
@Database(entities = [CalendarEventEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun calendarEventDao(): CalendarEventDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "hyundai-app.db").build()
    }
}
