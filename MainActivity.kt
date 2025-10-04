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
import kotlin.math.max
import android.view.Gravity

class MainActivity : Activity() {

    private lateinit var drinkButton: Button
    private lateinit var calendarButton: Button
    private lateinit var profileButton: Button
    private lateinit var resetButton: Button
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

    private var firstDrinkTime: Long = 0 // Время первого употребления

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

        // ДОБАВЬТЕ ЭТУ ПРОВЕРКУ
        println("DEBUG ПРИ ЗАПУСКЕ: Вес=${userProfile.weight}кг, Метаболизм=${userProfile.metabolism}‰/час")

        initViews()
        setupClickListeners()
        updateUI()
        startTimer()
    }

    private fun initViews() {
        drinkButton = findViewById(R.id.drinkButton)
        calendarButton = findViewById(R.id.calendarButton)
        profileButton = findViewById(R.id.profileButton)
        resetButton = findViewById(R.id.resetButton)
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
            showEnhancedCalendar() // вместо showCalendar()
        }

        profileButton.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        resetButton.setOnClickListener {
            showResetConfirmationDialog()
        }
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Сброс данных")
            .setMessage("Сбросить данные за сегодняшний день?")
            .setPositiveButton("Да") { _, _ ->
                resetCurrentDay()
            }
            .setNegativeButton("Отмена", null)
            .show()
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
                if (amount <= 0) {
                    Toast.makeText(this, "Введите положительное число", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

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
        val currentTime = System.currentTimeMillis()

        // Запоминаем время первого употребления
        if (historyList.isEmpty()) {
            firstDrinkTime = currentTime
        }

        totalAmount += amount

        // Расчет алкоголя в граммах
        val strength = drinkStrength[drink] ?: 0.0
        val alcoholGrams = (amount * strength * 0.789) / 100
        totalAlcoholGrams += alcoholGrams

        // Добавляем в историю
        val record = DrinkRecord(drink, amount, currentTime, alcoholGrams)
        historyList.add(0, record)

        // Обновляем дневную статистику
        updateDailyRecords(record)

        updateUI()
        saveData()

        // Показываем тост с расчетом уровня алкоголя
        val hoursSinceFirstDrink = if (firstDrinkTime > 0) {
            (currentTime - firstDrinkTime) / (1000.0 * 60 * 60)
        } else {
            0.0
        }

        val bac = calculateBAC(totalAlcoholGrams, hoursSinceFirstDrink)
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

        // Расчет уровня алкоголя в крови с учетом времени
        val hoursSinceFirstDrink = if (firstDrinkTime > 0) {
            (System.currentTimeMillis() - firstDrinkTime) / (1000.0 * 60 * 60)
        } else {
            0.0
        }

        val bac = calculateBAC(totalAlcoholGrams, hoursSinceFirstDrink)
        alcoholLevelText.text = "🔬 Алкоголь в крови: ${"%.3f".format(bac)}‰"

        // Статус опьянения
        val status = calculateStatus(bac)
        statusText.text = status.first
        statusText.setTextColor(status.second)

        // Время до трезвости
        updateTimeToSober(bac)

        // История за неделю
        updateWeekHistory()
    }

    // 🧮 ПРАВИЛЬНЫЙ расчет уровня алкоголя в крови
    private fun calculateBAC(totalAlcoholGrams: Double, hoursSinceFirstDrink: Double): Double {
        // ПРАВИЛЬНАЯ формула Widmark:
        // BAC = (алкоголь в граммах / (вес * коэффициент распределения)) - (метаболизм * часы)
        // Коэффициент распределения: 0.7 для мужчин, 0.6 для женщин

        val r = if (userProfile.isMale) 0.7 else 0.6
        val weight = userProfile.weight

        // BAC в промилле (‰) = (граммы алкоголя / (вес * r))
        val bac = totalAlcoholGrams / (weight * r)

        // Учитываем метаболизм (вывод алкоголя)
        val metabolismEffect = userProfile.metabolism * hoursSinceFirstDrink

        return max(0.0, bac - metabolismEffect)
    }

    // 🎯 Расчет статуса опьянения на основе промилле
    private fun calculateStatus(bac: Double): Pair<String, Int> {
        return when {
            bac < 0.3 -> Pair("🎯 Абсолютно трезв", Color.parseColor("#4ADE80"))
            bac < 0.5 -> Pair("😊 Легкое расслабление", Color.parseColor("#22C55E"))
            bac < 1.0 -> Pair("😊 Легкое опьянение", Color.parseColor("#EAB308"))
            bac < 1.5 -> Pair("😮 Среднее опьянение", Color.parseColor("#F97316"))
            bac < 2.0 -> Pair("🚨 Сильное опьянение", Color.parseColor("#EF4444"))
            bac < 3.0 -> Pair("💀 Опасное опьянение", Color.parseColor("#DC2626"))
            else -> Pair("☠️ Критическое состояние", Color.parseColor("#991B1B"))
        }
    }

    // ⏰ ПРАВИЛЬНЫЙ расчет времени до трезвости
    // ⏰ ПРАВИЛЬНЫЙ расчет времени до трезвости
    // ⏰ ПРАВИЛЬНЫЙ расчет времени до трезвости
    private fun updateTimeToSober(currentBAC: Double) {
        if (currentBAC > 0.1) {
            // Время до трезвости = текущий BAC / скорость метаболизма
            val hoursToSober = currentBAC / userProfile.metabolism

            val hours = hoursToSober.toInt()
            val minutes = ((hoursToSober - hours) * 60).toInt()

            // ОТЛАДКА ДЛЯ ПРОВЕРКИ
            println("DEBUG: BAC=$currentBAC, Метаболизм=${userProfile.metabolism}, Время=$hoursToSober часов")
            println("DEBUG: Вес=${userProfile.weight}кг, Пол=${if (userProfile.isMale) "муж" else "жен"}")

            // Предупреждение об опасности
            if (currentBAC > 3.0) {
                timeToSoberText.text = "ОПАСНОСТЬ! ${hours}ч ${minutes}м\nСрочно к врачу! 🚑"
                timeToSoberText.setTextColor(Color.parseColor("#DC2626"))
            } else if (currentBAC > 2.0) {
                timeToSoberText.text = "Опасно! ${hours}ч ${minutes}м\nВызовите скорую! ⚠️"
                timeToSoberText.setTextColor(Color.parseColor("#EF4444"))
            } else if (currentBAC > 1.0) {
                timeToSoberText.text = "Внимание! ${hours}ч ${minutes}м\nНе садитесь за руль! 🚗"
                timeToSoberText.setTextColor(Color.parseColor("#F97316"))
            } else {
                timeToSoberText.text = "До трезвости: ${hours}ч ${minutes}м"
                timeToSoberText.setTextColor(Color.parseColor("#F97316"))
            }
        } else {
            timeToSoberText.text = "🎯 Трезв"
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
            calendar.add(Calendar.DAY_OF_YEAR, i)
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

    // 🔄 ПРАВИЛЬНЫЙ сброс только текущего дня
    private fun resetCurrentDay() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Находим записи за сегодня
        val todayRecord = dailyRecords[currentDate]

        if (todayRecord != null) {
            // Вычитаем из общих счетчиков
            totalAmount -= todayRecord.totalAmount
            totalAlcoholGrams -= todayRecord.totalAlcohol

            // Удаляем записи за сегодня
            dailyRecords.remove(currentDate)

            // Удаляем из истории записи за сегодня
            historyList.removeAll { record ->
                val recordDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date(record.timestamp))
                recordDate == currentDate
            }

            // Сбрасываем время первого употребления если история пустая
            if (historyList.isEmpty()) {
                firstDrinkTime = 0
            }

            updateUI()
            saveData()
            Toast.makeText(this, "Данные за сегодня сброшены! 🎯", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Нет данных за сегодня для сброса", Toast.LENGTH_SHORT).show()
        }
    }

    // 💾 Методы для сохранения/загрузки данных
    private fun saveData() {
        val editor = sharedPreferences.edit()
        editor.putInt("totalAmount", totalAmount)
        editor.putFloat("totalAlcoholGrams", totalAlcoholGrams.toFloat())
        editor.putLong("firstDrinkTime", firstDrinkTime)

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
        firstDrinkTime = sharedPreferences.getLong("firstDrinkTime", 0)

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

    // ⏱️ УМНЫЙ таймер для обновления времени
    private fun startTimer() {
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    // Обновляем UI с правильными расчетами
                    updateUI()
                    saveData()
                }
            }
        }, 0, 60000) // Обновлять каждую минуту
    }

    // 📅 Улучшенный календарь с цветами и деталями
    private fun showEnhancedCalendar() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)

        val dialogView = layoutInflater.inflate(R.layout.calendar_dialog, null)
        val calendarTitle = dialogView.findViewById<TextView>(R.id.calendarTitle)
        val calendarGrid = dialogView.findViewById<GridLayout>(R.id.calendarGrid)
        val prevMonthButton = dialogView.findViewById<Button>(R.id.prevMonthButton)
        val nextMonthButton = dialogView.findViewById<Button>(R.id.nextMonthButton)

        val monthNames = arrayOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")

        var displayedYear = currentYear
        var displayedMonth = currentMonth

        fun updateCalendar() {
            calendarGrid.removeAllViews()
            calendarTitle.text = "${monthNames[displayedMonth]} $displayedYear"

            // Заголовки дней недели
            val daysOfWeek = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
            for (day in daysOfWeek) {
                val dayView = TextView(this).apply {
                    text = day
                    textSize = 12f
                    setTextColor(Color.BLACK)
                    gravity = Gravity.CENTER
                    setPadding(4, 8, 4, 8)
                }
                calendarGrid.addView(dayView)
            }

            // Дни месяца
            val tempCalendar = Calendar.getInstance().apply {
                set(displayedYear, displayedMonth, 1)
            }

            val daysInMonth = tempCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val firstDayOfWeek = tempCalendar.get(Calendar.DAY_OF_WEEK)

            // Пустые ячейки до первого дня
            val offset = (firstDayOfWeek + 5) % 7 // Коррекция для понедельника
            for (i in 0 until offset) {
                calendarGrid.addView(TextView(this))
            }

            // Дни месяца
            for (day in 1..daysInMonth) {
                val dateStr = String.format("%d-%02d-%02d", displayedYear, displayedMonth + 1, day)
                val amount = dailyRecords[dateStr]?.totalAmount ?: 0

                val dayView = TextView(this).apply {
                    text = day.toString()
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setPadding(8, 12, 8, 12)

                    // Цвета: зеленый - трезво, красный - пили
                    if (amount > 0) {
                        setBackgroundColor(Color.parseColor("#FFE0E0"))
                        setTextColor(Color.parseColor("#D32F2F"))
                        setOnClickListener {
                            showDayDetails(dateStr, amount)
                        }
                    } else {
                        setBackgroundColor(Color.parseColor("#E8F5E8"))
                        setTextColor(Color.parseColor("#388E3C"))
                    }
                }
                calendarGrid.addView(dayView)
            }
        }

        prevMonthButton.setOnClickListener {
            displayedMonth--
            if (displayedMonth < 0) {
                displayedMonth = 11
                displayedYear--
            }
            updateCalendar()
        }

        nextMonthButton.setOnClickListener {
            displayedMonth++
            if (displayedMonth > 11) {
                displayedMonth = 0
                displayedYear++
            }
            updateCalendar()
        }

        updateCalendar()

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Закрыть", null)
            .show()
    }

    // 📊 Показать детали за день
    private fun showDayDetails(date: String, amount: Int) {
        val record = dailyRecords[date]
        val message = if (record != null) {
            val drinksText = record.drinks.joinToString("\n") { drink ->
                "• ${drink.drink}: ${drink.amount} мл (${"%.1f".format(drink.alcoholGrams)} г алкоголя)"
            }
            "📅 $date\n\n" +
                    "Всего выпито: ${record.totalAmount} мл\n" +
                    "Алкоголя: ${"%.1f".format(record.totalAlcohol)} г\n\n" +
                    "Напитки:\n$drinksText"
        } else {
            "📅 $date\n\nТрезвый день! 🎉"
        }

        AlertDialog.Builder(this)
            .setTitle("Детали дня")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}