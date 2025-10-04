package com.example.alcoholtracker

import java.text.SimpleDateFormat
import java.util.*

data class DrinkRecord(
    val drink: String,
    val amount: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val alcoholGrams: Double = 0.0
)

data class DailyRecord(
    val date: String,
    val totalAmount: Int,
    val totalAlcohol: Double,
    val drinks: List<DrinkRecord>
)

data class UserProfile(
    val weight: Double = 70.0,
    val isMale: Boolean = true,
    val metabolism: Double = 0.15,
    val createdDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
)