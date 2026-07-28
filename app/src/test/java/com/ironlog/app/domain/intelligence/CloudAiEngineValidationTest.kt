package com.ironlog.app.domain.intelligence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAiEngineValidationTest {
    @Test
    fun `plan validation requires exact day count and required exercise fields`() {
        val valid = """{
          "type":"ironlog_plan",
          "version":1,
          "plan":{"name":"Accurate plan","days":[
            {"name":"Upper","exercises":[{"exerciseName":"Barbell Bench Press","sets":3,"reps":"6-8","restSeconds":180}]},
            {"name":"Lower","exercises":[{"exerciseName":"Barbell Squat","sets":3,"reps":"5","restSeconds":210}]}
          ]}
        }"""

        assertTrue(CloudAiEngine.isStructurallyValidGeneratedPlan(valid, expectedDays = 2))
        assertFalse(CloudAiEngine.isStructurallyValidGeneratedPlan(valid, expectedDays = 3))
        assertFalse(CloudAiEngine.isStructurallyValidGeneratedPlan(valid.replace("\"sets\":3", "\"sets\":0"), expectedDays = 2))
    }

    @Test
    fun `provider URL protects keys while allowing local development`() {
        assertTrue(CloudAiEngine.validatedProviderUrl("https://api.example.com/v1", "models").startsWith("https://"))
        assertTrue(CloudAiEngine.validatedProviderUrl("http://localhost:11434/v1", "models").startsWith("http://localhost"))
        assertTrue(runCatching { CloudAiEngine.validatedProviderUrl("http://api.example.com/v1", "models") }.isFailure)
        assertTrue(runCatching { CloudAiEngine.validatedProviderUrl("https://key@api.example.com/v1", "models") }.isFailure)
    }
}
