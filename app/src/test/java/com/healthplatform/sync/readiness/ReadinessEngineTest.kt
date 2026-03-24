package com.healthplatform.sync.readiness

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ReadinessEngineTest {

    private val recentTime = Instant.now().toString()
    private val staleTime = Instant.now().minusSeconds(15 * 3600).toString() // 15h ago
    private val veryStaleTime = Instant.now().minusSeconds(30 * 3600).toString() // 30h ago

    private val defaultConfig = ReadinessEngine.Config()

    // -------------------------------------------------------------------------
    // Aggregate scoring
    // -------------------------------------------------------------------------

    @Test
    fun `good metrics produce high score and Good to go`() {
        // Sleep 480min (8h) → 100, BP 115 → 100
        // Weighted: (100*0.30 + 100*0.20) / 0.50 = 100 → "Good to go"
        val inputs = listOf(
            ReadinessEngine.RawInput(ReadinessInputId.SLEEP, 480.0, recentTime),
            ReadinessEngine.RawInput(ReadinessInputId.BLOOD_PRESSURE, 115.0, recentTime),
        )
        val result = ReadinessEngine.compute(inputs, defaultConfig)

        assertNotNull(result.aggregateScore)
        assertEquals(100, result.aggregateScore)
        assertEquals("Good to go", result.label)
    }

    @Test
    fun `poor metrics produce low score and Recovery day`() {
        // Sleep 300min (<6h) → 25, BP 145 → 10
        // Weighted: (25*0.30 + 10*0.20) / 0.50 = (7.5+2)/0.50 = 19 → "Recovery day"
        val inputs = listOf(
            ReadinessEngine.RawInput(ReadinessInputId.SLEEP, 300.0, recentTime),
            ReadinessEngine.RawInput(ReadinessInputId.BLOOD_PRESSURE, 145.0, recentTime),
        )
        val result = ReadinessEngine.compute(inputs, defaultConfig)

        assertNotNull(result.aggregateScore)
        assertTrue(result.aggregateScore!! < 50)
        assertEquals("Recovery day", result.label)
    }

    @Test
    fun `mixed metrics produce Take it easy`() {
        // Sleep 400min (6h40m, 360-420 range) → 70, BP 125 (120-130 range) → 70
        // Weighted: (70*0.30 + 70*0.20) / 0.50 = 35/0.50 = 70 → "Take it easy"
        val inputs = listOf(
            ReadinessEngine.RawInput(ReadinessInputId.SLEEP, 400.0, recentTime),
            ReadinessEngine.RawInput(ReadinessInputId.BLOOD_PRESSURE, 125.0, recentTime),
        )
        val result = ReadinessEngine.compute(inputs, defaultConfig)

        assertNotNull(result.aggregateScore)
        assertEquals(70, result.aggregateScore)
        assertEquals("Take it easy", result.label)
    }

    // -------------------------------------------------------------------------
    // Missing / excluded inputs
    // -------------------------------------------------------------------------

    @Test
    fun `all inputs missing returns null score`() {
        val inputs = listOf(
            ReadinessEngine.RawInput(ReadinessInputId.SLEEP, null, null),
            ReadinessEngine.RawInput(ReadinessInputId.BLOOD_PRESSURE, null, null),
        )
        val result = ReadinessEngine.compute(inputs, defaultConfig)

        assertNull(result.aggregateScore)
        assertNull(result.label)
        assertEquals("Sync to update readiness", result.summary)
    }

    @Test
    fun `excluded inputs have zero effective weight`() {
        // HRV weight is 0 by default — should be excluded
        val inputs = listOf(
            ReadinessEngine.RawInput(ReadinessInputId.HRV, 55.0, recentTime),
        )
        val result = ReadinessEngine.compute(inputs, defaultConfig)

        assertNull(result.aggregateScore) // only input is excluded
        val hrvInput = result.inputs.first { it.id == ReadinessInputId.HRV }
        assertEquals(ReadinessInputStatus.EXCLUDED, hrvInput.status)
        assertEquals(0.0, hrvInput.effectiveWeight, 0.001)
    }

    @Test
    fun `HRV contributes when weight is nonzero`() {
        val config = defaultConfig.copy(hrvWeight = 0.25)
        val inputs = listOf(
            ReadinessEngine.RawInput(ReadinessInputId.SLEEP, 480.0, recentTime),
            ReadinessEngine.RawInput(ReadinessInputId.HRV, 55.0, recentTime),
        )
        val result = ReadinessEngine.compute(inputs, config)

        assertNotNull(result.aggregateScore)
        val hrvInput = result.inputs.first { it.id == ReadinessInputId.HRV }
        assertEquals(ReadinessInputStatus.FRESH, hrvInput.status)
        assertTrue(hrvInput.effectiveWeight > 0)
    }

    // -------------------------------------------------------------------------
    // Staleness
    // -------------------------------------------------------------------------

    @Test
    fun `degraded staleness halves weight`() {
        val inputs = listOf(
            ReadinessEngine.RawInput(ReadinessInputId.SLEEP, 480.0, staleTime), // 15h ago
        )
        val result = ReadinessEngine.compute(inputs, defaultConfig)

        val sleepInput = result.inputs.first { it.id == ReadinessInputId.SLEEP }
        assertEquals(ReadinessInputStatus.DEGRADED, sleepInput.status)
        assertEquals(0.15, sleepInput.effectiveWeight, 0.001) // 0.30 * 0.5
        assertNotNull(sleepInput.score)
    }

    @Test
    fun `very stale data is excluded`() {
        val inputs = listOf(
            ReadinessEngine.RawInput(ReadinessInputId.SLEEP, 480.0, veryStaleTime), // 30h ago
        )
        val result = ReadinessEngine.compute(inputs, defaultConfig)

        val sleepInput = result.inputs.first { it.id == ReadinessInputId.SLEEP }
        assertEquals(ReadinessInputStatus.MISSING, sleepInput.status)
        assertEquals(0.0, sleepInput.effectiveWeight, 0.001)
        assertNull(result.aggregateScore)
    }

    // -------------------------------------------------------------------------
    // Individual scoring functions
    // -------------------------------------------------------------------------

    @Test
    fun `sleep scoring discrete thresholds`() {
        val below360 = listOf(ReadinessEngine.RawInput(ReadinessInputId.SLEEP, 300.0, recentTime))
        val at360 = listOf(ReadinessEngine.RawInput(ReadinessInputId.SLEEP, 360.0, recentTime))
        val at400 = listOf(ReadinessEngine.RawInput(ReadinessInputId.SLEEP, 400.0, recentTime))
        val at420 = listOf(ReadinessEngine.RawInput(ReadinessInputId.SLEEP, 420.0, recentTime))

        assertEquals(25, ReadinessEngine.compute(below360, defaultConfig).inputs[0].score)
        assertEquals(70, ReadinessEngine.compute(at360, defaultConfig).inputs[0].score)
        assertEquals(70, ReadinessEngine.compute(at400, defaultConfig).inputs[0].score)
        assertEquals(100, ReadinessEngine.compute(at420, defaultConfig).inputs[0].score)
    }

    @Test
    fun `BP scoring discrete thresholds`() {
        val bp115 = listOf(ReadinessEngine.RawInput(ReadinessInputId.BLOOD_PRESSURE, 115.0, recentTime))
        val bp125 = listOf(ReadinessEngine.RawInput(ReadinessInputId.BLOOD_PRESSURE, 125.0, recentTime))
        val bp135 = listOf(ReadinessEngine.RawInput(ReadinessInputId.BLOOD_PRESSURE, 135.0, recentTime))
        val bp145 = listOf(ReadinessEngine.RawInput(ReadinessInputId.BLOOD_PRESSURE, 145.0, recentTime))

        assertEquals(100, ReadinessEngine.compute(bp115, defaultConfig).inputs[0].score)
        assertEquals(70, ReadinessEngine.compute(bp125, defaultConfig).inputs[0].score)
        assertEquals(40, ReadinessEngine.compute(bp135, defaultConfig).inputs[0].score)
        assertEquals(10, ReadinessEngine.compute(bp145, defaultConfig).inputs[0].score)
    }

    // -------------------------------------------------------------------------
    // Weight re-distribution
    // -------------------------------------------------------------------------

    @Test
    fun `single input gets full effective weight`() {
        // Only sleep available; BP is null. Score should be based on sleep alone.
        val inputs = listOf(
            ReadinessEngine.RawInput(ReadinessInputId.SLEEP, 480.0, recentTime),
            ReadinessEngine.RawInput(ReadinessInputId.BLOOD_PRESSURE, null, null),
        )
        val result = ReadinessEngine.compute(inputs, defaultConfig)

        assertNotNull(result.aggregateScore)
        // Aggregate should equal sleep's individual score since it's the only contributor
        val sleepScore = result.inputs.first { it.id == ReadinessInputId.SLEEP }.score
        assertEquals(sleepScore, result.aggregateScore)
    }

    // -------------------------------------------------------------------------
    // Training-load integration (Phase 3)
    // -------------------------------------------------------------------------

    @Test
    fun `training load contributes when weight is non-zero`() {
        val configWithTraining = ReadinessEngine.Config(
            sleepWeight = 0.30, bpWeight = 0.20,
            trainingLoadWeight = 0.10
        )
        val inputs = listOf(
            ReadinessEngine.RawInput(ReadinessInputId.SLEEP, 480.0, recentTime),
            ReadinessEngine.RawInput(ReadinessInputId.BLOOD_PRESSURE, 115.0, recentTime),
            ReadinessEngine.RawInput(ReadinessInputId.TRAINING_LOAD, 62.0, recentTime),
        )
        val result = ReadinessEngine.compute(inputs, configWithTraining)

        assertNotNull(result.aggregateScore)
        val trainingInput = result.inputs.first { it.id == ReadinessInputId.TRAINING_LOAD }
        assertEquals(ReadinessInputStatus.FRESH, trainingInput.status)
        assertEquals(62, trainingInput.score)
        assertEquals(0.10, trainingInput.effectiveWeight, 0.001)
    }

    @Test
    fun `training load excluded when weight is zero`() {
        val inputs = listOf(
            ReadinessEngine.RawInput(ReadinessInputId.SLEEP, 480.0, recentTime),
            ReadinessEngine.RawInput(ReadinessInputId.TRAINING_LOAD, 62.0, recentTime),
        )
        val result = ReadinessEngine.compute(inputs, defaultConfig) // trainingLoadWeight = 0

        val trainingInput = result.inputs.first { it.id == ReadinessInputId.TRAINING_LOAD }
        assertEquals(ReadinessInputStatus.EXCLUDED, trainingInput.status)
        assertEquals(0.0, trainingInput.effectiveWeight, 0.001)
    }

    @Test
    fun `training load missing degrades gracefully`() {
        val configWithTraining = ReadinessEngine.Config(
            sleepWeight = 0.30, bpWeight = 0.20,
            trainingLoadWeight = 0.10
        )
        val inputs = listOf(
            ReadinessEngine.RawInput(ReadinessInputId.SLEEP, 480.0, recentTime),
            ReadinessEngine.RawInput(ReadinessInputId.BLOOD_PRESSURE, 115.0, recentTime),
            ReadinessEngine.RawInput(ReadinessInputId.TRAINING_LOAD, null, null),
        )
        val result = ReadinessEngine.compute(inputs, configWithTraining)

        assertNotNull(result.aggregateScore)
        val trainingInput = result.inputs.first { it.id == ReadinessInputId.TRAINING_LOAD }
        assertEquals(ReadinessInputStatus.MISSING, trainingInput.status)
        // Score should still be computed from sleep + BP
        assertEquals(100, result.aggregateScore)
    }
}
