package com.example.alcoholtracker

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI as MathPI
import kotlin.math.max

class MainActivity : Activity() {

    private lateinit var drinkButton: Button
    private lateinit var calendarButton: Button
    private lateinit var profileButton: Button
    private lateinit var resetButton: Button
    private lateinit var rouletteButton: Button
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

    private var firstDrinkTime: Long = 0

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
        rouletteButton = findViewById(R.id.rouletteButton)
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
            showEnhancedCalendar()
        }

        profileButton.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        resetButton.setOnClickListener {
            showResetConfirmationDialog()
        }

        rouletteButton.setOnClickListener {
            showRouletteDialog()
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

        if (historyList.isEmpty()) {
            firstDrinkTime = currentTime
        }

        totalAmount += amount

        val strength = drinkStrength[drink] ?: 0.0
        val alcoholGrams = (amount * strength * 0.789) / 100
        totalAlcoholGrams += alcoholGrams

        val record = DrinkRecord(drink, amount, currentTime, alcoholGrams)
        historyList.add(0, record)

        updateDailyRecords(record)

        updateUI()
        saveData()

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

        totalDrinksText.text = "${totalAmount} мл"

        val dailyAmount = todayRecord?.totalAmount ?: 0
        val dailyAlcohol = todayRecord?.totalAlcohol ?: 0.0
        dailyTotalText.text = "Выпито сегодня: ${dailyAmount} мл"
        dailyAlcoholText.text = "Чистого алкоголя: ${"%.1f".format(dailyAlcohol)} г"

        val hoursSinceFirstDrink = if (firstDrinkTime > 0) {
            (System.currentTimeMillis() - firstDrinkTime) / (1000.0 * 60 * 60)
        } else {
            0.0
        }

        val bac = calculateBAC(totalAlcoholGrams, hoursSinceFirstDrink)

        alcoholLevelText.text = "🔬 Алкоголь в крови: ${"%.3f".format(bac)}‰"

        val status = calculateStatus(bac)
        statusText.text = status.first
        statusText.setTextColor(status.second)

        updateTimeToSober(bac)
        updateWeekHistory()
    }

    private fun calculateBAC(totalAlcoholGrams: Double, hoursSinceFirstDrink: Double): Double {
        if (userProfile.weight <= 0) return 0.0

        val r = if (userProfile.isMale) 0.7 else 0.6
        val weight = userProfile.weight

        val maxBAC = totalAlcoholGrams / (weight * r)
        val metabolismEffect = userProfile.metabolism * hoursSinceFirstDrink

        return max(0.0, maxBAC - metabolismEffect)
    }

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

    private fun updateTimeToSober(currentBAC: Double) {
        if (currentBAC > 0.05) {
            val hoursToSober = currentBAC / userProfile.metabolism
            val hours = hoursToSober.toInt()
            val minutes = ((hoursToSober - hours) * 60).toInt()

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

    private fun resetCurrentDay() {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todayRecord = dailyRecords[currentDate]

        if (todayRecord != null) {
            totalAmount -= todayRecord.totalAmount
            totalAlcoholGrams -= todayRecord.totalAlcohol

            dailyRecords.remove(currentDate)

            historyList.removeAll { record ->
                val recordDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date(record.timestamp))
                recordDate == currentDate
            }

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

        val historyJson = sharedPreferences.getString("history", "")
        if (!historyJson.isNullOrEmpty()) {
            val type = object : TypeToken<MutableList<DrinkRecord>>() {}.type
            val loadedList = gson.fromJson<MutableList<DrinkRecord>>(historyJson, type)
            historyList.clear()
            historyList.addAll(loadedList)
        }

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
            UserProfile()
        }

        if (userProfile.metabolism <= 0.01) {
            userProfile = UserProfile(
                weight = userProfile.weight,
                isMale = userProfile.isMale,
                metabolism = if (userProfile.isMale) 0.15 else 0.13,
                participants = userProfile.participants
            )
        }

        if (userProfile.participants < 2 || userProfile.participants > 8) {
            userProfile = userProfile.copy(participants = 4)
        }
    }

    private fun startTimer() {
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    updateUI()
                    saveData()
                }
            }
        }, 0, 60000)
    }

    private fun showEnhancedCalendar() {
        try {
            val dialogView = layoutInflater.inflate(R.layout.calendar_dialog, null)
            val calendarTitle = dialogView.findViewById<TextView>(R.id.calendarTitle)
            val calendarGrid = dialogView.findViewById<GridView>(R.id.calendarGrid)
            val prevMonthButton = dialogView.findViewById<ImageButton>(R.id.prevMonthButton)
            val nextMonthButton = dialogView.findViewById<ImageButton>(R.id.nextMonthButton)

            val monthNames = arrayOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")

            var currentCalendar = Calendar.getInstance()

            data class CalendarDay(val day: Int, val amountText: String, val date: String = "")

            fun updateCalendar() {
                try {
                    val year = currentCalendar.get(Calendar.YEAR)
                    val month = currentCalendar.get(Calendar.MONTH)
                    calendarTitle.text = "${monthNames[month]} $year"

                    val days = mutableListOf<CalendarDay>()

                    val tempCalendar = Calendar.getInstance()
                    tempCalendar.set(year, month, 1)

                    val daysInMonth = tempCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val firstDayOfWeek = (tempCalendar.get(Calendar.DAY_OF_WEEK) + 5) % 7

                    for (i in 0 until firstDayOfWeek) {
                        days.add(CalendarDay(0, ""))
                    }

                    for (day in 1..daysInMonth) {
                        val dateStr = String.format("%d-%02d-%02d", year, month + 1, day)
                        val record = dailyRecords[dateStr]
                        val amountText = if (record?.totalAmount ?: 0 > 0) "${record!!.totalAmount}мл" else ""
                        days.add(CalendarDay(day, amountText, dateStr))
                    }

                    val adapter = object : BaseAdapter() {
                        override fun getCount(): Int = days.size
                        override fun getItem(position: Int): Any = days[position]
                        override fun getItemId(position: Int): Long = position.toLong()

                        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                            val view = convertView ?: layoutInflater.inflate(R.layout.calendar_day_item, parent, false)
                            val dayItem = days[position]

                            val dayNumber = view.findViewById<TextView>(R.id.dayNumber)
                            val dayAmount = view.findViewById<TextView>(R.id.dayAmount)

                            if (dayItem.day > 0) {
                                dayNumber.text = dayItem.day.toString()
                                dayAmount.text = dayItem.amountText

                                val today = Calendar.getInstance()
                                val isToday = (dayItem.day == today.get(Calendar.DAY_OF_MONTH) &&
                                        month == today.get(Calendar.MONTH) &&
                                        year == today.get(Calendar.YEAR))

                                if (isToday) {
                                    dayNumber.setBackgroundColor(Color.parseColor("#1976D2"))
                                    dayNumber.setTextColor(Color.WHITE)
                                    dayAmount.setTextColor(Color.parseColor("#1976D2"))
                                } else if (dayItem.amountText.isNotEmpty()) {
                                    dayNumber.setBackgroundColor(Color.parseColor("#FF5252"))
                                    dayNumber.setTextColor(Color.WHITE)
                                    dayAmount.setTextColor(Color.parseColor("#FF5252"))
                                } else {
                                    dayNumber.setBackgroundColor(Color.parseColor("#F5F5F5"))
                                    dayNumber.setTextColor(Color.parseColor("#333333"))
                                    dayAmount.setTextColor(Color.parseColor("#666666"))
                                }

                                view.setOnClickListener {
                                    showDayDetails(dayItem.date)
                                }
                            } else {
                                dayNumber.text = ""
                                dayAmount.text = ""
                                dayNumber.setBackgroundColor(Color.TRANSPARENT)
                            }

                            return view
                        }
                    }

                    calendarGrid.adapter = adapter

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            prevMonthButton.setOnClickListener {
                currentCalendar.add(Calendar.MONTH, -1)
                updateCalendar()
            }

            nextMonthButton.setOnClickListener {
                currentCalendar.add(Calendar.MONTH, 1)
                updateCalendar()
            }

            var startX = 0f
            val swipeOverlay = dialogView.findViewById<View>(R.id.swipeOverlay)
            swipeOverlay.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.x
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val endX = event.x
                        val diffX = endX - startX

                        if (Math.abs(diffX) > 100) {
                            if (diffX > 0) {
                                currentCalendar.add(Calendar.MONTH, -1)
                                updateCalendar()
                            } else {
                                currentCalendar.add(Calendar.MONTH, 1)
                                updateCalendar()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Закрыть") { d, _ -> d.dismiss() }
                .create()

            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.show()
            updateCalendar()

        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки календаря", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDayDetails(date: String) {
        val record = dailyRecords[date]
        val message = if (record != null && record.totalAmount > 0) {
            val drinksText = record.drinks.joinToString("\n") { drink ->
                "• ${drink.drink}: ${drink.amount} мл (${"%.1f".format(drink.alcoholGrams)} г алкоголя)"
            }
            "📅 ${formatDateForDisplay(date)}\n\n" +
                    "Всего выпито: ${record.totalAmount} мл\n" +
                    "Алкоголя: ${"%.1f".format(record.totalAlcohol)} г\n\n" +
                    "Напитки:\n$drinksText"
        } else {
            "📅 ${formatDateForDisplay(date)}\n\nТрезвый день! 🎉\n\nВы не употребляли алкоголь в этот день."
        }

        AlertDialog.Builder(this)
            .setTitle("Детали дня")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun formatDateForDisplay(dateStr: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
            outputFormat.format(inputFormat.parse(dateStr)!!)
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun showRouletteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.roulette_dialog, null)
        val rouletteWheel = dialogView.findViewById<ImageView>(R.id.roulette_background)
        val resultText = dialogView.findViewById<TextView>(R.id.resultText)
        val spinButton = dialogView.findViewById<Button>(R.id.spinButton)

        val participants = userProfile.participants
        val colors = arrayOf("#FF5252", "#FF9800", "#4CAF50", "#2196F3", "#9C27B0", "#795548", "#607D8B", "#E91E63")
        val playerNames = arrayOf("Игрок 1", "Игрок 2", "Игрок 3", "Игрок 4", "Игрок 5", "Игрок 6", "Игрок 7", "Игрок 8")

        updateRouletteWheel(rouletteWheel, participants, colors)

        var isSpinning = false

        spinButton.setOnClickListener {
            if (!isSpinning) {
                isSpinning = true
                resultText.text = "Крутим..."
                spinButton.isEnabled = false

                val spinAnimator = ObjectAnimator.ofFloat(rouletteWheel, "rotation", 0f, 3600f)
                spinAnimator.duration = 5000
                spinAnimator.interpolator = DecelerateInterpolator()

                spinAnimator.addListener(object : android.animation.Animator.AnimatorListener {
                    override fun onAnimationStart(animation: android.animation.Animator) {}
                    override fun onAnimationCancel(animation: android.animation.Animator) {}
                    override fun onAnimationRepeat(animation: android.animation.Animator) {}

                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        val winner = (0 until participants).random()
                        val winnerName = playerNames[winner]
                        val winnerColor = colors[winner]

                        resultText.text = "Пьёт: $winnerName! 🍻"
                        resultText.setTextColor(Color.parseColor(winnerColor))

                        val toast = Toast.makeText(this@MainActivity,
                            "🎉 $winnerName пьёт! 🍻\nЗа твое здоровье!", Toast.LENGTH_LONG)
                        toast.setGravity(Gravity.CENTER, 0, 0)
                        toast.show()

                        isSpinning = false
                        spinButton.isEnabled = true
                    }
                })

                spinAnimator.start()
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Закрыть") { d, _ -> d.dismiss() }
            .create()

        dialog.show()
    }

    private fun updateRouletteWheel(wheel: ImageView, participants: Int, colors: Array<String>) {
        val size = 500
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = true
        }

        val centerX = size / 2f
        val centerY = size / 2f
        val radius = size / 2f - 20

        val sectorAngle = 360f / participants
        for (i in 0 until participants) {
            paint.color = Color.parseColor(colors[i])

            val startAngle = i * sectorAngle
            val rectF = RectF(20f, 20f, size - 20f, size - 20f)

            canvas.drawArc(rectF, startAngle, sectorAngle, true, paint)

            paint.color = Color.WHITE
            paint.textSize = 30f
            paint.textAlign = Paint.Align.CENTER

            val textAngle = startAngle + sectorAngle / 2
            val textRadius = radius * 0.6f
            val x = centerX + textRadius * cos(Math.toRadians(textAngle.toDouble())).toFloat()
            val y = centerY + textRadius * sin(Math.toRadians(textAngle.toDouble())).toFloat()

            canvas.drawText("${i + 1}", x, y, paint)
        }

        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, radius * 0.2f, paint)

        wheel.setImageBitmap(bitmap)
    }
}