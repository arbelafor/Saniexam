package es.saniexam.app.data.ingest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JUnit-only tests for the importer's pure helpers. The importer itself
 * depends on Android types and is exercised via the integration test path
 * (deferred to PR4 with the Room transaction test).
 *
 * PR-A: the [DatasetImportException.Reason] enum gained
 * `MissingCategory` and `ProvenanceMissing` so a pack without
 * pack-level `category` or a per-Q `officialSourceRef` can be
 * rejected without a stringly-typed comparison. The release
 * gate is the first line of defence; the validator's enum
 * reasons are the second.
 */
class DatasetImporterValidationTest {

    @Test
    fun `sha256 of known vector is stable`() {
        // SHA-256 of the empty string — easy cross-check with
        // `echo -n "" | sha256sum`.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex("".toByteArray(Charsets.UTF_8)),
        )
    }

    @Test
    fun `sha256 is lowercase hex and case-insensitive`() {
        val hex = sha256Hex("abc".toByteArray(Charsets.UTF_8))
        assertEquals(64, hex.length)
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(hex, hex.uppercase().lowercase())
    }

    @Test
    fun `different inputs produce different digests`() {
        assertNotEquals(
            sha256Hex("sanidad".toByteArray(Charsets.UTF_8)),
            sha256Hex("sanidadv1".toByteArray(Charsets.UTF_8)),
        )
    }

    @Test
    fun `reason enum covers every spec failure path`() {
        val expected = setOf(
            "AssetMissing", "AssetUnreadable", "ManifestMissing", "ManifestMismatch",
            "PackIdMismatch", "VersionMismatch", "ChecksumMismatch", "MissingAttribution",
            "MissingCategory", "CategoryMismatch", "ProvenanceMissing",
            "QuestionMissingFields", "ZeroOrMultipleCorrectOptions",
            "OrphanTopicReference", "DuplicateQuestionId",
        )
        assertEquals(expected, DatasetImportException.Reason.values().map { it.name }.toSet())
    }
}
