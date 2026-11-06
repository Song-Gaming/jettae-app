package com.example.hyundaiapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 앱 로컬 DB. 버전을 올릴 때는 아래 MIGRATION을 추가해 기존 방문 기록을 지키지 않고 날리는 일이 없게 한다 */
@Database(
    entities = [CalendarEventEntity::class, VisitEntity::class, PlaceLabelEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun calendarEventDao(): CalendarEventDao

    abstract fun visitDao(): VisitDao

    abstract fun placeLabelDao(): PlaceLabelDao

    companion object {
        /** 1 → 2: 방문 기록과 장소 라벨 테이블 추가 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `visits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`placeId` TEXT, `name` TEXT, `category` TEXT, `latitude` REAL NOT NULL, " +
                        "`longitude` REAL NOT NULL, `arrivedAtMillis` INTEGER NOT NULL, " +
                        "`departedAtMillis` INTEGER, `source` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_visits_arrivedAtMillis` ON `visits` (`arrivedAtMillis`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `place_labels` (`placeKey` TEXT NOT NULL, " +
                        "`role` TEXT NOT NULL, PRIMARY KEY(`placeKey`))"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "hyundai-app.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
