package com.example.ui

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.telephony.SmsManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Contact
import com.example.data.ContactRepository
import com.example.util.LocationHelper
import com.example.util.ShakeDetector
import com.example.util.SirenPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SmsLog(
    val id: String = UUID.randomUUID().toString(),
    val contactName: String,
    val phone: String,
    val message: String,
    val status: String, // "SENT", "QUEUED", "FAILED"
    val timestamp: String
)

class SafetyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = ContactRepository(database.contactDao())
    private val locationHelper = LocationHelper(application)
    private val sirenPlayer = SirenPlayer(application)

    // Contacts
    val contacts: StateFlow<List<Contact>> = repository.allContacts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // UI States
    private val _isSosTriggered = MutableStateFlow(false)
    val isSosTriggered: StateFlow<Boolean> = _isSosTriggered.asStateFlow()

    private val _isSirenPlaying = MutableStateFlow(false)
    val isSirenPlaying: StateFlow<Boolean> = _isSirenPlaying.asStateFlow()

    private val _isShakeDetectionEnabled = MutableStateFlow(true)
    val isShakeDetectionEnabled: StateFlow<Boolean> = _isShakeDetectionEnabled.asStateFlow()

    private val _shakeSensitivitySet = MutableStateFlow(2.5f)
    val shakeSensitivitySet: StateFlow<Float> = _shakeSensitivitySet.asStateFlow()

    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

    private val _lastLocation = MutableStateFlow<Location?>(null)
    val lastLocation: StateFlow<Location?> = _lastLocation.asStateFlow()

    private val _smsLogs = MutableStateFlow<List<SmsLog>>(emptyList())
    val smsLogs: StateFlow<List<SmsLog>> = _smsLogs.asStateFlow()

    // Real-Time Location Sharing & Privacy States
    private val _isLiveTrackingActive = MutableStateFlow(false)
    val isLiveTrackingActive: StateFlow<Boolean> = _isLiveTrackingActive.asStateFlow()

    private val _selectedTrackingContacts = MutableStateFlow<Set<Int>>(emptySet())
    val selectedTrackingContacts: StateFlow<Set<Int>> = _selectedTrackingContacts.asStateFlow()

    private val _trackingIntervalSeconds = MutableStateFlow(15) // Default 15s updates for interactive feel
    val trackingIntervalSeconds: StateFlow<Int> = _trackingIntervalSeconds.asStateFlow()

    private val _autoExpireMinutes = MutableStateFlow(60) // Default 1 hour auto-expiration
    val autoExpireMinutes: StateFlow<Int> = _autoExpireMinutes.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _liveTrackingLogs = MutableStateFlow<List<String>>(emptyList())
    val liveTrackingLogs: StateFlow<List<String>> = _liveTrackingLogs.asStateFlow()

    private var liveTrackingJob: Job? = null

    // Sensors
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var shakeDetector: ShakeDetector? = null

    init {
        setupShakeDetector()
        registerShakeListener()
    }

    private fun setupShakeDetector() {
        shakeDetector = ShakeDetector(threshold = _shakeSensitivitySet.value) {
            Log.d("SafetyViewModel", "Shake event received inside SafetyViewModel coordinate scope.")
            triggerSOS()
        }
    }

    fun registerShakeListener() {
        if (_isShakeDetectionEnabled.value) {
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            accelerometer?.let {
                sensorManager.registerListener(shakeDetector, it, SensorManager.SENSOR_DELAY_UI)
                Log.d("SafetyViewModel", "Sensor listener registered successfully.")
            }
        }
    }

    fun unregisterShakeListener() {
        shakeDetector?.let {
            sensorManager.unregisterListener(it)
            Log.d("SafetyViewModel", "Sensor listener unregistered successfully.")
        }
    }

    fun toggleShakeDetection() {
        val nextState = !_isShakeDetectionEnabled.value
        _isShakeDetectionEnabled.value = nextState
        if (nextState) {
            registerShakeListener()
        } else {
            unregisterShakeListener()
        }
    }

    fun setSensitivity(value: Float) {
        _shakeSensitivitySet.value = value
        shakeDetector?.setSensitivity(value)
    }

    // Contacts management
    fun addContact(name: String, phone: String, isPrimary: Boolean) {
        viewModelScope.launch {
            repository.insertContact(Contact(name = name, phone = phone, isPrimary = isPrimary))
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            repository.deleteContact(contact)
        }
    }

    fun toggleSiren() {
        if (_isSirenPlaying.value) {
            sirenPlayer.stop()
            _isSirenPlaying.value = false
        } else {
            sirenPlayer.start()
            _isSirenPlaying.value = true
        }
    }

    fun triggerSOS() {
        if (_isSosTriggered.value) return // SOS already active

        _isSosTriggered.value = true
        
        // Siren sound acts as a separate, independent feature now.

        viewModelScope.launch {
            _isLocating.value = true
            val location = locationHelper.getCurrentLocation()
            _lastLocation.value = location
            _isLocating.value = false

            // Build SMS payload
            val mapsLink = if (location != null) {
                "https://maps.google.com/?q=${location.latitude},${location.longitude}"
            } else {
                null
            }

            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val rawMessage = if (mapsLink != null) {
                "EMERGENCY! I need help. My current live location is: $mapsLink (SafeHer SOS)"
            } else {
                "EMERGENCY! I need help. (Unable to secure GPS coordinates immediately) - SafeHer SOS"
            }

            val contactList = contacts.value
            if (contactList.isEmpty()) {
                // Log simulated diagnostic message for empty setup
                _smsLogs.value = listOf(
                    SmsLog(
                        contactName = "No Contacts Saved!",
                        phone = "---",
                        message = "SOS active, but no emergency contacts have been added. Please add contacts to dispatch SMS alerts.",
                        status = "FAILED",
                        timestamp = timestamp
                    )
                ) + _smsLogs.value
                return@launch
            }

            contactList.forEach { contact ->
                try {
                    val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        getApplication<Application>().getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }

                    if (smsManager == null) {
                        throw Exception("SmsManager is not available on this device.")
                    }

                    smsManager.sendTextMessage(contact.phone, null, rawMessage, null, null)
                    
                    _smsLogs.value = listOf(
                        SmsLog(
                            contactName = contact.name,
                            phone = contact.phone,
                            message = rawMessage,
                            status = "SENT",
                            timestamp = timestamp
                        )
                    ) + _smsLogs.value

                    Log.d("SafetyViewModel", "SMS alert successfully dispatched to ${contact.name}.")
                } catch (e: Exception) {
                    _smsLogs.value = listOf(
                        SmsLog(
                            contactName = contact.name,
                            phone = contact.phone,
                            message = rawMessage,
                            status = "FAILED",
                            timestamp = timestamp
                        )
                    ) + _smsLogs.value

                    Log.e("SafetyViewModel", "Failed to dispatch SMS to ${contact.name}: ${e.message}")
                }
            }
        }
    }

    fun resetSOS() {
        _isSosTriggered.value = false
        if (_isSirenPlaying.value) {
            sirenPlayer.stop()
            _isSirenPlaying.value = false
        }
    }

    fun clearSmsLogs() {
        _smsLogs.value = emptyList()
    }

    fun startLiveTracking() {
        if (_isLiveTrackingActive.value) return
        _isLiveTrackingActive.value = true
        
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _liveTrackingLogs.value = listOf("[$timestamp] 🟢 Live GPS tracking active.")
        
        // Initialize countdown seconds
        _remainingSeconds.value = _autoExpireMinutes.value * 60
        
        liveTrackingJob = viewModelScope.launch {
            var elapsedSeconds = 0
            
            // Dispatch first update immediately
            broadcastLocationUpdate()
            
            while (_isLiveTrackingActive.value) {
                delay(1000)
                
                if (_remainingSeconds.value > 0) {
                    _remainingSeconds.value--
                    if (_remainingSeconds.value <= 0) {
                        stopLiveTracking("Session expired automatically (Privacy Timer).")
                        break
                    }
                }
                
                elapsedSeconds++
                if (elapsedSeconds >= _trackingIntervalSeconds.value) {
                    broadcastLocationUpdate()
                    elapsedSeconds = 0
                }
            }
        }
    }

    fun stopLiveTracking(reason: String = "Stopped by user.") {
        if (!_isLiveTrackingActive.value) return
        _isLiveTrackingActive.value = false
        liveTrackingJob?.cancel()
        liveTrackingJob = null
        
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _liveTrackingLogs.value = listOf("[$timestamp] 🔴 Session ended ($reason)") + _liveTrackingLogs.value
    }

    private suspend fun broadcastLocationUpdate() {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val location = locationHelper.getCurrentLocation()
        
        val selectedIds = _selectedTrackingContacts.value
        val allContacts = contacts.value
        
        // Filter targeted contacts. If set is empty, fall back to all primary contacts
        val targetContacts = if (selectedIds.isEmpty()) {
            allContacts.filter { it.isPrimary }
        } else {
            allContacts.filter { selectedIds.contains(it.id) }
        }
        
        if (targetContacts.isEmpty()) {
            _liveTrackingLogs.value = listOf("[$timestamp] ⚠️ Broadcast held: No contacts selected (Add emergency contacts in Contacts tab).") + _liveTrackingLogs.value
            return
        }

        val lat = location?.latitude ?: 37.421999
        val lng = location?.longitude ?: -122.084058
        
        val mapsLink = "https://maps.google.com/?q=$lat,$lng"
        
        targetContacts.forEach { contact ->
            val smsText = "[SafeHer Track] Live Tracker: My coordinates: $mapsLink"
            try {
                val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    getApplication<Application>().getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                if (smsManager == null) {
                    throw Exception("SmsManager is not available on this device.")
                }

                smsManager.sendTextMessage(contact.phone, null, smsText, null, null)
                
                // Add log inside the network SMS dashboard log too
                _smsLogs.value = listOf(
                    SmsLog(
                        contactName = contact.name,
                        phone = contact.phone,
                        message = smsText,
                        status = "SENT",
                        timestamp = timestamp
                    )
                ) + _smsLogs.value
                
                Log.d("SafetyViewModel", "Dispatched live tracking SMS payload successfully to ${contact.name}")
            } catch (e: Exception) {
                Log.e("SafetyViewModel", "Error sending live tracking SMS: ${e.message}")
            }
        }
        
        val targetNames = targetContacts.joinToString { it.name }
        _liveTrackingLogs.value = listOf("[$timestamp] 📡 Shared GPS coordinate pulse with: $targetNames") + _liveTrackingLogs.value
    }

    fun toggleTrackingContact(contactId: Int) {
        val current = _selectedTrackingContacts.value.toMutableSet()
        if (current.contains(contactId)) {
            current.remove(contactId)
        } else {
            current.add(contactId)
        }
        _selectedTrackingContacts.value = current
    }

    fun setTrackingInterval(seconds: Int) {
        _trackingIntervalSeconds.value = seconds
    }

    fun setAutoExpireMinutes(minutes: Int) {
        _autoExpireMinutes.value = minutes
        if (_isLiveTrackingActive.value) {
            _remainingSeconds.value = minutes * 60
        }
    }

    fun clearLiveTrackingLogs() {
        _liveTrackingLogs.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        unregisterShakeListener()
        sirenPlayer.stop()
        stopLiveTracking("App closed.")
    }
}
