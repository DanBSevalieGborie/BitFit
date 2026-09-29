package com.codepath.bitfit

import android.os.Looper
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.codepath.bitfit.data.AppDatabase
import com.codepath.bitfit.data.EntryEntity
import com.codepath.bitfit.ui.EntryActivity
import com.codepath.bitfit.ui.MainActivity
import com.codepath.bitfit.util.Formatters
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** End-to-end checks of the required features on the JVM. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppFlowTest {

    private val app get() = ApplicationProvider.getApplicationContext<BitFitApplication>()

    @Before
    fun setUp() = AppDatabase.resetForTests(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = AppDatabase.resetForTests(ApplicationProvider.getApplicationContext())

    /** Lets Room's background threads finish and then runs pending main-thread work. */
    private fun settle(times: Int = 15) = repeat(times) {
        Thread.sleep(40)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Keeps settling until [condition] is true (or ~5s pass). */
    private fun settleUntil(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            settle(1)
            Thread.sleep(10)
        }
    }

    @Test
    fun createEntryScreen_savesToDatabase() {
        ActivityScenario.launch(EntryActivity::class.java).use { scenario ->
            settle()
            scenario.onActivity { activity ->
                activity.findViewById<TextView>(R.id.food_input).text = "Greek salad"
                activity.findViewById<EditText>(R.id.calories_input).setText("450")
                activity.findViewById<TextView>(R.id.save_button).performClick()
            }
            settle()
        }
        settleUntil { runBlocking { app.db.entryDao().getAll() }.isNotEmpty() }
        val saved = runBlocking { app.db.entryDao().getAll() }
        assertEquals(1, saved.size)
        assertEquals("Greek salad", saved[0].foodName)
        assertEquals(450, saved[0].calories)
        assertEquals(Formatters.today(), saved[0].epochDay)
    }

    @Test
    fun createEntryScreen_requiresFoodAndCalories() {
        ActivityScenario.launch(EntryActivity::class.java).use { scenario ->
            settle()
            scenario.onActivity { it.findViewById<TextView>(R.id.save_button).performClick() }
            settle()
        }
        assertEquals(0, runBlocking { app.db.entryDao().getAll() }.size)
    }

    @Test
    fun mainScreen_listsEntriesFromDatabase_andShowsDashboard() {
        runBlocking {
            app.db.entryDao().insertAll(
                listOf(
                    EntryEntity(epochDay = Formatters.today(), foodName = "Oatmeal", calories = 350, waterCups = 3, mood = 4),
                    EntryEntity(epochDay = Formatters.today() - 1, foodName = "Pasta", calories = 800, sleepHours = 7.5f),
                )
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var count = 0
            settleUntil {
                scenario.onActivity { count = it.findViewById<RecyclerView>(R.id.entries_recycler).adapter?.itemCount ?: 0 }
                count == 2
            }
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.entries_recycler)
                assertEquals(2, list.adapter?.itemCount)
                activity.findViewById<NavigationBarView>(R.id.main_nav).selectedItemId = R.id.nav_dashboard
            }
            var avgText = ""
            settleUntil {
                scenario.onActivity { avgText = it.findViewById<TextView>(R.id.stat_avg_calories)?.text?.toString().orEmpty() }
                avgText == "575"
            }
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById(R.id.trend_chart))
                val avg = activity.findViewById<TextView>(R.id.stat_avg_calories).text.toString()
                assertEquals("575", avg)
                activity.findViewById<NavigationBarView>(R.id.main_nav).selectedItemId = R.id.nav_settings
            }
            settle()
        }
    }
}
