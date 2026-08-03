package com.mexadev.aura

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.core.preferences.PreferencesManager
import com.mexadev.aura.core.session.SessionManager

class AuraApplication : Application(), DefaultLifecycleObserver {
    
    private var currentActivity: Activity? = null

    override fun onCreate() {
        super<Application>.onCreate()
        
        ApiClient.initialize(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                currentActivity = activity
            }
            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity == activity) {
                    currentActivity = null
                }
            }
        })
    }

    override fun onStart(owner: LifecycleOwner) {
        val prefs = PreferencesManager(this)
        val session = SessionManager(this)
        
        // Si la app viene al primer plano y "Bloquear al salir" está activo, y hay sesión
        // No bloquear si estamos en el SplashActivity (el splash ya valida la sesión y pide biometría)
        if (prefs.lockOnExit && session.getAccessTokenSync() != null && currentActivity !is SplashActivity) {
            val intent = Intent(this, LockActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
    }
}
