package com.example

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.widget.BabyCareWidgetProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetProviderTest {

    @Test
    fun `test widget provider update does not throw`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appWidgetManager = AppWidgetManager.getInstance(context)
        assertNotNull(appWidgetManager)

        // Execute updateAppWidget safely
        BabyCareWidgetProvider.updateAppWidget(context, appWidgetManager, 1)
    }
}
