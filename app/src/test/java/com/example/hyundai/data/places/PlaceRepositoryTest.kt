package com.example.hyundai.data.places

import com.example.hyundai.data.local.PlaceLabelDao
import com.example.hyundai.data.local.PlaceLabelEntity
import com.example.hyundai.data.local.VisitDao
import com.example.hyundai.data.local.VisitEntity
import com.example.hyundai.data.local.VisitSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class PlaceRepositoryTest {

    private class MemoryVisitDao : VisitDao {
        val rows = mutableListOf<VisitEntity>()
        private var nextId = 1L

        override suspend fun insert(visit: VisitEntity): Long {
            val id = nextId++
            rows += visit.copy(id = id)
            return id
        }
        override suspend fun insertAll(visits: List<VisitEntity>) { visits.forEach { insert(it) } }
        override suspend fun setDeparted(id: Long, departedAtMillis: Long) {
            val i = rows.indexOfFirst { it.id == id }
            rows[i] = rows[i].copy(departedAtMillis = departedAtMillis)
        }
        override suspend fun since(sinceMillis: Long) = rows.filter { it.arrivedAtMillis >= sinceMillis }.sortedBy { it.arrivedAtMillis }
        override suspend fun deleteBefore(beforeMillis: Long) { rows.removeAll { it.arrivedAtMillis < beforeMillis } }
        override suspend fun deleteAll() = rows.clear()
    }

    private class MemoryLabelDao : PlaceLabelDao {
        val rows = mutableMapOf<String, PlaceLabelEntity>()
        override suspend fun all() = rows.values.toList()
        override suspend fun upsert(label: PlaceLabelEntity) { rows[label.placeKey] = label }
        override suspend fun delete(placeKey: String) { rows.remove(placeKey) }
        override suspend fun deleteAll() = rows.clear()
    }

    private val seoul = ZoneId.of("Asia/Seoul")
    // 2026-11-06(금) 08:30 서울. 평일 아침 등원 시간
    private val now = ZonedDateTime.of(2026, 11, 6, 8, 30, 0, 0, seoul)
    private val clock = Clock.fixed(now.toInstant(), seoul)
    private val visitDao = MemoryVisitDao()
    private val labelDao = MemoryLabelDao()
    private val repository = PlaceRepository(visitDao, labelDao, clock)

    private suspend fun seedDemo() = repository.replaceWithDemoVisits()

    @Test
    fun `가상 기록으로 장소 종류가 모두 분류된다`() = runTest {
        seedDemo()
        val byName = repository.places().associateBy { it.displayName }

        assertEquals(PlaceRole.KINDERGARTEN, byName.getValue("해님유치원").role)
        assertEquals(RoleSource.CATEGORY, byName.getValue("해님유치원").roleSource)
        assertEquals(PlaceRole.MART, byName.getValue("이마트 판교점").role)
        assertEquals(PlaceRole.SUPERMARKET, byName.getValue("GS더프레시 판교점").role)
        assertEquals(PlaceRole.LAUNDRY, byName.getValue("크린토피아 판교점").role)
        // 이름 없는 주차 기록은 시간 패턴으로 집·회사가 된다
        assertEquals(RoleSource.PATTERN, byName.getValue("집").roleSource)
        assertEquals(RoleSource.PATTERN, byName.getValue("회사").roleSource)
    }

    @Test
    fun `유치원은 최근에 자주 간 해님유치원으로 정해진다`() = runTest {
        seedDemo()
        val match = repository.resolvePersonal("유치원")

        assertTrue(match.toString(), match is PersonalMatch.Found)
        assertEquals("해님유치원", (match as PersonalMatch.Found).place.name)
    }

    @Test
    fun `점수가 비슷한 후보가 둘이면 물어본다`() = runTest {
        // 같은 시간대에 비슷한 횟수로 간 유치원 두 곳
        repeat(4) { d ->
            val at = now.minusDays(d + 1L)
            visitDao.insert(demoVisit("a", "가나유치원", at))
            visitDao.insert(demoVisit("b", "다라유치원", at.plusMinutes(5)))
        }
        val match = repository.resolvePersonal("유치원")

        assertTrue(match.toString(), match is PersonalMatch.Ambiguous)
        assertEquals(2, (match as PersonalMatch.Ambiguous).candidates.size)
    }

    @Test
    fun `기록에 없으면 NotFound라 search로 넘긴다`() = runTest {
        seedDemo()
        assertEquals(PersonalMatch.NotFound, repository.resolvePersonal("약국"))
    }

    @Test
    fun `종류 이름이 아니면 장소 이름으로 찾는다`() = runTest {
        seedDemo()
        val match = repository.resolvePersonal("크린토피아")
        assertEquals("크린토피아 판교점", (match as PersonalMatch.Found).place.name)
    }

    @Test
    fun `사용자가 고친 종류가 자동 분류보다 우선한다`() = runTest {
        seedDemo()
        val laundry = repository.places().first { it.name == "크린토피아 판교점" }
        repository.setRole(laundry.key, PlaceRole.CAFE)

        val updated = repository.places().first { it.key == laundry.key }
        assertEquals(PlaceRole.CAFE, updated.role)
        assertEquals(RoleSource.USER, updated.roleSource)

        repository.setRole(laundry.key, null)
        assertEquals(PlaceRole.LAUNDRY, repository.places().first { it.key == laundry.key }.role)
    }

    @Test
    fun `평일 아침 출발이면 유치원을 추천하고 지금 있는 곳은 뺀다`() = runTest {
        seedDemo()
        val suggestion = repository.suggestDestination(null, null)
        assertNotNull(suggestion)
        assertEquals("해님유치원", suggestion!!.name)

        val atKindergarten = repository.suggestDestination(suggestion.latitude, suggestion.longitude)
        assertTrue(atKindergarten?.name != "해님유치원")
    }

    @Test
    fun `도착과 출발을 기록하면 머문 시간이 남는다`() = runTest {
        val id = repository.recordArrival(Arrival("kg", "해님유치원", "교육,학습 > 유아교육 > 유치원", 37.395, 127.115, VisitSource.NAVI))
        repository.recordDeparture(id)

        val saved = visitDao.rows.single()
        assertEquals(clock.millis(), saved.arrivedAtMillis)
        assertEquals(clock.millis(), saved.departedAtMillis)
    }

    @Test
    fun `90일보다 오래된 기록은 지운다`() = runTest {
        visitDao.insert(demoVisit("old", "옛날 마트", now.minusDays(100)))
        visitDao.insert(demoVisit("new", "요즘 마트", now.minusDays(1)))

        repository.pruneOld()

        assertEquals(listOf("new"), visitDao.rows.map { it.placeId })
    }

    private fun demoVisit(id: String, name: String, at: ZonedDateTime) = VisitEntity(
        placeId = id,
        name = name,
        category = "교육,학습 > 유아교육 > 유치원",
        latitude = 37.39,
        longitude = 127.11,
        arrivedAtMillis = at.toInstant().toEpochMilli(),
        departedAtMillis = Instant.ofEpochMilli(at.toInstant().toEpochMilli()).plusSeconds(600).toEpochMilli(),
        source = VisitSource.NAVI,
    )
}
