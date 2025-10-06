package com.example.alcoholtracker

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.*
import android.text.TextWatcher
import android.text.Editable

class ProfileActivity : Activity() {

    private lateinit var weightEditText: EditText
    private lateinit var genderRadioGroup: RadioGroup
    private lateinit var metabolismValueText: TextView
    private lateinit var participantsEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var backButton: Button

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var userProfile: UserProfile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sharedPreferences = getSharedPreferences("AlcoholTracker", MODE_PRIVATE)
        loadUserProfile()

        initViews()
        setupClickListeners()
        updateUI()
    }

    private fun initViews() {
        weightEditText = findViewById(R.id.weightEditText)
        genderRadioGroup = findViewById(R.id.genderRadioGroup)
        metabolismValueText = findViewById(R.id.metabolismValueText)
        participantsEditText = findViewById(R.id.participantsEditText)
        saveButton = findViewById(R.id.saveButton)
        backButton = findViewById(R.id.backButton)
    }

    private fun setupClickListeners() {
        saveButton.setOnClickListener {
            saveProfile()
        }

        backButton.setOnClickListener {
            finish()
        }

        // Слушатель изменения веса - автоматически пересчитываем метаболизм
        weightEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                updateMetabolism()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Слушатель изменения пола
        genderRadioGroup.setOnCheckedChangeListener { _, _ ->
            updateMetabolism()
        }
    }

    private fun updateUI() {
        // Вес
        weightEditText.setText(userProfile.weight.toString())

        // Пол
        if (userProfile.isMale) {
            genderRadioGroup.check(R.id.maleRadioButton)
        } else {
            genderRadioGroup.check(R.id.femaleRadioButton)
        }

        // Участники
        participantsEditText.setText(userProfile.participants.toString())

        // Метаболизм
        updateMetabolism()
    }

    // ПРАВИЛЬНЫЙ расчет метаболизма - постоянная величина
    private fun updateMetabolism() {
        try {
            val isMale = genderRadioGroup.checkedRadioButtonId == R.id.maleRadioButton

            // Метаболизм - ПОСТОЯННАЯ величина
            val metabolism = if (isMale) 0.15 else 0.13

            metabolismValueText.text = "Скорость метаболизма: ${"%.2f".format(metabolism)} ‰/час"

        } catch (e: Exception) {
            metabolismValueText.text = "Скорость метаболизма: 0.15 ‰/час"
        }
    }

    private fun saveProfile() {
        try {
            val weightText = weightEditText.text.toString()
            if (weightText.isEmpty()) {
                Toast.makeText(this, "Введите вес", Toast.LENGTH_SHORT).show()
                return
            }

            val weight = weightText.toDouble()
            if (weight < 30 || weight > 200) {
                Toast.makeText(this, "Введите вес от 30 до 200 кг", Toast.LENGTH_SHORT).show()
                return
            }

            // Проверка участников
            val participantsText = participantsEditText.text.toString()
            if (participantsText.isEmpty()) {
                Toast.makeText(this, "Введите количество участников", Toast.LENGTH_SHORT).show()
                return
            }

            var participants = participantsText.toInt()
            if (participants < 2 || participants > 8) {
                Toast.makeText(this, "Участников должно быть от 2 до 8", Toast.LENGTH_SHORT).show()
                participants = 4 // значение по умолчанию
                participantsEditText.setText("4")
            }

            val isMale = genderRadioGroup.checkedRadioButtonId == R.id.maleRadioButton

            // Метаболизм - ПОСТОЯННАЯ величина, не зависящая от веса
            val metabolism = if (isMale) 0.15 else 0.13

            userProfile = UserProfile(
                weight = weight,
                isMale = isMale,
                metabolism = metabolism,  // постоянное значение
                participants = participants
            )

            val gson = Gson()
            val profileJson = gson.toJson(userProfile)
            sharedPreferences.edit().putString("userProfile", profileJson).apply()

            Toast.makeText(this, "Профиль сохранен! ✅", Toast.LENGTH_SHORT).show()
            finish()

        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Введите корректные данные", Toast.LENGTH_SHORT).show()
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

        // Проверяем участников
        if (userProfile.participants < 2 || userProfile.participants > 8) {
            userProfile = userProfile.copy(participants = 4)
        }
    }
}