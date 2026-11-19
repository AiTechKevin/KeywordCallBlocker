package com.example.keywordcallblocker

import android.content.Context
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class BlockConfig(
    val enabled: Boolean = true,
    val keywords: Set<String> = emptySet(),
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0,
    val activeDays: Set<Int> = setOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    ),
    val skipCallLog: Boolean = false
)

object RuleStorage {
    private const val PREF_NAME = "keyword_blocker_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_KEYWORDS = "keywords"
    private const val KEY_START_HOUR = "start_hour"
    private const val KEY_START_MIN = "start_min"
    private const val KEY_END_HOUR = "end_hour"
    private const val KEY_END_MIN = "end_min"
    private const val KEY_DAYS = "active_days"
    private const val KEY_SKIP_LOG = "skip_call_log"
    private const val KEY_LOGS = "blocked_logs"

    fun load(context: Context): BlockConfig {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val defaultDays = setOf("1", "2", "3", "4", "5", "6", "7")
        val savedDays = prefs.getStringSet(KEY_DAYS, defaultDays) ?: defaultDays

        return BlockConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            keywords = prefs.getStringSet(KEY_KEYWORDS, emptySet())?.toSet() ?: emptySet(),
            startHour = prefs.getInt(KEY_START_HOUR, 22),
            startMinute = prefs.getInt(KEY_START_MIN, 0),
            endHour = prefs.getInt(KEY_END_HOUR, 7),
            endMinute = prefs.getInt(KEY_END_MIN, 0),
            activeDays = savedDays.mapNotNull { it.toIntOrNull() }.toSet(),
            skipCallLog = prefs.getBoolean(KEY_SKIP_LOG, false)
        )
    }

    fun save(context: Context, config: BlockConfig) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putStringSet(KEY_KEYWORDS, config.keywords)
            .putInt(KEY_START_HOUR, config.startHour)
            .putInt(KEY_START_MIN, config.startMinute)
            .putInt(KEY_END_HOUR, config.endHour)
            .putInt(KEY_END_MIN, config.endMinute)
            .putStringSet(KEY_DAYS, config.activeDays.map { it.toString() }.toSet())
            .putBoolean(KEY_SKIP_LOG, config.skipCallLog)
            .apply()
    }

    fun isWithinSchedule(config: BlockConfig, now: Calendar = Calendar.getInstance()): Boolean {
        if (config.activeDays.isEmpty()) return false

        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMin = config.startHour * 60 + config.startMinute
        val endMin = config.endHour * 60 + config.endMinute
        val today = now.get(Calendar.DAY_OF_WEEK)

        return when {
            startMin == endMin -> today in config.activeDays
            startMin < endMin -> today in config.activeDays && nowMin in startMin until endMin
            else -> {
                if (nowMin >= startMin) {
                    today in config.activeDays
                } else if (nowMin < endMin) {
                    val yesterday = if (today == Calendar.SUNDAY) Calendar.SATURDAY else today - 1
                    yesterday in config.activeDays
                } else {
                    false
                }
            }
        }
    }

    fun addBlockedLog(context: Context, contactName: String, number: String, matchedWord: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val existing = JSONArray(prefs.getString(KEY_LOGS, "[]"))
        val time = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
        val entry = "$time — $contactName ($number) [Matched: \"$matchedWord\"]"

        val updated = JSONArray()
        updated.put(entry)
        val maxEntries = minOf(existing.length(), 19)
        for (i in 0 until maxEntries) {
            updated.put(existing.getString(i))
        }
        prefs.edit().putString(KEY_LOGS, updated.toString()).apply()
    }

    fun getBlockedLogs(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_LOGS, "[]"))
        return List(arr.length()) { arr.getString(it) }
    }

    fun clearLogs(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_LOGS).apply()
    }
}
