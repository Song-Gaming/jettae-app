package com.example.hyundai

import org.junit.Assert.assertEquals
import org.junit.Test

class StopAdvisorTest {

    private fun trip(
        routeDistanceM: Int = 100_000,
        tollFare: Int = 0,
        rangeRemainingM: Int? = 300_000,
        energyType: EnergyType = EnergyType.ELECTRIC,
        hipassBalance: Int? = null,
    ) = TripFacts(routeDistanceM, tollFare, rangeRemainingM, energyType, hipassBalance)

    @Test
    fun enoughRange_noToll_noStops() {
        assertEquals(emptyList<StopAdvice>(), adviseStops(trip()))
    }

    @Test
    fun rangeShort_suggestsChargingStop() {
        val advice = adviseStops(trip(routeDistanceM = 100_000, rangeRemainingM = 90_000))
        assertEquals(listOf(StopAdvice.Refuel(EnergyType.ELECTRIC, 40_000)), advice)
    }

    @Test
    fun rangeExactlyCoversRouteAndReserve_noStop() {
        val advice = adviseStops(trip(routeDistanceM = 100_000, rangeRemainingM = 100_000 + RANGE_RESERVE_M))
        assertEquals(emptyList<StopAdvice>(), advice)
    }

    @Test
    fun rangeInsideReserve_suggestsStop() {
        val advice = adviseStops(trip(routeDistanceM = 100_000, rangeRemainingM = 120_000))
        assertEquals(listOf(StopAdvice.Refuel(EnergyType.ELECTRIC, 10_000)), advice)
    }

    @Test
    fun fuelCar_suggestsGasStation() {
        val advice = adviseStops(trip(rangeRemainingM = 50_000, energyType = EnergyType.FUEL))
        assertEquals(listOf(StopAdvice.Refuel(EnergyType.FUEL, 80_000)), advice)
    }

    @Test
    fun rangeUnknown_noRefuelAdvice() {
        assertEquals(emptyList<StopAdvice>(), adviseStops(trip(rangeRemainingM = null)))
    }

    @Test
    fun customReserve_isUsed() {
        val advice = adviseStops(trip(routeDistanceM = 100_000, rangeRemainingM = 105_000), reserveM = 0)
        assertEquals(emptyList<StopAdvice>(), advice)
    }

    @Test
    fun toll_balanceEnough_noHipassAdvice() {
        assertEquals(emptyList<StopAdvice>(), adviseStops(trip(tollFare = 3_200, hipassBalance = 3_200)))
    }

    @Test
    fun toll_balanceShort_suggestsTopUpWithShortfall() {
        val advice = adviseStops(trip(tollFare = 3_200, hipassBalance = 1_000))
        assertEquals(listOf(StopAdvice.HipassTopUp(3_200, 2_200)), advice)
    }

    @Test
    fun toll_balanceUnknown_asksUser() {
        val advice = adviseStops(trip(tollFare = 3_200, hipassBalance = null))
        assertEquals(listOf(StopAdvice.HipassTopUp(3_200, null)), advice)
    }

    @Test
    fun noToll_balanceIgnored() {
        assertEquals(emptyList<StopAdvice>(), adviseStops(trip(tollFare = 0, hipassBalance = 0)))
    }

    @Test
    fun rangeShortAndToll_bothAdvicesInOrder() {
        val advice = adviseStops(trip(rangeRemainingM = 50_000, tollFare = 3_200, hipassBalance = 0))
        assertEquals(
            listOf(StopAdvice.Refuel(EnergyType.ELECTRIC, 80_000), StopAdvice.HipassTopUp(3_200, 3_200)),
            advice
        )
    }
}
