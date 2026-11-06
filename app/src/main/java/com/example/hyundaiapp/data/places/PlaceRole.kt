package com.example.hyundaiapp.data.places

/** 자주 가는 장소의 종류 */
enum class PlaceRole(val label: String) {
    HOME("집"),
    WORK("회사"),
    KINDERGARTEN("유치원·어린이집"),
    SCHOOL("학교"),
    ACADEMY("학원"),
    MART("대형마트"),
    SUPERMARKET("슈퍼마켓"),
    CONVENIENCE("편의점"),
    LAUNDRY("세탁소"),
    HOSPITAL("병원"),
    PHARMACY("약국"),
    CAFE("카페"),
    OTHER("기타");

    companion object {
        /**
         * 발화(/parse의 kind: personal query)를 장소 종류로 바꾼다. "마트"는 대형마트와 슈퍼마켓 둘 다.
         * 모르는 말이면 빈 집합(이름으로 찾는다).
         */
        fun fromQuery(query: String): Set<PlaceRole> = QUERY_ALIASES[query.replace(" ", "")].orEmpty()

        private val QUERY_ALIASES: Map<String, Set<PlaceRole>> = mapOf(
            "집" to setOf(HOME),
            "우리집" to setOf(HOME),
            "회사" to setOf(WORK),
            "직장" to setOf(WORK),
            "사무실" to setOf(WORK),
            "유치원" to setOf(KINDERGARTEN),
            "어린이집" to setOf(KINDERGARTEN),
            "학교" to setOf(SCHOOL),
            "학원" to setOf(ACADEMY),
            "마트" to setOf(MART, SUPERMARKET),
            "대형마트" to setOf(MART),
            "장보기" to setOf(MART, SUPERMARKET),
            "슈퍼" to setOf(SUPERMARKET),
            "슈퍼마켓" to setOf(SUPERMARKET),
            "편의점" to setOf(CONVENIENCE),
            "세탁소" to setOf(LAUNDRY),
            "병원" to setOf(HOSPITAL),
            "약국" to setOf(PHARMACY),
            "카페" to setOf(CAFE),
        )
    }
}
