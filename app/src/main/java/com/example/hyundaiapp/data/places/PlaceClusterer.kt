package com.example.hyundaiapp.data.places

import com.example.hyundaiapp.data.local.VisitEntity
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 같은 장소로 묶인 방문들 */
data class PlaceCluster(val key: String, val visits: List<VisitEntity>) {
    val placeId: String? get() = visits.firstNotNullOfOrNull { it.placeId }

    /** 가장 최근에 기록된 이름·카테고리 */
    val name: String? get() = visits.lastOrNull { it.name != null }?.name
    val category: String? get() = visits.lastOrNull { it.category != null }?.category

    val latitude: Double get() = visits.map { it.latitude }.average()
    val longitude: Double get() = visits.map { it.longitude }.average()
}

/**
 * 방문 기록을 장소별로 묶는다. 같은 곳이라도 주차 위치가 매번 조금씩 다르기 때문이다.
 * - 카카오 id가 같으면 같은 장소
 * - id가 없는 방문끼리는 반경 [SAME_PLACE_METERS] 안이면 같은 장소
 * - id가 없는 방문과 가게(id 있는 장소)는 [SHOP_ATTACH_METERS] 안일 때만 합친다.
 *   집 앞 세탁소처럼 가까운 가게에 집 기록이 빨려 들어가지 않게 하려는 것
 * - id가 서로 다른 두 곳(유치원 옆 마트)은 가까워도 합치지 않는다
 */
object PlaceClusterer {

    const val SAME_PLACE_METERS = 150.0
    const val SHOP_ATTACH_METERS = 75.0

    fun cluster(visits: List<VisitEntity>): List<PlaceCluster> {
        val groups = mutableListOf<Group>()
        for (visit in visits.sortedBy { it.arrivedAtMillis }) {
            val target = visit.placeId?.let { id -> groups.firstOrNull { it.placeId == id } }
                ?: groups
                    .filter { visit.placeId == null || it.placeId == null }
                    .map { it to distanceMeters(it.anchorLat, it.anchorLng, visit.latitude, visit.longitude) }
                    .filter { (group, meters) ->
                        val oneIsShop = visit.placeId != null || group.placeId != null
                        meters <= if (oneIsShop) SHOP_ATTACH_METERS else SAME_PLACE_METERS
                    }
                    .minByOrNull { (_, meters) -> meters }
                    ?.first
            if (target == null) {
                groups += Group(visit.placeId, visit.latitude, visit.longitude, mutableListOf(visit))
            } else {
                target.visits += visit
                if (target.placeId == null) target.placeId = visit.placeId
            }
        }
        return groups.map { PlaceCluster(it.key(), it.visits) }
    }

    /** 두 좌표 사이 거리(m). 하버사인 공식 */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * earthRadius * asin(sqrt(a))
    }

    private class Group(
        var placeId: String?,
        val anchorLat: Double,
        val anchorLng: Double,
        val visits: MutableList<VisitEntity>,
    ) {
        /** 사용자 라벨을 붙일 때 쓰는 키. 카카오 id가 있으면 그것, 없으면 첫 방문 좌표 */
        fun key(): String = placeId?.let { "kakao:$it" }
            ?: String.format(Locale.US, "geo:%.4f,%.4f", anchorLat, anchorLng)
    }
}
