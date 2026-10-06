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

import com.ritense.valtimoplugins.publictask.BaseIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.operaton.bpm.engine.RepositoryService
import org.operaton.bpm.engine.RuntimeService
import org.operaton.bpm.engine.TaskService
import org.operaton.bpm.engine.runtime.ProcessInstance
import org.operaton.bpm.model.bpmn.Bpmn
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

internal class PublicTaskServiceIT : BaseIntegrationTest() {
    @Autowired
    lateinit var repositoryService: RepositoryService

    @Autowired
    lateinit var runtimeService: RuntimeService

    @Autowired
    lateinit var taskService: TaskService

    private val caseDefinitionTag = "CD:case-${UUID.randomUUID()}:1.0.0"
    private val buildingBlockTag = "BB:building-block-${UUID.randomUUID()}:1.0.0"

    @Test
    fun `a public task in a case definition starts the URL process of that case definition`() {
        deployUrlProcess(caseDefinitionTag)
        deployUrlProcess(buildingBlockTag)

        val publicTask = startPublicTask(caseDefinitionTag)

        assertThat(versionTagOf(urlProcessStartedBy(publicTask))).isEqualTo(caseDefinitionTag)
    }

    @Test
    fun `a public task in a building block starts the URL process of that building block`() {
        deployUrlProcess(buildingBlockTag)
        deployUrlProcess(caseDefinitionTag)

        val publicTask = startPublicTask(buildingBlockTag)

        assertThat(versionTagOf(urlProcessStartedBy(publicTask))).isEqualTo(buildingBlockTag)
    }

    @Test
    fun `the URL process knows which task it is for and belongs to the same case`() {
        deployUrlProcess(caseDefinitionTag)
        deployUrlProcess(buildingBlockTag)

        val publicTask = startPublicTask(caseDefinitionTag)

        val urlProcess = urlProcessStartedBy(publicTask)
        val userTaskId =
            taskService
                .createTaskQuery()
                .processInstanceId(publicTask.id)
                .singleResult()
                .id
        assertThat(urlProcess.businessKey).isEqualTo(publicTask.businessKey)
        assertThat(runtimeService.getVariable(urlProcess.id, "userTaskId")).isEqualTo(userTaskId)
    }

    @Test
    fun `a public task on an earlier copy of a redeployed process still starts the URL process of its case`() {
        deployUrlProcess(caseDefinitionTag)
        deployUrlProcess(buildingBlockTag)

        val publicTask = startPublicTask("DETACHED:$caseDefinitionTag")

        assertThat(versionTagOf(urlProcessStartedBy(publicTask))).isEqualTo(caseDefinitionTag)
    }

    @Test
    fun `a public task whose case definition has no URL process of its own starts the last deployed one`() {
        deployUrlProcess(caseDefinitionTag)
        deployUrlProcess(buildingBlockTag)

        val publicTask = startPublicTask("CD:case-without-url-process-${UUID.randomUUID()}:1.0.0")

        assertThat(versionTagOf(urlProcessStartedBy(publicTask))).isEqualTo(buildingBlockTag)
    }

    @Test
    fun `a public task outside any case definition or building block starts the last deployed URL process`() {
        deployUrlProcess(buildingBlockTag)
        deployUrlProcess(caseDefinitionTag)

        val publicTask = startPublicTask(versionTag = null)

        assertThat(versionTagOf(urlProcessStartedBy(publicTask))).isEqualTo(caseDefinitionTag)
    }

    @Test
    fun `a process waiting on the message itself still receives it next to the URL process of its case`() {
        deployUrlProcess(caseDefinitionTag)
        deployUrlProcess(buildingBlockTag)

        val publicTask = startPublicTask(caseDefinitionTag, withEventSubProcess = true)

        assertThat(versionTagOf(urlProcessStartedBy(publicTask))).isEqualTo(caseDefinitionTag)
        assertThat(
            taskService
                .createTaskQuery()
                .processInstanceId(
                    publicTask.id,
                ).taskDefinitionKey(EVENT_SUBPROCESS_TASK)
                .count(),
        ).isEqualTo(1)
    }

    private fun deployUrlProcess(versionTag: String) {
        val model =
            Bpmn
                .createExecutableProcess(URL_PROCESS_KEY)
                .operatonVersionTag(versionTag)
                .startEvent()
                .message("startNotifyAssigneeMessage")
                .userTask("ut-communicate-public-task-url")
                .endEvent()
                .done()
        repositoryService
            .createDeployment()
            .addModelInstance("$URL_PROCESS_KEY.bpmn", model)
            .deploy()
    }

    private fun startPublicTask(
        versionTag: String?,
        withEventSubProcess: Boolean = false,
    ): ProcessInstance {
        val key = "public-task-it-${UUID.randomUUID()}"
        val builder = Bpmn.createExecutableProcess(key)
        if (versionTag != null) {
            builder.operatonVersionTag(versionTag)
        }
        // A wait state first: the subscription of an event subprocess is only visible once it is stored.
        val start = builder.startEvent()
        val beforePublicTask = if (withEventSubProcess) start.userTask(TASK_BEFORE_PUBLIC_TASK) else start
        beforePublicTask
            .userTask("ut-public-task")
            .operatonTaskListenerExpression("create", "\${publicTaskService.startNotifyAssigneeCandidateProcess(task)}")
            .endEvent()
        if (withEventSubProcess) {
            builder
                .eventSubProcess()
                .startEvent()
                .message("startNotifyAssigneeMessage")
                .interrupting(false)
                .userTask(EVENT_SUBPROCESS_TASK)
                .endEvent()
        }
        val model = builder.done()
        val processDefinition =
            repositoryService
                .createDeployment()
                .addModelInstance("$key.bpmn", model)
                .deployWithResult()
                .deployedProcessDefinitions
                .single()
        val processInstance =
            runtimeService.startProcessInstanceById(
                processDefinition.id,
                UUID.randomUUID().toString(),
            )
        if (withEventSubProcess) {
            taskService.complete(
                taskService
                    .createTaskQuery()
                    .processInstanceId(processInstance.id)
                    .taskDefinitionKey(TASK_BEFORE_PUBLIC_TASK)
                    .singleResult()
                    .id,
            )
        }
        return processInstance
    }

    private fun urlProcessStartedBy(publicTask: ProcessInstance): ProcessInstance =
        runtimeService
            .createProcessInstanceQuery()
            .processDefinitionKey(URL_PROCESS_KEY)
            .processInstanceBusinessKey(publicTask.businessKey)
            .singleResult()

    private fun versionTagOf(processInstance: ProcessInstance): String? =
        repositoryService.getProcessDefinition(processInstance.processDefinitionId).versionTag

    companion object {
        private const val URL_PROCESS_KEY = "create-public-task-url-it"
        private const val EVENT_SUBPROCESS_TASK = "ut-in-event-subprocess"
        private const val TASK_BEFORE_PUBLIC_TASK = "ut-before-public-task"
    }
}
