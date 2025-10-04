package com.example.alcoholtracker

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import com.google.gson.Gson

class ProfileActivity : Activity() {

    private lateinit var weightEditText: EditText
    private lateinit var genderRadioGroup: RadioGroup
    private lateinit var metabolismSeekBar: SeekBar
    private lateinit var metabolismText: TextView
    private lateinit var currentBacText: TextView
    private lateinit var soberTimeText: TextView
    private lateinit var saveProfileButton: Button
    private lateinit var backButton: Button

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var userProfile: UserProfile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.profile_activity)

        sharedPreferences = getSharedPreferences("AlcoholTracker", MODE_PRIVATE)
        loadUserProfile()

        initViews()
        setupClickListeners()
        updateProfileUI()
    }

    private fun initViews() {
        weightEditText = findViewById(R.id.weightEditText)
        genderRadioGroup = findViewById(R.id.genderRadioGroup)
        metabolismSeekBar = findViewById(R.id.metabolismSeekBar)
        metabolismText = findViewById(R.id.metabolismText)
        currentBacText = findViewById(R.id.currentBacText)
        soberTimeText = findViewById(R.id.soberTimeText)
        saveProfileButton = findViewById(R.id.saveProfileButton)
        backButton = findViewById(R.id.backButton)
    }

    private fun setupClickListeners() {
        saveProfileButton.setOnClickListener {
            saveProfile()
        }

        backButton.setOnClickListener {
            finish()
        }

        metabolismSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateMetabolismText(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateProfileUI() {
        weightEditText.setText(userProfile.weight.toString())

        if (userProfile.isMale) {
            genderRadioGroup.check(R.id.maleRadio)
        } else {
            genderRadioGroup.check(R.id.femaleRadio)
        }

        val metabolismProgress = ((userProfile.metabolism - 0.1) / 0.1 * 50).toInt()
        metabolismSeekBar.progress = metabolismProgress
        updateMetabolismText(metabolismProgress)

        updateCurrentStats()
    }

    private fun updateMetabolismText(progress: Int) {
        val metabolism = 0.1 + (progress / 50.0) * 0.1
        val text = when {
            metabolism < 0.12 -> "Медленный"
            metabolism < 0.14 -> "Ниже среднего"
            metabolism < 0.16 -> "Средняя"
            metabolism < 0.18 -> "Выше среднего"
            else -> "Быстрый"
        }
        metabolismText.text = "$text (${"%.3f".format(metabolism)} г/ч/кг)"
    }

    private fun updateCurrentStats() {
        // Загрузка текущих данных алкоголя
        val totalAlcoholGrams = sharedPreferences.getFloat("totalAlcoholGrams", 0f).toDouble()

        // Расчет BAC
        val r = if (userProfile.isMale) 0.68 else 0.55
        val weightGrams = userProfile.weight * 1000
        val bac = (totalAlcoholGrams / (r * weightGrams)) * 100

        currentBacText.text = "🔬 Алкоголь в крови: ${"%.3f".format(bac)}‰"

        // Расчет времени до трезвости
        if (bac > 0.1) {
            val eliminationRate = userProfile.metabolism * userProfile.weight
            val hoursToSober = (bac * 10) / eliminationRate
            val hours = hoursToSober.toInt()
            val minutes = ((hoursToSober - hours) * 60).toInt()
            soberTimeText.text = "⏰ До полного вывода: ${hours}ч ${minutes}м"
        } else {
            soberTimeText.text = "⏰ До полного вывода: 0 ч"
        }
    }

    private fun saveProfile() {
        try {
            val weight = weightEditText.text.toString().toDouble()
            if (weight < 30 || weight > 200) {
                Toast.makeText(this, "Введите реальный вес (30-200 кг)", Toast.LENGTH_SHORT).show()
                return
            }

            val isMale = genderRadioGroup.checkedRadioButtonId == R.id.maleRadio
            val metabolism = 0.1 + (metabolismSeekBar.progress / 50.0) * 0.1

            userProfile = UserProfile(weight, isMale, metabolism)

            val editor = sharedPreferences.edit()
            val gson = Gson()
            val profileJson = gson.toJson(userProfile)
            editor.putString("userProfile", profileJson)
            editor.apply()

            Toast.makeText(this, "✅ Профиль сохранен!", Toast.LENGTH_LONG).show()
            updateCurrentStats()

        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Введите корректный вес", Toast.LENGTH_SHORT).show()
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
}