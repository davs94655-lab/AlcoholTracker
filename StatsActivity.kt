package com.example.alcoholtracker

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class StatsActivity : Activity() {

    private lateinit var totalStatsText: TextView
    private lateinit var alcoholStatsText: TextView
    private lateinit var drinksCountText: TextView
    private lateinit var drinksDistributionText: TextView
    private lateinit var backButton: Button

    private lateinit var sharedPreferences: SharedPreferences
    private val historyList = mutableListOf<DrinkRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.stats_activity)

        sharedPreferences = getSharedPreferences("AlcoholTracker", MODE_PRIVATE)
        loadData()

        initViews()
        setupClickListeners()
        updateStats()
    }

    private fun initViews() {
        totalStatsText = findViewById(R.id.totalStatsText)
        alcoholStatsText = findViewById(R.id.alcoholStatsText)
        drinksCountText = findViewById(R.id.drinksCountText)
        drinksDistributionText = findViewById(R.id.drinksDistributionText)
        backButton = findViewById(R.id.backButton)
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }
    }

    private fun loadData() {
        val gson = Gson()
        val json = sharedPreferences.getString("history", "")
        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<MutableList<DrinkRecord>>() {}.type
            val loadedList = gson.fromJson<MutableList<DrinkRecord>>(json, type)
            historyList.clear()
            historyList.addAll(loadedList)
        }
    }

    private fun updateStats() {
        val totalAmount = historyList.sumOf { it.amount }
        val totalAlcohol = historyList.sumOf { it.alcoholGrams }
        val drinksCount = historyList.size

        totalStatsText.text = "Всего выпито: ${totalAmount} мл"
        alcoholStatsText.text = "Чистого алкоголя: ${"%.1f".format(totalAlcohol)} г"
        drinksCountText.text = "Количество напитков: $drinksCount"

        // Распределение по напиткам
        val distribution = historyList
            .groupBy { it.drink }
            .mapValues { (_, records) -> records.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        val distributionText = if (distribution.isEmpty()) {
            "Нет данных"
        } else {
            distribution.joinToString("\n") { (drink, amount) ->
                "• $drink: $amount мл"
            }
        }

        drinksDistributionText.text = distributionText
    }
}