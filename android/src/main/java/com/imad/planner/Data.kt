package com.imad.planner

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

// ---------------- DAO / DB ----------------


@Database(entities = [Task::class, Rule::class, Goal::class, GoalLog::class, Project::class, Meta::class, Event::class], version = 6, exportSchema = false)
abstract class PlannerDb : RoomDatabase() {
    abstract fun store(): Store
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "ALTER TABLE tasks ADD COLUMN category TEXT NOT NULL DEFAULT 'other'",
            "ALTER TABLE tasks ADD COLUMN sport INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE tasks ADD COLUMN phase INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE tasks ADD COLUMN phaseCount INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE tasks ADD COLUMN pct INTEGER NOT NULL DEFAULT 100",
            "ALTER TABLE tasks ADD COLUMN units INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE tasks ADD COLUMN doneUnits INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE tasks ADD COLUMN note TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE rules ADD COLUMN category TEXT NOT NULL DEFAULT 'other'",
            "ALTER TABLE rules ADD COLUMN sport INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE rules ADD COLUMN phases TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE rules ADD COLUMN members TEXT NOT NULL DEFAULT ''",
        ).forEach(db::execSQL)
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN atWork INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN preferMin INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE rules ADD COLUMN atWork INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS `events` (`id` TEXT NOT NULL, `taskId` TEXT, `series` TEXT NOT NULL, `day` TEXT NOT NULL, `at` INTEGER NOT NULL, `type` TEXT NOT NULL, `text` TEXT NOT NULL, `delayMin` INTEGER NOT NULL, PRIMARY KEY(`id`))")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN period TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN details TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN notBefore INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rules ADD COLUMN realHours INTEGER NOT NULL DEFAULT 0")
    }
}

// ---------------- Repository on the phone ----------------

/** The phone's side of [Platform]: Room, app files, alarms, the widget, the Quran app and Instagram's page. */
class AndroidPlatform(private val context: Context, private val db: PlannerDb) : Platform {
    override val filesDir: java.io.File get() = context.filesDir
    override fun asset(name: String) = context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
    override suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }
    override fun pomoAlarm(end: Long) { Alarms.pomoAlarm(context, end) }
    override fun rescheduleAlarms(tasks: List<Task>, settings: Settings) { Alarms.reschedule(context, tasks, settings) }
    override fun updateWidget(todays: List<Task>, settings: Settings) { PlannerWidget.update(context, todays, settings) }
    override fun quranReport(day: String) = QuranAdapter.report(context, day)
    override fun quranWards() = QuranAdapter.wards(context)
    override fun quranSessions(day: String) = QuranAdapter.sessions(context, day)
    override suspend fun igFetchAll(users: List<String>): Map<String, IgAnswer> =
        IgWeb.fetchAll(context, users).mapValues { IgAnswer(it.value.code, it.value.body) }
}

fun Repository.Companion.get(context: Context): Repository =
    instance ?: synchronized(Repository) {
        instance ?: run {
            val app = context.applicationContext
            val db = Room.databaseBuilder(app, PlannerDb::class.java, "planner-v2.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
            // Every change is noted for the sync with the computer (only once sync is set up: PhoneSync).
            Repository(TrackingStore(db.store(), PhoneSync.pending(app)), AndroidPlatform(app, db)).also { instance = it }
        }
    }
