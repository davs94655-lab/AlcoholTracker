package com.example.alcoholtracker

import java.text.SimpleDateFormat
import java.util.*

// Класс для хранения дневной статистики
data class DailyRecord(
    val date: String, // Формат: "2024-01-15"
    val totalAmount: Int,
    val totalAlcohol: Double,
    val drinks: List<DrinkRecord>
)

// Класс профиля пользователя
data class UserProfile(
    val weight: Double = 70.0,
    val isMale: Boolean = true,
    val metabolism: Double = 0.015, // автоматически рассчитывается
    val createdDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
)

// Класс для записи о напитке
data class DrinkRecord(
    val drink: String,
    val amount: Int,
    val timestamp: Long,
    val alcoholGrams: Double
)