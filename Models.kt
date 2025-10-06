package com.example.alcoholtracker

import java.text.SimpleDateFormat
import java.util.*

// Класс для хранения дневной статистики
data class DailyRecord(
    val date: String,
    val totalAmount: Int,
    val totalAlcohol: Double,
    val drinks: List<DrinkRecord>
)

// Класс профиля пользователя
data class UserProfile(
    var weight: Double = 70.0,
    var isMale: Boolean = true,
    var metabolism: Double = 0.15,
    var participants: Int = 4,
    val createdDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
)

// Класс для записи о напитке
data class DrinkRecord(
    val drink: String,
    val amount: Int,
    val timestamp: Long,
    val alcoholGrams: Double
)