package com.kkaeum.alarm
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class ManagementDataTest {
    private val context: Context=ApplicationProvider.getApplicationContext()
    @Test fun backupValidationAndMergePreserveExistingData() = runBlocking {
        val original=AppPreferences.read(context)
        val db=Room.inMemoryDatabaseBuilder(context,AlarmDatabase::class.java).build()
        try {
            val json=JSONObject(AlarmBackup.export(context))
            json.put("alarms",org.json.JSONArray().put(JSONObject(AlarmCodec.encode(Alarm(name="백업 검사",groupName="출근",soundUri="file:///private/test")))))
            json.put("rules",org.json.JSONArray().put(JSONObject().put("date","2027-01-01").put("force",true)))
            json.put("pauses",org.json.JSONArray().put(JSONObject().put("start","2027-02-01").put("end","2027-02-03")))
            val data=AlarmBackup.parse(json.toString())
            assertFalse(data.alarms.single().enabled); assertEquals("",data.alarms.single().soundUri)
            db.alarms().put(Alarm(id=42,name="기존",enabled=true))
            db.alarms().putRule(DateRule("2027-01-01",false))
            AlarmBackup.restore(context,data,db)
            assertEquals("기존",db.alarms().find(42)!!.name)
            assertTrue(db.alarms().find(42)!!.enabled)
            assertEquals(2,db.alarms().all().size)
            assertFalse(db.alarms().rules().single().force)
            assertEquals("출근",db.alarms().all().first { it.id!=42 }.groupName)
            json.put("version",99)
            assertTrue(runCatching { AlarmBackup.parse(json.toString()) }.isFailure)
            json.put("version",1)
            json.getJSONArray("alarms").getJSONObject(0).put("hour",25)
            assertTrue(runCatching { AlarmBackup.parse(json.toString()) }.isFailure)
        } finally { db.close(); AppPreferences.appearance(context,original) }
    }
    @Test fun versionTwoMigrationPreservesAlarmAndDates() = runBlocking {
        val filename="management-migration-test.db"
        context.deleteDatabase(filename)
        context.openOrCreateDatabase(filename,Context.MODE_PRIVATE,null).use { db ->
            db.execSQL("CREATE TABLE alarms (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, hour INTEGER NOT NULL, minute INTEGER NOT NULL, days INTEGER NOT NULL, enabled INTEGER NOT NULL, digits INTEGER NOT NULL, count INTEGER NOT NULL, quietSeconds INTEGER NOT NULL, vibrate INTEGER NOT NULL)")
            db.execSQL("INSERT INTO alarms VALUES (42,7,30,31,1,2,3,15,1)")
            // Build the published v2 schema through its original migration.
        }
        // Use SupportSQLiteOpenHelper so the exact shipping migration defines v2.
        val helper=androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name(filename)
                .callback(object: androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { AlarmDatabase.MIGRATION.migrate(db) }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion:Int,newVersion:Int) { AlarmDatabase.MIGRATION.migrate(db) }
                }).build())
        helper.writableDatabase.execSQL("INSERT INTO date_rules VALUES ('2027-01-01',0)"); helper.close()
        val db=Room.databaseBuilder(context,AlarmDatabase::class.java,filename).addMigrations(AlarmDatabase.MIGRATION_2_3).build()
        try { assertEquals(30,db.alarms().find(42)!!.minute); assertEquals("",db.alarms().find(42)!!.groupName)
            assertEquals(false,db.alarms().rules().single().force); assertTrue(db.alarms().pauses().isEmpty())
        } finally { db.close(); context.deleteDatabase(filename) }
    }
}
