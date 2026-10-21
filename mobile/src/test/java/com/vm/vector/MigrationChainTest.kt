package com.vm.vector

import com.vm.core.models.VECTOR_DB_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class MigrationChainTest {

    @Test
    fun everyVersionFromOldestMigratableReachesCurrent() {
        val byStart = VECTOR_MIGRATIONS.associateBy { it.startVersion }
        assertEquals("Duplicate migration start versions", VECTOR_MIGRATIONS.size, byStart.size)
        var version = OLDEST_MIGRATABLE_VERSION
        while (version < VECTOR_DB_VERSION) {
            val step = byStart[version]
            assertNotNull("Missing migration from v$version", step)
            assertEquals("Migration from v$version must go one step", version + 1, step!!.endVersion)
            version = step.endVersion
        }
        assertEquals(VECTOR_DB_VERSION, version)
    }

    @Test
    fun destructiveFallbackNeverOverlapsMigrations() {
        val starts = VECTOR_MIGRATIONS.map { it.startVersion }.toSet()
        DESTRUCTIVE_FROM_VERSIONS.forEach { assertFalse("v$it is both destructive and migratable", it in starts) }
        assertFalse(VECTOR_DB_VERSION in DESTRUCTIVE_FROM_VERSIONS)
    }
}
