package com.codepath.bitfit

import android.app.Application
import com.codepath.bitfit.data.AppDatabase
import com.codepath.bitfit.reminder.ReminderScheduler
import com.codepath.bitfit.util.Prefs

class BitFitApplication : Application() {

    val db: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        Prefs.applyTheme(Prefs(this).theme)
        ReminderScheduler.createChannel(this)
    }
}
