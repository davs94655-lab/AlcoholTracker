package com.example.alcoholtracker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    private lateinit var drinkButton: Button
    private lateinit var calendarButton: Button
    private lateinit var profileButton: Button
    private lateinit var totalDrinksText: TextView
    private lateinit var dailyTotalText: TextView
    private lateinit var dailyAlcoholText: TextView
    private lateinit var statusText: TextView
    private lateinit var alcoholLevelText: TextView
    private lateinit var timeToSoberText: TextView
    private lateinit var weekHistoryText: TextView

    private var totalAmount = 0
    private var totalAlcoholGrams = 0.0
    private val historyList = mutableListOf<DrinkRecord>()
    private val dailyRecords = mutableMapOf<String, DailyRecord>()
    private lateinit var userProfile: UserProfile
    private lateinit var sharedPreferences: SharedPreferences

    // Крепость напитков в %
    private val drinkStrength = mapOf(
        "🍺 Пиво" to 5.0,
        "🍷 Вино" to 12.0,
        "🥃 Водка" to 40.0,
        "🍸 Виски" to 40.0,
        "🍹 Коктейль" to 15.0,
        "🥂 Шампанское" to 11.0
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("AlcoholTracker", MODE_PRIVATE)
        loadData()
        loadUserProfile()

        initViews()
        setupClickListeners()
        updateUI()
        startTimer()
    }

    private fun initViews() {
        drinkButton = findViewById(R.id.drinkButton)
        calendarButton = findViewById(R.id.calendarButton)
        profileButton = findViewById(R.id.profileButton)
        totalDrinksText = findViewById(R.id.totalDrinksText)
        dailyTotalText = findViewById(R.id.dailyTotalText)
        dailyAlcoholText = findViewById(R.id.dailyAlcoholText)
        statusText = findViewById(R.id.statusText)
        alcoholLevelText = findViewById(R.id.alcoholLevelText)
        timeToSoberText = findViewById(R.id.timeToSoberText)
        weekHistoryText = findViewById(R.id.weekHistoryText)
    }

    private fun setupClickListeners() {
        drinkButton.setOnClickListener {
            showDrinkDialog()
        }

        calendarButton.setOnClickListener {
            showCalendar()
        }

        profileButton.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        // Добавьте эту строку:
        findViewById<Button>(R.id.resetButton).setOnClickListener {
            resetAll()
        }
    }

    private fun showDrinkDialog() {
        val dialogView = layoutInflater.inflate(R.layout.drink_dialog, null)
        val drinkSpinner = dialogView.findViewById<Spinner>(R.id.drinkSpinner)
        val amountEditText = dialogView.findViewById<EditText>(R.id.amountEditText)
        val addButton = dialogView.findViewById<Button>(R.id.addButton)

        val drinks = arrayOf("🍺 Пиво", "🍷 Вино", "🥃 Водка", "🍸 Виски", "🍹 Коктейль", "🥂 Шампанское")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, drinks)
        drinkSpinner.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setTitle("🎯 Что будем пить?")
            .setView(dialogView)
            .setCancelable(true)
            .create()

        addButton.setOnClickListener {
            val amountStr = amountEditText.text.toString()
            if (amountStr.isEmpty()) {
                Toast.makeText(this, "Введите количество", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val amount = amountStr.toInt()
                val selectedDrink = drinkSpinner.selectedItem.toString()
                addDrink(selectedDrink, amount)
                dialog.dismiss()
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Введите число", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun addDrink(drink: String, amount: Int) {
        totalAmount += amount

        // Расчет алкоголя в граммах
        val strength = drinkStrength[drink] ?: 0.0
        val alcoholGrams = (amount * strength * 0.789) / 100
        totalAlcoholGrams += alcoholGrams

        // Добавляем в историю
        val record = DrinkRecord(drink, amount, System.currentTimeMillis(), alcoholGrams)
        historyList.add(0, record)

        // Обновляем дневную статистику
        updateDailyRecords(record)

        updateUI()
        saveData()

        // Показываем тост с расчетом уровня алкоголя
        val bac = calculateBAC(totalAlcoholGrams)
        val messages = arrayOf(
            "За здоровье! 🍻 (${"%.2f".format(bac)}‰)",
            "Будь счастлив! 😊 (${"%.2f".format(bac)}‰)",
            "За любовь! ❤️ (${"%.2f".format(bac)}‰)",
            "За дружбу! 👫 (${"%.2f".format(bac)}‰)",
            "За успех! 🚀 (${"%.2f".format(bac)}‰)"
        )
        val randomMessage = messages.random()
        Toast.makeText(this, "$randomMessage\n+${amount} мл $drink", Toast.LENGTH_LONG).show()
    }

    private fun updateDailyRecords(record: DrinkRecord) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val dailyRecord = dailyRecords[currentDate] ?: DailyRecord(currentDate, 0, 0.0, mutableListOf())

        val updatedRecord = dailyRecord.copy(
            totalAmount = dailyRecord.totalAmount + record.amount,
            totalAlcohol = dailyRecord.totalAlcohol + record.alcoholGrams,
            drinks = dailyRecord.drinks + record
        )

        dailyRecords[currentDate] = updatedRecord
    }

    private fun updateUI() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todayRecord = dailyRecords[currentDate]

        // Общая статистика
        totalDrinksText.text = "${totalAmount} мл"

        // Дневная статистика
        val dailyAmount = todayRecord?.totalAmount ?: 0
        val dailyAlcohol = todayRecord?.totalAlcohol ?: 0.0
        dailyTotalText.text = "Выпито сегодня: ${dailyAmount} мл"
        dailyAlcoholText.text = "Чистого алкоголя: ${"%.1f".format(dailyAlcohol)} г"

        // Расчет уровня алкоголя в крови
        val bac = calculateBAC(totalAlcoholGrams)
        alcoholLevelText.text = "🔬 Алкоголь в крови: ${"%.3f".format(bac)}‰"

        // Статус опьянения
        val status = calculateStatus(bac)
        statusText.text = status.first
        statusText.setTextColor(status.second)

        // Время до трезвости
        updateTimeToSober()

        // История за неделю
        updateWeekHistory()
    }

    // 🧮 Расчет уровня алкоголя в крови (промилле)
    private fun calculateBAC(totalAlcoholGrams: Double): Double {
        // Формула Видмарка: BAC = (A / (r * W)) * 100
        // A - масса чистого алкоголя в граммах
        // r - фактор распределения (0.68 для мужчин, 0.55 для женщин)
        // W - масса тела в граммах

        val r = if (userProfile.isMale) 0.68 else 0.55
        val weightGrams = userProfile.weight * 1000

        return (totalAlcoholGrams / (r * weightGrams)) * 100
    }

    // 🎯 Расчет статуса опьянения на основе промилле
    private fun calculateStatus(bac: Double): Pair<String, Int> {
        return when {
            bac < 0.3 -> Pair("🎯 Абсолютно трезв", Color.parseColor("#4ADE80"))
            bac < 0.5 -> Pair("😊 Легкое расслабление", Color.parseColor("#22C55E"))
            bac < 1.0 -> Pair("😎 Легкое опьянение", Color.parseColor("#EAB308"))
            bac < 1.5 -> Pair("😮 Среднее опьянение", Color.parseColor("#F97316"))
            bac < 2.5 -> Pair("🚨 Сильное опьянение", Color.parseColor("#EF4444"))
            bac < 3.0 -> Pair("💀 Опасное опьянение", Color.parseColor("#DC2626"))
            else -> Pair("☠️ Критическое состояние", Color.parseColor("#991B1B"))
        }
    }

    // ⏰ Расчет времени до трезвости
    private fun updateTimeToSober() {
        val bac = calculateBAC(totalAlcoholGrams)
        if (bac > 0.1) {
            // Скорость вывода алкоголя зависит от метаболизма и веса
            val eliminationRate = userProfile.metabolism * userProfile.weight
            val hoursToSober = (bac * 10) / eliminationRate

            val hours = hoursToSober.toInt()
            val minutes = ((hoursToSober - hours) * 60).toInt()

            timeToSoberText.text = "${hours}ч ${minutes}м"
            timeToSoberText.setTextColor(Color.parseColor("#F97316"))
        } else {
            timeToSoberText.text = "Трезв"
            timeToSoberText.setTextColor(Color.parseColor("#4ADE80"))
        }
    }

    // 📈 Обновление истории за неделю
    private fun updateWeekHistory() {
        val calendar = Calendar.getInstance()
        val weekData = mutableListOf<Pair<String, Int>>()

        for (i in 6 downTo 0) {
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            val amount = dailyRecords[date]?.totalAmount ?: 0
            val dayName = SimpleDateFormat("EE", Locale.getDefault()).format(calendar.time)
            weekData.add(Pair(dayName, amount))
            calendar.add(Calendar.DAY_OF_YEAR, i) // возвращаем обратно
        }

        val weekText = weekData.joinToString(" • ") { (day, amount) ->
            "$day: ${amount}мл"
        }

        weekHistoryText.text = "📈 Неделя: $weekText"
    }

    // 📅 Показать календарь
    private fun showCalendar() {
        val calendar = Calendar.getInstance()
        val currentMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)

        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val calendarText = StringBuilder("📅 $currentMonth\n\n")

        for (day in 1..daysInMonth) {
            val dateStr = String.format("%s-%02d",
                SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time), day)
            val amount = dailyRecords[dateStr]?.totalAmount ?: 0
            if (amount > 0) {
                calendarText.append("$day число: $amount мл\n")
            }
        }

        if (calendarText.toString() == "📅 $currentMonth\n\n") {
            calendarText.append("В этом месяце трезво! 🎉")
        }

        AlertDialog.Builder(this)
            .setTitle("Календарь употребления")
            .setMessage(calendarText.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    // 💾 Методы для сохранения/загрузки данных
    private fun saveData() {
        val editor = sharedPreferences.edit()
        editor.putInt("totalAmount", totalAmount)
        editor.putFloat("totalAlcoholGrams", totalAlcoholGrams.toFloat())

        val gson = Gson()
        val historyJson = gson.toJson(historyList)
        val dailyJson = gson.toJson(dailyRecords)

        editor.putString("history", historyJson)
        editor.putString("dailyRecords", dailyJson)
        editor.apply()
    }

    private fun loadData() {
        totalAmount = sharedPreferences.getInt("totalAmount", 0)
        totalAlcoholGrams = sharedPreferences.getFloat("totalAlcoholGrams", 0f).toDouble()

        val gson = Gson()

        // Загрузка истории
        val historyJson = sharedPreferences.getString("history", "")
        if (!historyJson.isNullOrEmpty()) {
            val type = object : TypeToken<MutableList<DrinkRecord>>() {}.type
            val loadedList = gson.fromJson<MutableList<DrinkRecord>>(historyJson, type)
            historyList.clear()
            historyList.addAll(loadedList)
        }

        // Загрузка дневных записей
        val dailyJson = sharedPreferences.getString("dailyRecords", "")
        if (!dailyJson.isNullOrEmpty()) {
            val type = object : TypeToken<MutableMap<String, DailyRecord>>() {}.type
            val loadedDaily = gson.fromJson<MutableMap<String, DailyRecord>>(dailyJson, type)
            dailyRecords.clear()
            dailyRecords.putAll(loadedDaily)
        }
    }

    private fun loadUserProfile() {
        val gson = Gson()
        val profileJson = sharedPreferences.getString("userProfile", "")
        userProfile = if (!profileJson.isNullOrEmpty()) {
            gson.fromJson(profileJson, UserProfile::class.java)
        } else {
            UserProfile() // профиль по умолчанию
        }
    }

    // ⏱️ Таймер для обновления времени
    private fun startTimer() {
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    updateTimeToSober()
                    // Постепенное уменьшение уровня алкоголя
                    if (totalAlcoholGrams > 0) {
                        val elimination = userProfile.metabolism * userProfile.weight * 0.001
                        totalAlcoholGrams = maxOf(0.0, totalAlcoholGrams - elimination)
                        updateUI()
                        saveData()
                    }
                }
            }
        }, 0, 60000) // Обновлять каждую минуту
    }
    private fun resetAll() {
        totalAmount = 0
        totalAlcoholGrams = 0.0
        historyList.clear()
        dailyRecords.clear()
        updateUI()
        saveData()
        Toast.makeText(this, "Всё сброшено! 🎯", Toast.LENGTH_SHORT).show()
    }
}