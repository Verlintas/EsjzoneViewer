package com.breakyuna.esjzone

import com.breakyuna.esjzone.update.ReleaseVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test
    fun betaVersionComparison() {
        assertTrue(ReleaseVersion.isNewerStableRelease("0.1.0", "beta-0.1.0"))
        assertTrue(ReleaseVersion.isNewerStableRelease("v0.1.0", "beta-0.1.0"))
        assertTrue(ReleaseVersion.isNewerStableRelease("0.2.0", "beta-0.1.0"))
        assertFalse(ReleaseVersion.isNewerStableRelease("0.0.9", "beta-0.1.0"))
        assertFalse(ReleaseVersion.isNewerStableRelease("beta-0.1.0", "beta-0.1.0"))
        assertFalse(ReleaseVersion.isNewerStableRelease("0.1.0-beta.1", "beta-0.1.0"))
        assertFalse(ReleaseVersion.isNewerStableRelease("0.1.0", "0.1.0"))
    }
}
