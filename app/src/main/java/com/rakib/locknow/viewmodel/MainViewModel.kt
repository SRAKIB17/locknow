package com.rakib.locknow.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rakib.locknow.data.PrefsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

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
    
    // Additional settings
    private val _isEmergencyCallEnabled = MutableStateFlow(prefs.isEmergencyCallEnabled)
    val isEmergencyCallEnabled = _isEmergencyCallEnabled.asStateFlow()

    init {
        startTimerUpdate()
    }

    private fun startTimerUpdate() {
        viewModelScope.launch {
            while (true) {
                val endTime = prefs.lockEndTime
                val now = System.currentTimeMillis()
                if (endTime > now && prefs.isLocked) {
                    _remainingTime.value = endTime - now
                    _isLocked.value = true
                } else {
                    _remainingTime.value = 0L
                    _isLocked.value = false
                    if (prefs.isLocked) {
                        prefs.isLocked = false
                    }
                }
                delay(1000)
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
        _emergencyPhone.value = phone
    }
    
    fun deleteEmergencyContact() {
        prefs.emergencyName = ""
        prefs.emergencyRelation = ""
        prefs.emergencyPhone = ""
        prefs.emergencyAltPhone = ""
        prefs.emergencyNotes = ""
        
        _emergencyName.value = ""
        _emergencyPhone.value = ""
    }

    fun toggleEmergencyCall(enabled: Boolean) {
        prefs.isEmergencyCallEnabled = enabled
        _isEmergencyCallEnabled.value = enabled
    }

    // Other settings toggles...
}
