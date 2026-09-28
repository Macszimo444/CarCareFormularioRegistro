package com.example.carcareformularioregistro.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceFlowTest {
    @Test fun dangerAnswersTerminateBeforeAnyTopicQuestion() {
        listOf("fire", "control", "uncertain").forEach { answer ->
            val state = GuidanceFlow.conversation(listOf(answer))
            assertTrue(state.current.isResult)
            assertEquals(GuidanceFlow.Attention.STOP, state.current.attention)
            assertEquals(listOf("safety"), state.answered.map { it.step.id })
        }
    }

    @Test fun eachSymptomOffersAReachableRouteAfterSafety() {
        val choices = GuidanceFlow.conversation(listOf("none")).current.choices
        assertEquals(setOf("brakes", "heat", "lamp", "start", "vibration", "leak"),
            choices.map { it.id }.toSet())
        choices.forEach { choice ->
            assertFalse(GuidanceFlow.conversation(listOf("none", choice.id)).current.isResult)
        }
    }

    @Test fun newlyReportedDangerOverridesEarlierNoDangerAnswer() {
        val paths = listOf(
            listOf("none", "brakes", "brake_control"),
            listOf("none", "vibration", "vibration_severe"),
            listOf("none", "leak", "leak_danger"),
            listOf("none", "lamp", "lamp_critical"),
            listOf("none", "heat", "heat_now"),
            listOf("none", "lamp", "lamp_engine", "engine_flashes")
        )
        paths.forEach { path ->
            val step = GuidanceFlow.conversation(path).current
            assertTrue(step.isResult)
            assertEquals(GuidanceFlow.Attention.STOP, step.attention)
        }
    }

    @Test fun changingAnAnswerDoesNotKeepTheAbandonedBranch() {
        val original = listOf("none", "lamp", "lamp_engine", "engine_steady")
        val changed = GuidanceFlow.answer(GuidanceFlow.back(original), "engine_flashes")
        assertEquals("engine_stop", GuidanceFlow.conversation(changed).current.id)
        assertFalse(changed.contains("engine_steady"))
        assertEquals(4, GuidanceFlow.conversation(changed).answered.size)
    }

    @Test fun savedAnswersRebuildSameTranscriptAndQuestion() {
        val original = listOf("none", "brakes", "brake_noise")
        val restoredIds = ArrayList(original)
        assertEquals(GuidanceFlow.conversation(original), GuidanceFlow.conversation(restoredIds))
        assertEquals("brake_when", GuidanceFlow.conversation(restoredIds).current.id)
    }

    @Test fun invalidSavedAnswersNeverJumpToAnotherBranch() {
        val restored = GuidanceFlow.conversation(listOf("none", "brakes", "lamp_engine", "engine_steady"))
        assertEquals(listOf("none", "brakes"), restored.answerIds)
        assertEquals("brakes", restored.current.id)
    }

    @Test fun answersAfterAResultAreIgnored() {
        val stopped = GuidanceFlow.conversation(listOf("fire", "none", "brakes"))
        assertEquals("fire_result", stopped.current.id)
        assertEquals(listOf("fire"), stopped.answerIds)
        assertEquals(listOf("fire"), GuidanceFlow.answer(listOf("fire"), "none"))
    }

    @Test fun clearingAnswersReturnsToSafetyTriage() {
        assertEquals("safety", GuidanceFlow.conversation(emptyList()).current.id)
        assertTrue(GuidanceFlow.back(emptyList()).isEmpty())
        assertEquals("safety", GuidanceFlow.conversation(GuidanceFlow.back(listOf("fire"))).current.id)
    }

    @Test fun allPathsTerminateWithoutCyclesAndAllResultsOfferAttention() {
        val reached = mutableSetOf<String>()
        fun visit(id: String, ancestors: Set<String>) {
            assertFalse("Cycle at $id", id in ancestors)
            assertTrue("Unbounded conversation", ancestors.size <= 4)
            val step = GuidanceFlow.steps.getValue(id)
            reached += id
            if (step.isResult) {
                assertTrue(step.attention != GuidanceFlow.Attention.INFORMATION)
                assertTrue(step.body.isNotBlank())
            } else {
                assertEquals(step.choices.size, step.choices.map { it.id }.toSet().size)
                step.choices.forEach { visit(it.next, ancestors + id) }
            }
        }
        visit("safety", emptySet())
        assertEquals(GuidanceFlow.steps.keys, reached)
    }

    @Test fun sourceReferencesResolveToBundledHttpsLinks() {
        val ids = GuidanceContent.sources.map { it.id }.toSet()
        assertEquals(GuidanceContent.sources.size, ids.size)
        val used = GuidanceFlow.steps.values.flatMap { it.sourceIds } +
            GuidanceContent.guide.flatMap { it.sourceIds }
        assertTrue(ids.containsAll(used))
        GuidanceContent.sources.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.url.startsWith("https://"))
        }
    }
}
