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

package com.ritense.valtimoplugins.publictask.service

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.ritense.form.service.impl.DefaultFormSubmissionService
import com.ritense.processlink.service.ProcessLinkActivityService
import com.ritense.valtimoplugins.publictask.BaseTest
import com.ritense.valtimoplugins.publictask.domain.PublicTaskEntity
import com.ritense.valtimoplugins.publictask.htmlrenderer.service.HtmlRenderService
import com.ritense.valtimoplugins.publictask.repository.PublicTaskRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.operaton.bpm.engine.RuntimeService
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

internal class PublicTaskServiceTest : BaseTest() {
    private val publicTaskRepository: PublicTaskRepository = mock()
    private val runtimeService: RuntimeService = mock()
    private val processLinkActivityService: ProcessLinkActivityService = mock()
    private val htmlRenderService: HtmlRenderService = mock()
    private val defaultFormSubmissionService: DefaultFormSubmissionService = mock()

    private val publicTaskService =
        PublicTaskService(
            publicTaskRepository = publicTaskRepository,
            runtimeService = runtimeService,
            processLinkActivityService = processLinkActivityService,
            htmlRenderService = htmlRenderService,
            defaultFormSubmissionService = defaultFormSubmissionService,
            baseUrl = "https://valtimo.example.org",
        )

    @Test
    fun `rendering the form is refused once the task has expired`() {
        givenPublicTask(expirationDate = LocalDate.now().minusDays(1))

        val response = publicTaskService.createPublicTaskHtml(PUBLIC_TASK_ID)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        verifyNoInteractions(processLinkActivityService, htmlRenderService)
    }

    @Test
    fun `rendering the form is refused once the task has been completed`() {
        givenPublicTask(completed = true)

        val response = publicTaskService.createPublicTaskHtml(PUBLIC_TASK_ID)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        verifyNoInteractions(processLinkActivityService, htmlRenderService)
    }

    @Test
    fun `rendering the form is refused when the expiration date cannot be read`() {
        givenPublicTask(expirationDate = null)

        val response = publicTaskService.createPublicTaskHtml(PUBLIC_TASK_ID)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        verifyNoInteractions(processLinkActivityService, htmlRenderService)
    }

    @Test
    fun `a task that is open and expires today is still rendered`() {
        givenPublicTask(expirationDate = LocalDate.now())

        // The task is looked up in the process engine, which proves the availability check did not short-circuit.
        // The mocked engine returns no task, so the response itself is still "not available".
        publicTaskService.createPublicTaskHtml(PUBLIC_TASK_ID)

        verify(processLinkActivityService).openTask(USER_TASK_ID)
    }

    @Test
    fun `submitting the form is refused once the task has expired`() {
        givenPublicTask(expirationDate = LocalDate.now().minusDays(1))

        val response = publicTaskService.completeUserTaskWithPublicTaskSubmission(PUBLIC_TASK_ID, SUBMISSION)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        verifyNoInteractions(processLinkActivityService, defaultFormSubmissionService)
    }

    @Test
    fun `submitting the form is refused once the task has been completed`() {
        givenPublicTask(completed = true)

        val response = publicTaskService.completeUserTaskWithPublicTaskSubmission(PUBLIC_TASK_ID, SUBMISSION)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        verifyNoInteractions(processLinkActivityService, defaultFormSubmissionService)
    }

    @Test
    fun `an unknown task is not available`() {
        whenever(publicTaskRepository.findById(PUBLIC_TASK_ID)).thenReturn(Optional.empty())

        val response = publicTaskService.createPublicTaskHtml(PUBLIC_TASK_ID)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        verifyNoInteractions(processLinkActivityService, htmlRenderService)
    }

    private fun givenPublicTask(
        expirationDate: LocalDate? = LocalDate.now().plusDays(1),
        completed: Boolean = false,
    ) {
        whenever(publicTaskRepository.findById(PUBLIC_TASK_ID)).thenReturn(
            Optional.of(
                PublicTaskEntity(
                    publicTaskId = PUBLIC_TASK_ID,
                    userTaskId = USER_TASK_ID,
                    processBusinessKey = "3e6b0dd5-3b4b-4bd4-a1ea-b9f0e4e1c7cb",
                    assigneeCandidateContactData = "citizen@example.org",
                    taskExpirationDate = expirationDate?.toString() ?: "",
                    isCompletedByPublicTask = completed,
                ),
            ),
        )
    }

    companion object {
        private val PUBLIC_TASK_ID = UUID.fromString("3f2a1c4e-0b7d-4a19-9c5e-8d6f0a1b2c3d")

        private val USER_TASK_ID = UUID.fromString("a0d1f5c2-1e3b-4a67-8c9d-0e1f2a3b4c5d")

        private val SUBMISSION = JsonNodeFactory.instance.objectNode().put("naam", "Ruben")
    }
}
