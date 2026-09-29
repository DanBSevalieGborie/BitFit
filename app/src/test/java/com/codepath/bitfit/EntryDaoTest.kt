package com.codepath.bitfit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.codepath.bitfit.data.AppDatabase
import com.codepath.bitfit.data.EntryDao
import com.codepath.bitfit.data.EntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the real Room DAO against an in-memory SQLite database on the JVM (via Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: EntryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.entryDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insert_thenObserve_returnsNewestFirst() = runTest {
        dao.insert(EntryEntity(epochDay = 10, foodName = "Old", calories = 100))
        dao.insert(EntryEntity(epochDay = 12, foodName = "New", calories = 200, photoPath = "/photos/a.jpg"))

        val entries = dao.observeAll().first()
        assertEquals(listOf("New", "Old"), entries.map { it.foodName })
        assertEquals("/photos/a.jpg", entries.first().photoPath)
    }

    @Test
    fun update_changesStoredValues() = runTest {
        val id = dao.insert(EntryEntity(epochDay = 1, foodName = "Soup", calories = 300))
        val saved = dao.getById(id)!!
        dao.update(saved.copy(calories = 350, mood = 4))
        val updated = dao.getById(id)!!
        assertEquals(350, updated.calories)
        assertEquals(4, updated.mood)
    }

    @Test
    fun delete_andRestore_keepsSameId() = runTest {
        val id = dao.insert(EntryEntity(epochDay = 1, foodName = "Toast", calories = 150))
        val entry = dao.getById(id)!!
        dao.delete(entry)
        assertNull(dao.getById(id))
        dao.insert(entry) // undo
        assertEquals("Toast", dao.getById(id)?.foodName)
    }

    @Test
    fun countForDay_andDeleteAll() = runTest {
        dao.insertAll(
            listOf(
                EntryEntity(epochDay = 5, foodName = "A", calories = 1),
                EntryEntity(epochDay = 5, foodName = "B", calories = 2),
                EntryEntity(epochDay = 6, foodName = "C", calories = 3),
            )
        )
        assertEquals(2, dao.countForDay(5))
        dao.deleteAll()
        assertTrue(dao.getAll().isEmpty())
    }
}
