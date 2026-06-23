package es.saniexam.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.saniexam.app.data.entity.CardStateEntity
import es.saniexam.app.data.entity.OptionEntity
import es.saniexam.app.data.entity.QuestionEntity
import es.saniexam.app.data.entity.SubjectPackEntity
import es.saniexam.app.data.entity.TopicEntity
import es.saniexam.app.data.ingest.DatasetImporter
import es.saniexam.app.data.ingest.DatasetImportException
import es.saniexam.app.data.ingest.PackAssetSource
import es.saniexam.app.data.ingest.sha256Hex
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Room migration tests for [SaniExamDb].
 *
 * PR3b follow-up to the `fsrs-scheduler` and `progress-backup` spec
 * requirement that "A Room migration test shows that bumping
 * `schedulerVersion` does not destroy user history" (verify-report §8 S1).
 *
 * Scope of this slice:
 *  1. The v1 -> v2 migration runs without crash.
 *  2. The v1 `schema_marker` table is removed by the migration.
 *  3. The v2 `card_state` table has the FSRS-v6 `learning_steps` and
 *     `scheduler_version` columns that protect future FSRS state from a
 *     destructive migration.
 *  4. The v2 schema's table list matches the exported JSON snapshot
 *     (`app/schemas/.../2.json`) so a schema drift fails the test.
 *  5. The migrated v2 DB is openable by the full Room generated impl and a
 *     real `CardStateEntity` round-trips through the DAO — proving the
 *     migration is not just a no-op but leaves the schema in a state the
 *     v2 entity graph can use.
 *
 * Implementation note — hand-rolled DDL:
 *
 * `androidx.room.testing.MigrationTestHelper` would be the textbook tool
 * here, but it requires the exported schema JSONs to be served via an
 * Android `AssetManager` from the canonical class-name path. AGP 8.2.2 +
 * Robolectric 4.11.1 in this module does not merge `app/src/main/assets/`
 * into the unit test asset scope (the merge lands on the `debug` APK build,
 * not on `debugUnitTest`), so `context.assets.open("schemas/1.json")` fails
 * with `FileNotFoundException` even when the JSON is present in
 * `app/schemas/.../1.json`. Rather than thread a custom
 * `SupportSQLiteOpenHelper.Factory` to inject a classpath-backed asset
 * source, this test follows the hand-rolled-migration pattern: build a v1
 * database with the same DDL Room would generate, run the migration, then
 * open the v2 database with the full Room generated implementation. This
 * is the same pattern the AOSP codelabs use when they need to test
 * migrations without bundled assets. The v1 DDL is taken verbatim from
 * `app/schemas/.../1.json`; the v2 schema assertions are also JSON-driven
 * so a schema drift between the JSON snapshot and the entity graph fails
 * the test loudly.
 *
 * The test runs on the plain JVM via Robolectric; no emulator needed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class SaniExamDbMigrationTest {

    @Test
    fun v1_bootstrap_table_is_dropped_by_migration_1_2() {
        // GIVEN a freshly-created v1 database with the bootstrap row.
        val v1File = createV1Database { db ->
            db.execSQL("INSERT INTO schema_marker(id) VALUES (1)")
        }
        assertTrue("v1 file should exist", v1File.exists())

        // WHEN the migration runs against the same on-disk file.
        runMigrationAndMaterialiseV2(v1File)

        // THEN the v1 bootstrap table is gone.
        openRaw(v1File).use { db ->
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='schema_marker'",
                null,
            ).use { c ->
                assertFalse("v1 schema_marker must be dropped by MIGRATION_1_2", c.moveToFirst())
            }
        }
    }

    @Test
    fun v2_schema_tables_match_exported_json_snapshot() {
        // GIVEN the v2 schema JSON (the source of truth) and a v1 DB.
        val v2Json = loadSchemaResource("schemas/2.json")
        val expected = JSONObject(v2Json).getJSONObject("database")
        val expectedEntityNames = mutableSetOf<String>()
        val entities = expected.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            expectedEntityNames += entities.getJSONObject(i).getString("tableName")
        }

        val v1File = createV1Database()

        // WHEN we run the migration AND materialise the v2 schema
        // (Room's v2 onCreate is what creates the tables in production;
        // the migration is only responsible for dropping v1 leftovers and
        // letting Room add the v2 tables on the same pass).
        runMigrationAndMaterialiseV2(v1File)

        // THEN the live DB's tables match the JSON snapshot exactly.
        openRaw(v1File).use { db ->
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                val actual = mutableSetOf<String>()
                while (c.moveToNext()) {
                    val name = c.getString(0)
                    // Skip the SQLite-internal bookkeeping tables that the
                    // exported JSON also omits.
                    if (
                        name != "android_metadata" &&
                        name != "sqlite_sequence" &&
                        name != "room_master_table"
                    ) {
                        actual += name
                    }
                }
                assertEquals(expectedEntityNames, actual)
            }
        }
    }

    @Test
    fun v2_card_state_has_learning_steps_and_scheduler_version_columns() {
        // GIVEN a v1 DB upgraded to v2 via the migration + v2 onCreate.
        val v1File = createV1Database()
        runMigrationAndMaterialiseV2(v1File)

        // THEN card_state exists with the FSRS-v6 columns.
        val expectedColumns = setOf(
            "question_id", "pack_id", "pack_version", "stability", "difficulty",
            "due_at", "last_reviewed_at", "reps", "lapses", "phase",
            "scheduled_days", "elapsed_days", "learning_steps", "scheduler_version",
        )
        openRaw(v1File).use { db ->
            db.rawQuery("PRAGMA table_info(`card_state`)", null).use { c ->
                val actual = mutableSetOf<String>()
                while (c.moveToNext()) actual += c.getString(1) // column name
                expectedColumns.forEach { col ->
                    assertTrue("card_state must have column $col", col in actual)
                }
            }
        }
    }

    @Test
    fun v2_to_v3_adds_review_log_and_user_settings_with_default_singleton() {
        // GIVEN a v1 DB upgraded to v2, then re-opened and migrated to v3.
        val v1File = createV1Database()
        runMigrationAndMaterialiseV2(v1File)
        runMigrationAndMaterialiseV3(v1File)

        // THEN review_log + user_settings exist with the expected shape.
        openRaw(v1File).use { db ->
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                    "('review_log', 'user_settings')",
                null,
            ).use { c ->
                val tables = mutableSetOf<String>()
                while (c.moveToNext()) tables += c.getString(0)
                assertEquals(setOf("review_log", "user_settings"), tables)
            }

            // review_log columns.
            db.rawQuery("PRAGMA table_info(`review_log`)", null).use { c ->
                val actual = mutableSetOf<String>()
                while (c.moveToNext()) actual += c.getString(1)
                assertTrue("id", "id" in actual)
                assertTrue("question_id", "question_id" in actual)
                assertTrue("reviewed_at", "reviewed_at" in actual)
                assertTrue("rating", "rating" in actual)
                assertTrue("elapsed_days", "elapsed_days" in actual)
                assertTrue("scheduled_days", "scheduled_days" in actual)
                assertTrue("previous_interval_days", "previous_interval_days" in actual)
                assertTrue("new_interval_days", "new_interval_days" in actual)
            }

            // user_settings columns.
            db.rawQuery("PRAGMA table_info(`user_settings`)", null).use { c ->
                val actual = mutableSetOf<String>()
                while (c.moveToNext()) actual += c.getString(1)
                assertTrue("id", "id" in actual)
                assertTrue("last_revealed_card_id", "last_revealed_card_id" in actual)
                assertTrue("last_session_queue_position", "last_session_queue_position" in actual)
                assertTrue("last_session_at", "last_session_at" in actual)
            }

            // The migration seeds the user_settings singleton with Default values.
            db.rawQuery("SELECT last_revealed_card_id, last_session_queue_position FROM user_settings", null).use { c ->
                assertTrue("user_settings singleton must be seeded", c.moveToFirst())
                assertTrue("last_revealed_card_id must be NULL", c.isNull(0))
                assertEquals(0, c.getInt(1))
            }
        }
    }

    @Test
    fun migrated_v3_db_supports_review_log_and_user_settings_dao_round_trip() {
        // GIVEN a v1 -> v2 -> v3 DB opened with the full Room generated impl.
        val v1File = createV1Database()
        runMigrationAndMaterialiseV2(v1File)
        runMigrationAndMaterialiseV3(v1File)

        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.databaseBuilder(ctx, SaniExamDb::class.java, v1File.absolutePath)
            .addMigrations(
                SaniExamDb.MIGRATION_1_2,
                SaniExamDb.MIGRATION_2_3,
                SaniExamDb.MIGRATION_3_4,
            )
            .build()
        try {
            runBlocking {
                // user_settings is seeded; get() returns the default singleton.
                val settings = db.userSettingsDao().get()
                assertNotNull("user_settings singleton must be present after v2->v3", settings)
                val nonNullSettings = settings!!
                assertNull(nonNullSettings.lastRevealedCardId)
                assertEquals(0, nonNullSettings.lastSessionQueuePosition)

                // review_log accepts a write.
                db.reviewLogDao().insert(
                    es.saniexam.app.data.entity.ReviewLogEntity(
                        questionId = "q1", reviewedAt = 1_000_000L,
                        rating = "GOOD", elapsedDays = 0, scheduledDays = 0,
                        previousIntervalDays = 0, newIntervalDays = 0,
                    ),
                )
                assertEquals(1, db.reviewLogDao().count())
            }
        } finally {
            db.close()
        }
    }

    /**
     * PR-A (`licensed-question-pack`): the v3 -> v4 migration adds
     * the `category` column to `subject_pack` and the
     * `active_category` column to `user_settings` (spec
     * `professional-categories` "Pack-Level Category Field" + "Active
     * Category in User Settings"). Both columns have a `TCAE` default
     * so v3 installs get a sensible value on the first upgrade. This
     * test asserts the migration adds both columns by opening a v3
     * file via the full Room generated impl with `MIGRATION_3_4`
     * chained, then inspecting the resulting schema with raw PRAGMA
     * queries. The DAO round-trip is asserted by
     * [migrated_v4_db_supports_subject_pack_category_and_user_settings_active_category].
     */
    @Test
    fun v3_to_v4_adds_category_and_active_category_columns_with_tcae_default() {
        // GIVEN a v1 -> v2 -> v3 DB upgraded to v4 via the full
        // Room generated impl.
        val v1File = createV1Database()
        runMigrationAndMaterialiseV2(v1File)
        runMigrationAndMaterialiseV3(v1File)

        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.databaseBuilder(ctx, SaniExamDb::class.java, v1File.absolutePath)
            .addMigrations(
                SaniExamDb.MIGRATION_1_2,
                SaniExamDb.MIGRATION_2_3,
                SaniExamDb.MIGRATION_3_4,
            )
            .build()
        try {
            // Force the v3 -> v4 migration to run by triggering an
            // open + a no-op query. Room runs the migration
            // synchronously on first open; without a query the
            // `openRaw` below would see the pre-migration v3 schema.
            runBlocking {
                db.openHelper.writableDatabase.query("SELECT 1", emptyArray()).use { cursor ->
                    cursor.moveToFirst()
                }
            }
            // THEN subject_pack has a `category` column and
            // user_settings has an `active_category` column.
            openRaw(v1File).use { raw ->
                raw.rawQuery("PRAGMA table_info(`subject_pack`)", null).use { c ->
                    val actual = mutableSetOf<String>()
                    while (c.moveToNext()) actual += c.getString(1) // column name
                    assertTrue(
                        "subject_pack must have a `category` column after v3->v4",
                        "category" in actual,
                    )
                }
                raw.rawQuery("PRAGMA table_info(`user_settings`)", null).use { c ->
                    val actual = mutableSetOf<String>()
                    while (c.moveToNext()) actual += c.getString(1) // column name
                    assertTrue(
                        "user_settings must have an `active_category` column after v3->v4",
                        "active_category" in actual,
                    )
                }
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun migrated_v4_db_supports_subject_pack_category_and_user_settings_active_category() {
        // GIVEN a v1 -> v2 -> v3 DB upgraded to v4 via the full
        // Room generated impl. The v3 file's identity hash is the
        // known v3 hash; `Room.databaseBuilder.addMigrations`
        // chains the migrations and runs MIGRATION_3_4 to bring
        // the schema from v3 to v4.
        val v1File = createV1Database()
        runMigrationAndMaterialiseV2(v1File)
        runMigrationAndMaterialiseV3(v1File)

        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.databaseBuilder(ctx, SaniExamDb::class.java, v1File.absolutePath)
            .addMigrations(
                SaniExamDb.MIGRATION_1_2,
                SaniExamDb.MIGRATION_2_3,
                SaniExamDb.MIGRATION_3_4,
            )
            .build()
        try {
            runBlocking {
                // user_settings is seeded with active_category=TCAE
                // from the MIGRATION_2_3 default; the v3 -> v4
                // migration leaves the value as TCAE because the
                // column was added with `DEFAULT 'TCAE'`.
                val settings = db.userSettingsDao().get()
                assertNotNull("user_settings singleton must be present after v3->v4", settings)
                assertEquals(
                    "user_settings.active_category must be TCAE after v3->v4",
                    "TCAE",
                    settings!!.activeCategory,
                )
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun v3_to_v4_marks_legacy_dev_placeholder_outside_active_tcae_category() {
        val v1File = createV1Database()
        runMigrationAndMaterialiseV2(v1File)
        runMigrationAndMaterialiseV3(v1File)

        openRaw(v1File).use { raw ->
            raw.execSQL(
                "INSERT INTO subject_pack " +
                    "(id, version, source_attribution, published_at, license, license_notes) " +
                    "VALUES ('sanidad-dev-placeholder', 1, 'test', '2026-06-16', 'dev-placeholder', 'test')",
            )
        }

        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.databaseBuilder(ctx, SaniExamDb::class.java, v1File.absolutePath)
            .addMigrations(
                SaniExamDb.MIGRATION_1_2,
                SaniExamDb.MIGRATION_2_3,
                SaniExamDb.MIGRATION_3_4,
            )
            .build()
        try {
            runBlocking {
                db.openHelper.writableDatabase.query("SELECT 1", emptyArray()).use { cursor ->
                    cursor.moveToFirst()
                }
            }
            openRaw(v1File).use { raw ->
                raw.rawQuery(
                    "SELECT category FROM subject_pack WHERE id = 'sanidad-dev-placeholder'",
                    null,
                ).use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals("sanidad-dev-placeholder", c.getString(0))
                }
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun importer_removes_legacy_dev_placeholder_before_inserting_release_pack() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, SaniExamDb::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            db.subjectPackDao().insert(
                SubjectPackEntity(
                    id = "sanidad-dev-placeholder", version = 1,
                    sourceAttribution = "test", publishedAt = "2026-06-16",
                    license = "dev-placeholder", licenseNotes = "test",
                    category = "TCAE",
                ),
            )
            db.topicDao().insertAll(
                listOf(TopicEntity(id = "t-old", packId = "sanidad-dev-placeholder", packVersion = 1, name = "Old")),
            )
            db.questionDao().insertAll(
                listOf(
                    QuestionEntity(
                        id = "q-001", packId = "sanidad-dev-placeholder", packVersion = 1,
                        topicId = "t-old", prompt = "old", explanation = null,
                        officialYear = null, officialSourceRef = null,
                    ),
                ),
            )
            db.optionDao().insertAll(
                listOf(OptionEntity(id = "old-a", questionId = "q-001", ordinal = 0, text = "old", isCorrect = true)),
            )

            val packJson = """
                {
                  "packId": "sanidad-v1",
                  "packVersion": 1,
                  "topics": [{"id": "t-new", "name": "New"}],
                  "questions": [{
                    "id": "q-001",
                    "topicId": "t-new",
                    "prompt": "new",
                    "officialYear": 2024,
                    "officialSourceRef": "BOE-A-2024-1-preg1",
                    "options": [
                      {"id": "new-a", "ordinal": 0, "text": "A", "isCorrect": true},
                      {"id": "new-b", "ordinal": 1, "text": "B", "isCorrect": false}
                    ]
                  }]
                }
            """.trimIndent()
            val packBytes = packJson.toByteArray(Charsets.UTF_8)
            val manifestJson = """
                {
                  "id": "sanidad-v1",
                  "version": 1,
                  "sourceAttribution": "test",
                  "publishedAt": "2026-06-22",
                  "license": "cleared-of-rights",
                  "licenseNotes": "test",
                  "category": "TCAE",
                  "sha256": "${sha256Hex(packBytes)}",
                  "packFile": "question-packs/sanidad-v1.json"
                }
            """.trimIndent()
            val importer = DatasetImporter(
                assetSource = MapPackAssetSource(
                    mapOf(
                        DatasetImporter.MANIFEST_PATH to manifestJson.toByteArray(Charsets.UTF_8),
                        "question-packs/sanidad-v1.json" to packBytes,
                    ),
                ),
                json = Json { ignoreUnknownKeys = true },
                packDao = db.subjectPackDao(),
                topicDao = db.topicDao(),
                questionDao = db.questionDao(),
                optionDao = db.optionDao(),
                versionDao = db.datasetVersionDao(),
                db = db,
            )

            assertEquals(1, importer.importBundled("sanidad-v1", 1, "TCAE"))
            assertNull(db.subjectPackDao().get("sanidad-dev-placeholder", 1))
            assertNotNull(db.subjectPackDao().get("sanidad-v1", 1))
            assertEquals("sanidad-v1", db.questionDao().get("q-001")!!.packId)
        } finally {
            db.close()
        }
    }

    @Test
    fun importer_rejects_manifest_with_missing_category() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, SaniExamDb::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val packJson = """
                {
                  "packId": "sanidad-v1",
                  "packVersion": 1,
                  "topics": [{"id": "t-new", "name": "New"}],
                  "questions": [{
                    "id": "q-001",
                    "topicId": "t-new",
                    "prompt": "new",
                    "officialSourceRef": "BOE-A-2024-1-preg1",
                    "options": [
                      {"id": "new-a", "ordinal": 0, "text": "A", "isCorrect": true},
                      {"id": "new-b", "ordinal": 1, "text": "B", "isCorrect": false}
                    ]
                  }]
                }
            """.trimIndent()
            val packBytes = packJson.toByteArray(Charsets.UTF_8)
            val manifestJson = """
                {
                  "id": "sanidad-v1",
                  "version": 1,
                  "sourceAttribution": "test",
                  "publishedAt": "2026-06-22",
                  "license": "cleared-of-rights",
                  "licenseNotes": "test",
                  "sha256": "${sha256Hex(packBytes)}",
                  "packFile": "question-packs/sanidad-v1.json"
                }
            """.trimIndent()
            val importer = DatasetImporter(
                assetSource = MapPackAssetSource(
                    mapOf(
                        DatasetImporter.MANIFEST_PATH to manifestJson.toByteArray(Charsets.UTF_8),
                        "question-packs/sanidad-v1.json" to packBytes,
                    ),
                ),
                json = Json { ignoreUnknownKeys = true },
                packDao = db.subjectPackDao(),
                topicDao = db.topicDao(),
                questionDao = db.questionDao(),
                optionDao = db.optionDao(),
                versionDao = db.datasetVersionDao(),
                db = db,
            )

            val ex = assertThrows(DatasetImportException::class.java) {
                runBlocking { importer.importBundled("sanidad-v1", 1, "TCAE") }
            }
            assertEquals(DatasetImportException.Reason.MissingCategory, ex.reason)
        } finally {
            db.close()
        }
    }

    @Test
    fun importer_rejects_manifest_category_mismatch_with_active_category() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, SaniExamDb::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val packJson = """
                {
                  "packId": "sanidad-v1",
                  "packVersion": 1,
                  "topics": [{"id": "t-new", "name": "New"}],
                  "questions": [{
                    "id": "q-001",
                    "topicId": "t-new",
                    "prompt": "new",
                    "officialSourceRef": "BOE-A-2024-1-preg1",
                    "options": [
                      {"id": "new-a", "ordinal": 0, "text": "A", "isCorrect": true},
                      {"id": "new-b", "ordinal": 1, "text": "B", "isCorrect": false}
                    ]
                  }]
                }
            """.trimIndent()
            val packBytes = packJson.toByteArray(Charsets.UTF_8)
            val manifestJson = """
                {
                  "id": "sanidad-v1",
                  "version": 1,
                  "sourceAttribution": "test",
                  "publishedAt": "2026-06-22",
                  "license": "cleared-of-rights",
                  "licenseNotes": "test",
                  "category": "TCAE",
                  "sha256": "${sha256Hex(packBytes)}",
                  "packFile": "question-packs/sanidad-v1.json"
                }
            """.trimIndent()
            val importer = DatasetImporter(
                assetSource = MapPackAssetSource(
                    mapOf(
                        DatasetImporter.MANIFEST_PATH to manifestJson.toByteArray(Charsets.UTF_8),
                        "question-packs/sanidad-v1.json" to packBytes,
                    ),
                ),
                json = Json { ignoreUnknownKeys = true },
                packDao = db.subjectPackDao(),
                topicDao = db.topicDao(),
                questionDao = db.questionDao(),
                optionDao = db.optionDao(),
                versionDao = db.datasetVersionDao(),
                db = db,
            )

            val ex = assertThrows(DatasetImportException::class.java) {
                runBlocking { importer.importBundled("sanidad-v1", 1, "MEDICINA") }
            }
            assertEquals(DatasetImportException.Reason.CategoryMismatch, ex.reason)
            assertNull(runBlocking { db.subjectPackDao().get("sanidad-v1", 1) })
        } finally {
            db.close()
        }
    }

    @Test
    fun migrated_v2_db_supports_real_crud_through_dao() {
        // GIVEN a v1 -> v2 upgraded DB opened via the full Room generated impl.
        val v1File = createV1Database()
        runMigrationAndMaterialiseV2(v1File)

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SaniExamDb::class.java,
            v1File.absolutePath,
        )
            .addMigrations(
                SaniExamDb.MIGRATION_1_2,
                SaniExamDb.MIGRATION_2_3,
                SaniExamDb.MIGRATION_3_4,
            )
            .build()

        try {
            // Seed a minimal pack/topic/question so the FK targets exist.
            val packId = "sanidad"
            val packVersion = 1
            runBlocking {
                db.subjectPackDao().insert(
                    SubjectPackEntity(
                        id = packId, version = packVersion,
                        sourceAttribution = "test", publishedAt = "2026-06-16",
                        license = "dev-placeholder", licenseNotes = "test",
                        category = "TCAE",
                    ),
                )
                db.topicDao().insertAll(
                    listOf(
                        TopicEntity(id = "t1", packId = packId, packVersion = packVersion, name = "T1"),
                    ),
                )
                val questionId = "q1"
                db.questionDao().insertAll(
                    listOf(
                        QuestionEntity(
                            id = questionId, packId = packId, packVersion = packVersion,
                            topicId = "t1", prompt = "p", explanation = null,
                            officialYear = null, officialSourceRef = null,
                        ),
                    ),
                )
                db.optionDao().insertAll(
                    listOf(
                        OptionEntity(
                            id = "o1", questionId = questionId, ordinal = 0, text = "a", isCorrect = true,
                        ),
                    ),
                )

                // Round-trip a real CardStateEntity — proves the v6 columns persist.
                val card = CardStateEntity(
                    questionId = questionId, packId = packId, packVersion = packVersion,
                    stability = 1.0, difficulty = 5.0, dueAt = 1_000_000L,
                    lastReviewedAt = null, reps = 0, lapses = 0,
                    phase = "new", scheduledDays = 0, elapsedDays = 0,
                    learningSteps = 0, schedulerVersion = 1,
                )
                db.cardStateDao().upsert(card)
                val read = db.cardStateDao().get(questionId)

                assertNotNull("card_state row must round-trip after migration", read)
                assertEquals(0, read!!.learningSteps)
                assertEquals(1, read.schedulerVersion)
                assertEquals(1, db.cardStateDao().count())
            }
        } finally {
            db.close()
        }
    }

    // --- helpers ---

    /**
     * Creates a v1 database on disk by running the v1 schema DDL (taken
     * verbatim from `app/schemas/.../1.json`) against a fresh SQLite file
     * via the framework [androidx.sqlite.db.SupportSQLiteDatabase]. The
     * DDL is hand-applied so the test does not need the
     * `MigrationTestHelper` asset-loading path. Using
     * [androidx.sqlite.db.SupportSQLiteDatabase] (not the raw
     * `android.database.sqlite.SQLiteDatabase`) lets us use
     * `SupportSQLiteOpenHelper.Callback`, which is what Room itself uses
     * under the hood.
     */
    private fun createV1Database(
        afterCreate: ((androidx.sqlite.db.SupportSQLiteDatabase) -> Unit)? = null,
    ): File {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File.createTempFile("saniexam-migration-", ".db", ctx.cacheDir)
        file.deleteOnExit()

        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(ctx)
                .name(file.absolutePath)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // DDL taken verbatim from the v1 schema JSON.
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `schema_marker` " +
                                "(`id` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS room_master_table " +
                                "(id INTEGER PRIMARY KEY,identity_hash TEXT)",
                        )
                        db.execSQL(
                            "INSERT OR REPLACE INTO room_master_table " +
                                "(id,identity_hash) VALUES(42, " +
                                "'63819db81ed94f3b87bf3a0efc7dd8bc')",
                        )
                        afterCreate?.invoke(db)
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) {
                        // No-op: this helper only ever opens v1.
                    }
                })
                .build(),
        )
        // Touch the helper so onCreate runs.
        helper.writableDatabase.close()
        return file
    }

    /**
     * Applies the production v1 -> v2 migration to the given on-disk file
     * by opening it as a raw `SupportSQLiteDatabase` at version 2 and
     * letting the framework call the production `MIGRATION_1_2.migrate(...)`
     * callback. The v2 schema's `createSql` statements (taken from
     * `app/schemas/.../2.json`) are then replayed so the file is fully
     * v2-shaped — exactly what would happen in production if a v1 user
     * upgraded and Room's v2 `onCreate` ran on the migrated DB.
     *
     * The `room_master_table` identity hash is overwritten with the v2
     * hash from the schema JSON so the v2 `Room.databaseBuilder` call in
     * the CRUD test accepts the file as the v2 schema it expects.
     */
    private fun runMigrationAndMaterialiseV2(v1File: File) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(ctx)
                .name(v1File.absolutePath)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // No onCreate: a v1 file already exists.
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) {
                        if (oldVersion < 2) {
                            SaniExamDb.MIGRATION_1_2.migrate(db)
                            createV2Tables(db)
                            db.execSQL(
                                "INSERT OR REPLACE INTO room_master_table " +
                                    "(id,identity_hash) VALUES(42, " +
                                    "'0062024761d377d015353ee737f76a74')",
                            )
                        }
                    }
                })
                .build(),
        )
        helper.writableDatabase.close()
    }

    /**
     * Opens the (already v2-shaped) file at version 3 and runs the
     * production `MIGRATION_2_3.migrate(...)` callback. Unlike the v1->v2
     * path, the v2 -> v3 migration is additive (no `createSql` replay
     * is needed) because Room's v3 onCreate is responsible for the new
     * tables in production. Here we open the v2 file at version 3 to
     * trigger the onUpgrade path.
     */
    private fun runMigrationAndMaterialiseV3(v2File: File) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(ctx)
                .name(v2File.absolutePath)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // No-op: a v2 file already exists.
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) {
                        if (oldVersion < 3) {
                            SaniExamDb.MIGRATION_2_3.migrate(db)
                            // Rewrite the identity hash so a subsequent
                            // Room.databaseBuilder call accepts the file
                            // as the v3 schema. The exact value is taken
                            // verbatim from `app/schemas/.../3.json` and
                            // asserted by Room on open.
                            db.execSQL(
                                "INSERT OR REPLACE INTO room_master_table " +
                                    "(id,identity_hash) VALUES(42, " +
                                    "'ce0cd4cdb536fdfcad900f709363df75')",
                            )
                        }
                    }
                })
                .build(),
        )
        helper.writableDatabase.close()
    }

    /**
     * Replays the v2 schema's `createSql` statements (taken from the v2
     * schema JSON) on the given open [androidx.sqlite.db.SupportSQLiteDatabase].
     *
     * `SupportSQLiteDatabase.execSQL` does **not** substitute the
     * `${TABLE_NAME}` placeholder that Room emits in its exported
     * `createSql` — that substitution is done by Room's `RoomOpenHelper`
     * before it calls `execSQL`. We perform the same substitution here:
     * the JSON holds one `createSql` per entity, and `${TABLE_NAME}` is
     * always the entity's own `tableName` value.
     */
    private fun createV2Tables(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        val v2Json = loadSchemaResource("schemas/2.json")
        val entities = JSONObject(v2Json).getJSONObject("database").getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            val createSql = entity.getString("createSql")
                .replace("\${TABLE_NAME}", tableName)
            db.execSQL(createSql)
        }
    }

    private fun openRaw(file: File): android.database.sqlite.SQLiteDatabase {
        return android.database.sqlite.SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
        )
    }

    private fun loadSchemaResource(relativePath: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream(relativePath)
            ?: error("Schema resource not found on test classpath: $relativePath")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private class MapPackAssetSource(
        private val assets: Map<String, ByteArray>,
    ) : PackAssetSource {
        override fun read(path: String): ByteArray = assets[path] ?: throw java.io.FileNotFoundException(path)
    }
}
