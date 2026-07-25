package com.example

import android.app.Application
import com.example.widget.BabyCareWidgetViewModel

class BabyCareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize reactive Room database sync for the Widget ViewModel
        BabyCareWidgetViewModel.initAutoSync(this)
    }
}
