package com.imiyar.removebutterknife

import com.imiyar.removebutterknife.parser.ActionDelegate
import com.imiyar.removebutterknife.utils.Notifier
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil

/**
 * 插件入口
 */
class Entrance(private val event: AnActionEvent) {

    private val project = event.project ?: throw IllegalStateException("Project cannot be null")
    private var javaFileCount = 0
    private var currFileIndex = 0
    private var parsedFileCount = 0
    private var exceptionFileCount = 0

    fun run() {
        val vFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        calJavaFileCount(vFiles)

        if (javaFileCount > 1) {
            showDialogWhenBatch { isContinue ->
                if (isContinue) startHandle(vFiles)
            }
        } else {
            startHandle(vFiles)
        }
    }

    private fun startHandle(vFiles: Array<out VirtualFile>?) {
        if (ArrayUtil.isEmpty(vFiles)) return

        ProgressManager.getInstance().runProcessWithProgressSynchronously({
            val progressIndicator = ProgressManager.getInstance().progressIndicator

            vFiles?.forEachIndexed { index, vFile ->
                progressIndicator.checkCanceled()
                progressIndicator.text2 = "($index/${vFiles.size}) '${vFile.name}'..."
                progressIndicator.fraction = index.toDouble() / vFiles.size

                handle(vFile)
            }

            showResult()
        }, "正在处理Java文件", true, project)
    }

    private fun handle(vFile: VirtualFile) {
        if (vFile.isDirectory) {
            vFile.children.forEach { handle(it) }
        } else if (vFile.fileType is JavaFileType) {
            val psiFile = PsiManager.getInstance(project).findFile(vFile)
            if (psiFile !is PsiJavaFile) return

            val psiClasses = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
            psiClasses.forEach { psiClass ->
                currFileIndex++
                try {
                    writeAction(psiFile) {
                        val parsed = ActionDelegate(project, vFile, psiFile, psiClass).parse()
                        if (parsed) parsedFileCount++
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                    exceptionFileCount++
                    Notifier.notifyError(project, "$currFileIndex. ${vFile.name} 出现异常，处理结束 ×")
                }
            }
        }
    }

    private fun showResult() {
        Notifier.notifyInfo(
            project,
            "RemoveButterKnife处理结果：${currFileIndex}个Java文件, ${parsedFileCount}个完成, ${exceptionFileCount}个异常"
        )
    }

    private fun calJavaFileCount(vFiles: Array<VirtualFile>?) {
        javaFileCount = 0
        if (ArrayUtil.isEmpty(vFiles)) return

        vFiles?.forEach { countJavaFiles(it) }
    }

    private fun countJavaFiles(vFile: VirtualFile) {
        if (vFile.isDirectory) {
            vFile.children.forEach { countJavaFiles(it) }
        } else if (vFile.fileType is JavaFileType) {
            val psiFile = PsiManager.getInstance(project).findFile(vFile)
            val psiClasses = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
            if (psiFile is PsiJavaFile && psiClasses.isNotEmpty()) javaFileCount++
        }
    }

    private fun showDialogWhenBatch(nextAction: (isContinue: Boolean) -> Unit) {
        val dialogBuilder = DialogBuilder()
        dialogBuilder.setErrorText(
            "你正在批量处理Java文件，总数$javaFileCount, 可能需要较长时间，是否继续？"
        )
        dialogBuilder.setOkOperation {
            nextAction(true)
            dialogBuilder.dialogWrapper.close(0)
        }
        dialogBuilder.setCancelOperation {
            nextAction(false)
            dialogBuilder.dialogWrapper.close(0)
        }
        dialogBuilder.showModal(true)
    }

    private fun writeAction(psiJavaFile: PsiFile, commandName: String = "RemoveButterKnifeWriteAction", runnable: Runnable) {
        WriteCommandAction.runWriteCommandAction(project, commandName, "RemoveButterKnifeGroupID", runnable, psiJavaFile)
    }
}
