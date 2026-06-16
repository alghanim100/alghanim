package com.alghanim.qibla.data

data class CityPreset(
    val nameEn: String,
    val nameAr: String,
    val latitude: Double,
    val longitude: Double
)

val PRESET_CITIES = listOf(
    CityPreset("Mecca, Saudi Arabia", "مكة المكرمة، السعودية", 21.422487, 39.826206),
    CityPreset("Medina, Saudi Arabia", "المدينة المنورة، السعودية", 24.4672, 39.6112),
    CityPreset("Riyadh, Saudi Arabia", "الرياض، السعودية", 24.7136, 46.6753),
    CityPreset("Kuwait City, Kuwait", "الكويت، الكويت", 29.3759, 47.9774),
    CityPreset("Cairo, Egypt", "القاهرة، مصر", 30.0444, 31.2357),
    CityPreset("Dubai, UAE", "دبي، الإمارات", 25.2048, 55.2708),
    CityPreset("Amman, Jordan", "عمان، الأردن", 31.9454, 35.9284),
    CityPreset("Jerusalem, Palestine", "القدس، فلسطين", 31.7683, 35.2137),
    CityPreset("Baghdad, Iraq", "بغداد، العراق", 33.3152, 44.3661),
    CityPreset("Doha, Qatar", "الدوحة، قطر", 25.2854, 51.5310),
    CityPreset("Manama, Bahrain", "المنامة، البحرين", 26.2285, 50.5860),
    CityPreset("Muscat, Oman", "مسقط، عمان", 23.5859, 58.4059),
    CityPreset("Beirut, Lebanon", "بيروت، لبنان", 33.8938, 35.5018),
    CityPreset("Casablanca, Morocco", "الدار البيضاء، المغرب", 33.5731, -7.5898),
    CityPreset("Istanbul, Turkey", "إسطنبول، تركيا", 41.0082, 28.9784),
    CityPreset("London, United Kingdom", "لندن، المملكة المتحدة", 51.5074, -0.1278),
    CityPreset("New York, USA", "نيويورك، أمريكا", 40.7128, -74.0060),
    CityPreset("Jakarta, Indonesia", "جاكرتا، إندونيسيا", -6.2088, 106.8456)
)
