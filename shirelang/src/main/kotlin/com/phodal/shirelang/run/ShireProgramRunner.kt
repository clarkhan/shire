package com.phodal.shirelang.run

import com.intellij.execution.ExecutionResult
import com.phodal.shirelang.run.flow.ShireProcessProcessor
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.atomic.AtomicReference

class ShireProgramRunner : GenericProgramRunner<RunnerSettings>(), Disposable {
    private val RUNNER_ID: String = "ShireProgramRunner"

    private val connection = ApplicationManager.getApplication().messageBus.connect(this)

    private var isSubscribed = false

    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile) = profile is ShireConfiguration

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        if (environment.runProfile !is ShireConfiguration) return null
        val shireState = state as ShireRunConfigurationProfileState

//        FileDocumentManager.getInstance().saveAllDocuments()
        var executeResult: ExecutionResult? = null

        val result = AtomicReference<RunContentDescriptor>()

        if (!isSubscribed) {
            connection.subscribe(ShireRunListener.TOPIC, object : ShireRunListener {
                override fun runFinish(string: String, event: ProcessEvent, scriptPath: String) {
                    val consoleView = (environment.state as? ShireRunConfigurationProfileState)?.console
                    environment.project.getService(ShireProcessProcessor::class.java)
                        .process(string, event, scriptPath, consoleView)

                    // if need, maybe we need a toggle to show the run content
                }
            })

            isSubscribed = true
        }

        ApplicationManager.getApplication().invokeAndWait {
            executeResult = shireState.execute(environment.executor, this)

            if (shireState.isShowRunContent) {
                result.set(showRunContent(executeResult, environment))
            }
        }

        return result.get()
    }

    override fun dispose() {
        connection.dispose()
    }
}
