package com.example.hyundai.data.places

import com.example.hyundai.data.local.PlaceLabelDao
import com.example.hyundai.data.local.PlaceLabelEntity
import com.example.hyundai.data.local.VisitDao
import com.example.hyundai.data.local.VisitEntity
import com.example.hyundai.data.local.VisitSource
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime

/** 도착 한 건. 내비로 간 곳이면 카카오 검색 결과의 id·이름·카테고리를 같이 넘긴다 */
data class Arrival(
    val placeId: String?,
    val name: String?,
    val category: String?,
    val latitude: Double,
    val longitude: Double,
    val source: VisitSource,
)

/** 도착·출발 이벤트 때 부르는 입구 */
interface VisitRecorder {
    /** 도착 시 호출. 돌려준 id를 출발 때 넘긴다 */
    suspend fun recordArrival(arrival: Arrival): Long

    /** 출발(기어 D) 시 호출. 머문 시간 계산에 쓴다 */
    suspend fun recordDeparture(visitId: Long)
}

/** 분류·점수까지 붙은 장소 */
data class FrequentPlace(
    val key: String,
    val name: String?,
    val category: String?,
    val placeId: String?,
    val role: PlaceRole,
    val roleSource: RoleSource,
    val latitude: Double,
    val longitude: Double,
    val visitsLast30Days: Int,
    val score: Double,
) {
    /** 최근 30일에 3번 이상 간 곳 */
    val isFrequent: Boolean get() = visitsLast30Days >= FREQUENT_MIN_VISITS

    /** 이름이 없는 곳(집·회사)은 종류 이름으로 보여 준다 */
    val displayName: String get() = name ?: role.label

    companion object {
        const val FREQUENT_MIN_VISITS = 3
    }
}

/** "유치원" 같은 말을 실제 장소로 바꾼 결과 */
sealed interface PersonalMatch {
    data class Found(val place: FrequentPlace) : PersonalMatch

    /** 점수가 비슷한 후보가 여럿이라 사용자에게 물어봐야 함. 점수 높은 순 최대 3개 */
    data class Ambiguous(val candidates: List<FrequentPlace>) : PersonalMatch

    /** 방문 기록에 없음 */
    data object NotFound : PersonalMatch
}

/**
 * 방문 기록을 쌓고, 장소별로 묶어 종류를 분류하고 점수를 매긴다. 모든 계산은 기기 안에서만 한다.
 * 분류 우선순위: 사용자 지정 > 카카오 카테고리 > 시간 패턴 > 기타
 */
class PlaceRepository(
    private val visitDao: VisitDao,
    private val labelDao: PlaceLabelDao,
    private val clock: Clock,
) : VisitRecorder {

    override suspend fun recordArrival(arrival: Arrival): Long = visitDao.insert(
        VisitEntity(
            placeId = arrival.placeId,
            name = arrival.name,
            category = arrival.category,
            latitude = arrival.latitude,
            longitude = arrival.longitude,
            arrivedAtMillis = clock.millis(),
            departedAtMillis = null,
            source = arrival.source,
        )
    )

    override suspend fun recordDeparture(visitId: Long) {
        visitDao.setDeparted(visitId, clock.millis())
    }

    /** 최근 90일 방문으로 만든 장소 목록. 점수 높은 순 */
    suspend fun places(): List<FrequentPlace> {
        val now = ZonedDateTime.now(clock)
        val visits = visitDao.since(now.minusDays(KEEP_DAYS).toInstant().toEpochMilli())
        val clusters = PlaceClusterer.cluster(visits)
        val labels = labelDao.all().associate { it.placeKey to it.role }

        val categoryRoles = clusters.associate { it.key to CategoryClassifier.classify(it.category) }
        val patternRoles = PatternClassifier.classify(
            clusters.filter { labels[it.key] == null && categoryRoles[it.key] == null },
            clock.zone,
        )
        val since30Days = now.minusDays(30).toInstant().toEpochMilli()

        return clusters.map { cluster ->
            val (role, source) = labels[cluster.key]?.let { it to RoleSource.USER }
                ?: categoryRoles[cluster.key]?.let { it to RoleSource.CATEGORY }
                ?: patternRoles[cluster.key]?.let { it to RoleSource.PATTERN }
                ?: (PlaceRole.OTHER to RoleSource.NONE)
            FrequentPlace(
                key = cluster.key,
                name = cluster.name,
                category = cluster.category,
                placeId = cluster.placeId,
                role = role,
                roleSource = source,
                latitude = cluster.latitude,
                longitude = cluster.longitude,
                visitsLast30Days = cluster.visits.count { it.arrivedAtMillis >= since30Days },
                score = FrequencyScorer.score(cluster.visits, now),
            )
        }.sortedByDescending { it.score }
    }

    suspend fun frequentPlaces(): List<FrequentPlace> = places().filter { it.isFrequent }

    /**
     * /parse의 kind: personal(예: "유치원")을 실제 장소로 바꾼다.
     * 종류가 맞는 곳 중 점수 1등을 고르되, 2등 점수가 1등의 70% 이상이면 사용자에게 묻는다.
     * 종류 이름이 아니면 장소 이름에 포함되는지로 찾는다(예: "해님유치원").
     */
    suspend fun resolvePersonal(query: String): PersonalMatch {
        val all = places()
        val roles = PlaceRole.fromQuery(query)
        val normalized = query.replace(" ", "")
        val candidates = if (roles.isNotEmpty()) {
            all.filter { it.role in roles }
        } else {
            all.filter { it.name?.replace(" ", "")?.contains(normalized) == true }
        }
        val top = candidates.firstOrNull() ?: return PersonalMatch.NotFound
        val second = candidates.getOrNull(1)
        return if (second == null || second.score < top.score * AMBIGUOUS_RATIO) {
            PersonalMatch.Found(top)
        } else {
            PersonalMatch.Ambiguous(candidates.take(3))
        }
    }

    /**
     * 출발할 때 제안할 다음 목적지: 같은 평일/주말에 지금부터 90분 안에 도착하던 기록이 3번 이상인 곳 중
     * 평소 가장 먼저 도착하던 곳. 예) 평일 8:30 출발 → 8:40 유치원(9:05 회사보다 먼저).
     * 지금 있는 곳(반경 150m)은 뺀다.
     */
    suspend fun suggestDestination(currentLatitude: Double?, currentLongitude: Double?): FrequentPlace? {
        val now = ZonedDateTime.now(clock)
        val visits = visitDao.since(now.minusDays(KEEP_DAYS).toInstant().toEpochMilli())
        val waitsByKey = PlaceClusterer.cluster(visits).associate { cluster ->
            cluster.key to cluster.visits.mapNotNull { v ->
                FrequencyScorer.minutesUntilUsualArrival(Instant.ofEpochMilli(v.arrivedAtMillis).atZone(now.zone), now)
            }
        }
        return frequentPlaces()
            .filter { place ->
                currentLatitude == null || currentLongitude == null ||
                    PlaceClusterer.distanceMeters(place.latitude, place.longitude, currentLatitude, currentLongitude) >
                    PlaceClusterer.SAME_PLACE_METERS
            }
            .filter { waitsByKey[it.key].orEmpty().size >= FrequentPlace.FREQUENT_MIN_VISITS }
            .minByOrNull { waitsByKey[it.key].orEmpty().average() }
    }

    /** 사용자가 장소 종류를 직접 고친다. null이면 자동 분류로 되돌린다 */
    suspend fun setRole(placeKey: String, role: PlaceRole?) {
        if (role == null) labelDao.delete(placeKey) else labelDao.upsert(PlaceLabelEntity(placeKey, role))
    }

    /** 90일보다 오래된 방문 기록을 지운다 */
    suspend fun pruneOld() {
        visitDao.deleteBefore(ZonedDateTime.now(clock).minusDays(KEEP_DAYS).toInstant().toEpochMilli())
    }

    /** 시연용: 기존 기록을 지우고 가상 방문 기록을 넣는다 */
    suspend fun replaceWithDemoVisits() {
        visitDao.deleteAll()
        labelDao.deleteAll()
        visitDao.insertAll(DemoVisits.generate(ZonedDateTime.now(clock)))
    }

    suspend fun clearAll() {
        visitDao.deleteAll()
        labelDao.deleteAll()
    }

    private companion object {
        const val KEEP_DAYS = 90L
        const val AMBIGUOUS_RATIO = 0.7
    }
}
