package com.codepath.bitfit.util

import com.codepath.bitfit.data.EntryEntity
import kotlin.random.Random

/** Generates realistic-looking demo entries so the dashboard can be explored right away. */
object SampleData {

    private val meals = listOf(
        "Oatmeal, banana & coffee", "Chicken burrito bowl", "Salmon, rice & broccoli",
        "Turkey sandwich & apple", "Veggie stir fry", "Spaghetti & meatballs",
        "Greek yogurt parfait", "Grilled chicken salad", "Pizza night 🍕", "Tacos & guac",
        "Egg scramble & toast", "Poke bowl", "Burger & sweet potato fries", "Lentil soup",
    )

    fun generate(today: Long, days: Int = 30, seed: Int = 42): List<EntryEntity> {
        val rnd = Random(seed)
        val out = mutableListOf<EntryEntity>()
        for (offset in (days - 1) downTo 0) {
            // Skip a few days so the streak / gaps look realistic
            if (offset > 2 && rnd.nextInt(100) < 12) continue
            val day = today - offset
            val sleep = (55 + rnd.nextInt(40)) / 10f  // 5.5 .. 9.4
            val mood = when {
                sleep >= 8f -> 4 + rnd.nextInt(2)
                sleep >= 6.5f -> 3 + rnd.nextInt(2)
                else -> 1 + rnd.nextInt(3)
            }
            out += EntryEntity(
                epochDay = day,
                foodName = meals[rnd.nextInt(meals.size)],
                calories = 1500 + rnd.nextInt(1100),
                waterCups = 3 + rnd.nextInt(8),
                sleepHours = (sleep * 2).toInt() / 2f,
                mood = mood,
                notes = if (rnd.nextInt(4) == 0) "Sample entry" else null,
                createdAt = System.currentTimeMillis() - offset * 86_400_000L,
            )
        }
        return out
    }
}
