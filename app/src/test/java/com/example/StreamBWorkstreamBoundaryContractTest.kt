package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBWorkstreamBoundaryContractTest {
    private val workstreams = File("../WORKSTREAMS.md")

    @Test
    fun streamBRemainsUiUxOnlyAndProtectsCoreOwnership() {
        val source = workstreams.readText()
        val streamB = source.substringAfter("## Stream B — UI/UX / MyFinanda-style Product Experience")
            .substringBefore("## Stream C — Provider / Commerce Integrations")

        listOf(
            "Dashboard visual hierarchy and information architecture",
            "Savings/opportunity presentation",
            "Bills/activity presentation",
            "Profile/settings presentation",
            "navigation polish",
            "design system, typography, spacing, icons and states",
            "RTL/Hebrew usability"
        ).forEach { ownedScope ->
            assertTrue("Stream B lost owned scope: $ownedScope", streamB.contains(ownedScope))
        }

        listOf(
            "backend Functions",
            "Firestore schema/rules",
            "Gmail/Auth mechanics",
            "financial calculations",
            "offer ranking",
            "commission/attribution logic",
            "BackendRepository.kt"
        ).forEach { protectedScope ->
            assertTrue("Stream B boundary no longer protects: $protectedScope", streamB.contains(protectedScope))
        }
    }

    @Test
    fun integrationOrderKeepsCoreValidationBeforeStreamBMerge() {
        val source = workstreams.readText()
        val integration = source.substringAfter("## Current integration order")

        assertTrue(integration.contains("Stream A fixes and validates E2E blockers"))
        assertTrue(integration.contains("Rebase/integrate Stream B onto the validated Stream A baseline"))
        assertTrue(integration.contains("Only then choose the next active stream"))
    }
}
