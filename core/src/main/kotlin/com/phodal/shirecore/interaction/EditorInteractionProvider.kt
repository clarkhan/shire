package com.phodal.shirecore.interaction

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.phodal.shirecore.ShireCoroutineScope
import com.phodal.shirecore.ShirelangNotifications
import com.phodal.shirecore.agent.InteractionType
import com.phodal.shirecore.interaction.dto.CodeCompletionRequest
import com.phodal.shirecore.interaction.task.BasicChatCompletionTask
import com.phodal.shirecore.interaction.task.FileGenerateTask
import com.phodal.shirecore.llm.LlmProvider
import com.phodal.shirecore.provider.ide.LocationInteractionContext
import com.phodal.shirecore.provider.ide.LocationInteractionProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch

class EditorInteractionProvider : LocationInteractionProvider {
    override fun isApplicable(context: LocationInteractionContext): Boolean {
        return true
    }

    override fun execute(context: LocationInteractionContext, postExecute: PostFunction) {
        val targetFile = context.editor?.virtualFile

        when (context.interactionType) {
            InteractionType.AppendCursor,
            InteractionType.AppendCursorStream,
            -> {
                val task = createTask(context, context.prompt, isReplacement = false, postExecute = postExecute, false)

                if (task == null) {
                    ShirelangNotifications.error(context.project, "Failed to create code completion task.")
                    postExecute.invoke("", null)
                    return
                }

                ProgressManager.getInstance()
                    .runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
            }

            InteractionType.OutputFile -> {
                val fileName = targetFile?.name
                val task = FileGenerateTask(context.project, context.prompt, fileName, postExecute = postExecute)
                ProgressManager.getInstance()
                    .runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
            }

            InteractionType.ReplaceSelection -> {
                val task = createTask(context, context.prompt, true, postExecute, false)

                if (task == null) {
                    ShirelangNotifications.error(context.project, "Failed to create code completion task.")
                    postExecute.invoke("", null)
                    return
                }

                ProgressManager.getInstance()
                    .runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
            }

            InteractionType.ReplaceCurrentFile -> {
                val fileName = targetFile?.name
                val task = FileGenerateTask(context.project, context.prompt, fileName, postExecute = postExecute)

                ProgressManager.getInstance()
                    .runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
            }

            InteractionType.InsertBeforeSelection -> {
                val task = createTask(context, context.prompt, false, postExecute, isInsertBefore = true)

                if (task == null) {
                    ShirelangNotifications.error(context.project, "Failed to create code completion task.")
                    postExecute.invoke("", null)
                    return
                }

                ProgressManager.getInstance()
                    .runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
            }

            InteractionType.RunPanel -> {
                val flow: Flow<String> = LlmProvider.provider(context.project)!!.stream(context.prompt, "", false)
                ShireCoroutineScope.scope(context.project).launch {
                    val suggestion = StringBuilder()

                    flow.cancellable().collect { char ->
                        suggestion.append(char)

                        invokeLater {
                            context.console.print(char, ConsoleViewContentType.NORMAL_OUTPUT)
                        }
                    }

                    postExecute.invoke(suggestion.toString(), null)
                }
            }
        }
    }

    private fun createTask(
        context: LocationInteractionContext,
        userPrompt: String,
        isReplacement: Boolean,
        postExecute: PostFunction,
        isInsertBefore: Boolean,
    ): BasicChatCompletionTask? {
        if (context.editor == null) {
            ShirelangNotifications.error(context.project, "Editor is null, please open a file to continue.")
            return null
        }

        val editor = context.editor

        val offset = if (isInsertBefore) {
            context.editor.selectionModel.selectionStart
        } else {
            editor.caretModel.offset
        }

        val request = runReadAction {
            CodeCompletionRequest.create(
                editor,
                offset,
                userPrompt = userPrompt,
                isReplacement = isReplacement,
                postExecute = postExecute,
                isInsertBefore = isInsertBefore,
            )
        } ?: return null

        val task = BasicChatCompletionTask(request)
        return task
    }
}