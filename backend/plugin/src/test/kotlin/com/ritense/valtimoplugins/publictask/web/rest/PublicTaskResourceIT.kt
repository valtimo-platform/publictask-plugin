/*
 * Copyright 2026 Ritense BV, the Netherlands.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ritense.valtimoplugins.publictask.web.rest

import com.ritense.valtimoplugins.publictask.BaseIntegrationTest
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@AutoConfigureMockMvc
internal class PublicTaskResourceIT : BaseIntegrationTest() {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `GET public-task endpoint is mapped to the controller and does not fall through to static resource handling`() {
        // Regression guard: PublicTaskResource must be registered via PublicTaskAutoConfiguration, not only via
        // component scanning. On a real Valtimo host the plugin package is not scanned, so an unmapped endpoint
        // would fall through to the static resource handler ("No static resource api/v1/public-task").
        // Requesting an unknown task must therefore reach the controller and return its "task not available" body.
        mockMvc
            .perform(get("/api/v1/public-task/{publicTaskId}", UUID.randomUUID().toString()))
            .andExpect(status().isNotFound)
            .andExpect(content().string(containsString("This task does not exist")))
    }

    @Test
    fun `GET public-task endpoint still accepts the public task id as a query parameter`() {
        // Links that were sent to applicants before the id moved into the path must keep working.
        mockMvc
            .perform(get("/api/v1/public-task").queryParam("publicTaskId", UUID.randomUUID().toString()))
            .andExpect(status().isNotFound)
            .andExpect(content().string(containsString("This task does not exist")))
    }
}
