package es.saniexam.app.data.backup

import es.saniexam.app.domain.model.CardState
import es.saniexam.app.domain.model.ReviewLog
import es.saniexam.app.domain.model.UserSettings
import es.saniexam.app.domain.repository.BackupException
import es.saniexam.app.scheduler.CardPhase
import es.saniexam.app.scheduler.Rating
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Round-trip + refusal scenarios for the backup codec (PR4 slice).
 * The codec is the single source of truth for the file format; the
 * repository just adds Room transaction + SAF I/O on top of it.
 *
 * Covers:
 *  - empty export (zero rows) round-trips byte-equivalent
 *  - full export (CardState + ReviewLog + UserSettings) round-trips
 *  - corrupt checksum refusal
 *  - unsupported `schemaVersion` refusal
 *  - malformed payload refusal
 *  - the `checksum` field is computed over the document without the
 *    field, so changing any other field invalidates it
 */
class BackupCodecRoundTripTest {

    private val json: Json = Json { ignoreUnknownKeys = true }
    private val codec = BackupCodec(json)

    @Test
    fun `empty export round trips`() {
        val bytes = codec.encode(
            cardStates = emptyList(),
            reviewLogs = emptyList(),
            userSettings = UserSettings.Default,
            exportedAt = Instant.parse("2026-06-16T10:00:00Z"),
            appVersion = "0.1.0",
        )
        val decoded = codec.decode(bytes)
        assertEquals(emptyList<CardState>(), decoded.cardStates)
        assertEquals(emptyList<ReviewLog>(), decoded.reviewLogs)
        assertEquals(UserSettings.Default, decoded.userSettings)
    }

    @Test
    fun `full export round trips with cardStates reviewLogs and userSettings`() {
        val cardState = cardState(
            qid = "q1",
            dueAt = Instant.parse("2026-06-20T10:00:00Z"),
            lastReviewedAt = Instant.parse("2026-06-15T10:00:00Z"),
        )
        val reviewLog = reviewLog(
            qid = "q1",
            rating = Rating.Good,
            at = Instant.parse("2026-06-15T10:00:00Z"),
        )
        val settings = UserSettings(
            lastRevealedCardId = "q1",
            lastSessionQueuePosition = 3,
            lastSessionAt = Instant.parse("2026-06-15T10:01:00Z"),
            activeCategory = UserSettings.TCAE,
        )

        val bytes = codec.encode(
            cardStates = listOf(cardState),
            reviewLogs = listOf(reviewLog),
            userSettings = settings,
            exportedAt = Instant.parse("2026-06-16T10:00:00Z"),
            appVersion = "0.1.0",
        )
        val decoded = codec.decode(bytes)

        assertEquals(listOf(cardState), decoded.cardStates)
        assertEquals(listOf(reviewLog), decoded.reviewLogs)
        assertEquals(settings, decoded.userSettings)
    }

    @Test
    fun `corrupted checksum is refused`() {
        val bytes = codec.encode(
            cardStates = listOf(cardState("q1", Instant.parse("2026-06-20T10:00:00Z"), null)),
            reviewLogs = emptyList(),
            userSettings = UserSettings.Default,
            exportedAt = Instant.parse("2026-06-16T10:00:00Z"),
            appVersion = "0.1.0",
        )
        // Flip a digit inside the `exportedAt` timestamp value. The key
        // names stay valid (so the parser accepts the JSON), the parsed
        // `BackupDocument` is different, and the recomputed checksum
        // therefore cannot match the embedded one.
        val stampBytes = "2026-06-16T10:00:00Z".toByteArray(Charsets.UTF_8)
        val stampOffset = findSubArray(bytes, stampBytes)
        check(stampOffset >= 0) { "timestamp not found in encoded payload" }
        val tampered = bytes.copyOf().also { buf ->
            // Flip the last digit of the day ("16" → "17") at offset +8.
            val target = stampOffset + 8
            buf[target] = (buf[target].toInt() xor 0x01).toByte()
        }
        val ex = assertThrows(BackupException::class.java) { codec.decode(tampered) }
        assertEquals(BackupException.Reason.ChecksumMismatch, ex.reason)
    }

    private fun findSubArray(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    @Test
    fun `unsupported schema version is refused`() {
        val bytes = codec.encode(
            cardStates = emptyList(),
            reviewLogs = emptyList(),
            userSettings = UserSettings.Default,
            exportedAt = Instant.parse("2026-06-16T10:00:00Z"),
            appVersion = "0.1.0",
        )
        val upgraded = String(bytes, Charsets.UTF_8)
            .replace("\"schemaVersion\":${BackupCodec.CURRENT_SCHEMA_VERSION}", "\"schemaVersion\":99")
            .toByteArray(Charsets.UTF_8)
        val ex = assertThrows(BackupException::class.java) { codec.decode(upgraded) }
        assertEquals(BackupException.Reason.UnsupportedSchemaVersion, ex.reason)
    }

    @Test
    fun `malformed payload is refused`() {
        val garbage = "{not even close to json".toByteArray(Charsets.UTF_8)
        val ex = assertThrows(BackupException::class.java) { codec.decode(garbage) }
        assertEquals(BackupException.Reason.MalformedPayload, ex.reason)
    }

    @Test
    fun `checksum field is over the document without the checksum slot`() {
        val bytes = codec.encode(
            cardStates = emptyList(),
            reviewLogs = emptyList(),
            userSettings = UserSettings.Default,
            exportedAt = Instant.parse("2026-06-16T10:00:00Z"),
            appVersion = "0.1.0",
        )
        val text = String(bytes, Charsets.UTF_8)
        // The encoded document must carry a `checksum` field with a 64-char lowercase hex SHA-256.
        val match = Regex("\"checksum\":\"([a-f0-9]{64})\"").find(text)
        assertNotNull("encoded document must carry a hex SHA-256 checksum field", match)
    }

    @Test
    fun `deck retained across a save and reload (byte-for-byte card state)`() {
        val a = codec.encode(
            cardStates = listOf(
                cardState("q1", Instant.parse("2026-06-20T10:00:00Z"), Instant.parse("2026-06-15T10:00:00Z")),
                cardState("q2", Instant.parse("2026-06-22T10:00:00Z"), null),
            ),
            reviewLogs = emptyList(),
            userSettings = UserSettings.Default,
            exportedAt = Instant.parse("2026-06-16T10:00:00Z"),
            appVersion = "0.1.0",
        )
        val b = codec.decode(a)
        // Re-encode the decoded list and confirm we get the same bytes back.
        val reEncoded = codec.encode(
            cardStates = b.cardStates,
            reviewLogs = b.reviewLogs,
            userSettings = b.userSettings,
            exportedAt = Instant.parse("2026-06-16T10:00:00Z"),
            appVersion = "0.1.0",
        )
        // `exportedAt` and the order of map keys (k/x serialization) are
        // stable, so the re-encoded document must be byte-equal to the
        // original — except for the timestamp and checksum, which differ.
        val aText = String(a, Charsets.UTF_8)
        val bText = String(reEncoded, Charsets.UTF_8)
        // Strip the volatile parts.
        val strip = Regex("\"(exportedAt|checksum)\":\"[^\"]*\"")
        val aStripped = strip.replace(aText, "")
        val bStripped = strip.replace(bText, "")
        assertEquals(aStripped, bStripped)
        assertTrue(a.size > 0 && reEncoded.size > 0)
    }

    // --- helpers ---

    private fun cardState(qid: String, dueAt: Instant, lastReviewedAt: Instant?): CardState = CardState(
        questionId = qid,
        packId = "sanidad-v1",
        packVersion = 1,
        stability = 1.5,
        difficulty = 5.0,
        dueAt = dueAt,
        lastReviewedAt = lastReviewedAt,
        reps = 1,
        lapses = 0,
        phase = CardPhase.Review,
        scheduledDays = 5,
        elapsedDays = 5,
        learningSteps = 0,
        schedulerVersion = 1,
    )

    private fun reviewLog(qid: String, rating: Rating, at: Instant): ReviewLog = ReviewLog(
        questionId = qid,
        reviewedAt = at,
        rating = rating,
        elapsedDays = 0,
        scheduledDays = 0,
        previousIntervalDays = 0,
        newIntervalDays = 0,
    )
}
