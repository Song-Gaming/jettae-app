package com.example.hyundai

/**
 * 출발 전 판단: 주행 가능 거리와 하이패스 잔액이 경로에 충분한지 보고 들를 곳을 제안한다.
 * 차량/내비/외부 API와 분리된 순수 로직이라 에뮬레이터 없이 단위 테스트한다.
 */
enum class EnergyType { ELECTRIC, FUEL }

data class TripFacts(
    val routeDistanceM: Int,
    val tollFare: Int, // 원, 0이면 유료도로 없음
    val rangeRemainingM: Int?, // null: 차량에서 못 읽음
    val energyType: EnergyType,
    val hipassBalance: Int?, // 원, null: 잔액 모름 (현재 SDK 미제공)
)

sealed interface StopAdvice {
    /** 충전소(ELECTRIC) 또는 주유소(FUEL)에 들러야 함. */
    data class Refuel(val energyType: EnergyType, val shortfallM: Int) : StopAdvice

    /** 하이패스 충전 확인. shortfall null이면 잔액을 몰라 사용자에게 물어봐야 함. */
    data class HipassTopUp(val tollFare: Int, val shortfall: Int?) : StopAdvice
}

// ponytail: fixed 30km reserve; tune per vehicle and season (EV range drops ~20-30% in winter)
const val RANGE_RESERVE_M = 30_000

fun adviseStops(trip: TripFacts, reserveM: Int = RANGE_RESERVE_M): List<StopAdvice> = buildList {
    trip.rangeRemainingM?.let { range ->
        val shortfall = trip.routeDistanceM + reserveM - range
        if (shortfall > 0) add(StopAdvice.Refuel(trip.energyType, shortfall))
    }
    if (trip.tollFare > 0) {
        val balance = trip.hipassBalance
        when {
            balance == null -> add(StopAdvice.HipassTopUp(trip.tollFare, shortfall = null))
            balance < trip.tollFare -> add(StopAdvice.HipassTopUp(trip.tollFare, trip.tollFare - balance))
        }
    }
}
