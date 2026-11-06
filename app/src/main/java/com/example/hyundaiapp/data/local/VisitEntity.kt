package com.example.hyundaiapp.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.hyundaiapp.data.places.PlaceRole

/** 내비로 간 목적지인지, 그냥 주차(기어 P)한 곳인지 */
enum class VisitSource { NAVI, PARKED }

/**
 * 도착 한 번의 기록. 차 안(Room)에만 저장하고 서버로 보내지 않는다.
 * 내비로 간 곳이면 카카오 검색 결과의 id·이름·카테고리가 같이 들어온다.
 */
@Entity(tableName = "visits", indices = [Index("arrivedAtMillis")])
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: String?,
    val name: String?,
    val category: String?,
    val latitude: Double,
    val longitude: Double,
    val arrivedAtMillis: Long,
    /** 다음 출발(기어 D) 시각. 아직 머무는 중이면 null */
    val departedAtMillis: Long?,
    val source: VisitSource,
)

/** 사용자가 직접 지정한 장소 종류. 자동 분류보다 우선한다 */
@Entity(tableName = "place_labels")
data class PlaceLabelEntity(
    @PrimaryKey val placeKey: String,
    val role: PlaceRole,
)
