package com.rakib.locknow.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rakib.locknow.R
import com.rakib.locknow.data.PrefsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PrefsManager(application)

    private val _isLocked = MutableStateFlow(prefs.isLocked)
    val isLocked = _isLocked.asStateFlow()

    private val _remainingTime = MutableStateFlow(0L)
    val remainingTime = _remainingTime.asStateFlow()

    private val _emergencyName = MutableStateFlow(prefs.emergencyName ?: "")
    val emergencyName = _emergencyName.asStateFlow()

    private val _emergencyPhone = MutableStateFlow(prefs.emergencyPhone ?: "")
    val emergencyPhone = _emergencyPhone.asStateFlow()

    private val _emergencyRelation = MutableStateFlow(prefs.emergencyRelation ?: "")
    val emergencyRelation = _emergencyRelation.asStateFlow()
    
    private val _isEmergencyCallEnabled = MutableStateFlow(prefs.isEmergencyCallEnabled)
    val isEmergencyCallEnabled = _isEmergencyCallEnabled.asStateFlow()

    private val _isQuotesEnabled = MutableStateFlow(prefs.isQuotesEnabled)
    val isQuotesEnabled = _isQuotesEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.themeMode)
    val themeMode = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(prefs.language)
    val language = _language.asStateFlow()

    private val _currentQuote = MutableStateFlow("")
    val currentQuote = _currentQuote.asStateFlow()

    private var timerJob: Job? = null
    private var quoteJob: Job? = null

    init {
        startTimerUpdate()
        startQuoteRotation()
    }

    private fun startTimerUpdate() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val endTime = prefs.lockEndTime
                val now = System.currentTimeMillis()
                if (endTime > now && prefs.isLocked) {
                    _remainingTime.value = endTime - now
                    _isLocked.value = true
                } else {
                    _remainingTime.value = 0L
                    if (_isLocked.value) {
                        _isLocked.value = false
                        prefs.isLocked = false
                    }
                }
                delay(1.seconds)
            }
        }
    }

    private fun startQuoteRotation() {
        quoteJob?.cancel()
        if (!prefs.isQuotesEnabled) {
            _currentQuote.value = ""
            return
        }
        quoteJob = viewModelScope.launch {
            while (true) {
                val quotes = getApplication<Application>().resources.getStringArray(R.array.motivational_quotes)
                if (quotes.isNotEmpty()) {
                    _currentQuote.value = quotes.random()
                }
                delay(3.seconds)
            }
        }
    }

    fun saveEmergencyContact(name: String, relation: String, phone: String, altPhone: String, notes: String) {
        prefs.emergencyName = name
        prefs.emergencyRelation = relation
        prefs.emergencyPhone = phone
        prefs.emergencyAltPhone = altPhone
        prefs.emergencyNotes = notes
        
        _emergencyName.value = name
        _emergencyRelation.value = relation
        _emergencyPhone.value = phone
    }
    
    fun deleteEmergencyContact() {
        prefs.emergencyName = ""
        prefs.emergencyRelation = ""
        prefs.emergencyPhone = ""
        prefs.emergencyAltPhone = ""
        prefs.emergencyNotes = ""
        
        _emergencyName.value = ""
        _emergencyRelation.value = ""
        _emergencyPhone.value = ""
    }

    fun toggleEmergencyCall(enabled: Boolean) {
        prefs.isEmergencyCallEnabled = enabled
        _isEmergencyCallEnabled.value = enabled
    }

    fun toggleQuotes(enabled: Boolean) {
        prefs.isQuotesEnabled = enabled
        _isQuotesEnabled.value = enabled
        startQuoteRotation()
    }

    fun setThemeMode(mode: Int) {
        prefs.themeMode = mode
        _themeMode.value = mode
    }

    fun setLanguage(lang: String) {
        prefs.language = lang
        _language.value = lang
        startQuoteRotation()
    }
}
