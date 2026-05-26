package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        return suspendCancellableCoroutine { continuation ->
            try {
                fusedLocationClient.lastLocation.addOnCompleteListener { task ->
                    try {
                        if (task.isSuccessful && task.result != null) {
                            Log.d("LocationHelper", "Using cash-backed last known location coordinates.")
                            if (continuation.isActive) {
                                continuation.resume(task.result)
                            }
                        } else {
                            Log.d("LocationHelper", "Last known location is empty, requesting fresh high precision update.")
                            val cts = CancellationTokenSource()
                            
                            continuation.invokeOnCancellation {
                                try {
                                    cts.cancel()
                                } catch (e: Exception) {
                                    Log.e("LocationHelper", "Error cancelling cts: ${e.message}")
                                }
                            }

                            fusedLocationClient.getCurrentLocation(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                cts.token
                            ).addOnCompleteListener { freshTask ->
                                try {
                                    if (continuation.isActive) {
                                        if (freshTask.isSuccessful) {
                                            continuation.resume(freshTask.result)
                                        } else {
                                            Log.e("LocationHelper", "Could not fetch active GPS coordinates.")
                                            continuation.resume(null)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("LocationHelper", "Error in freshLocation callback: ${e.message}")
                                    if (continuation.isActive) {
                                        continuation.resume(null)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("LocationHelper", "Error in lastLocation callback: ${e.message}")
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("LocationHelper", "Error resolving provider coordinate query: ${e.message}")
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }
}
