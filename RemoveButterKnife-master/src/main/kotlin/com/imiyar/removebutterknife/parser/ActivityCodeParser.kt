package com.imiyar.removebutterknife.parser

import com.imiyar.removebutterknife.utils.getLayoutRes
import com.imiyar.removebutterknife.utils.underLineToHump
import com.imiyar.removebutterknife.utils.withViewBinding
import com.intellij.openapi.application.ReadResult.Companion.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile

class ActivityCodeParser(
    project: Project,
    private val vFile: VirtualFile,
    psiJavaFile: PsiJavaFile,
    psiClass: PsiClass
) : BaseCodeParser(project, psiJavaFile, psiClass) {

    init {
        findBindViewAnnotation()
        findOnClickAnnotation()
    }

    override fun findViewInsertAnchor() {
        val onCreateMethod = psiClass.findMethodsByName("onCreate", false).firstOrNull() ?: return
        val firstRLayoutStatement = onCreateMethod.body?.statements?.firstOrNull { it.text.contains("R.layout.") } ?: return

        val layoutRes = firstRLayoutStatement.text.getLayoutRes()
        val bindingClassName = layoutRes.underLineToHump().withViewBinding()

        // 获取正确包名
        val packageName = psiJavaFile.packageName
        val fullImport = "$packageName.databinding.$bindingClassName"

        // 添加 import
        addImportStatement(fullImport)

        // 声明 mBinding
        addBindingField("private $bindingClassName mBinding;\n")

        // 初始化 mBinding
        val initStatement = elementFactory.createStatementFromText(
            "mBinding = $bindingClassName.inflate(getLayoutInflater());",
            psiClass
        )
        addMethodBeforeStatement(onCreateMethod, firstRLayoutStatement, initStatement)

        // 替换 setContentView
        val afterStatement = elementFactory.createStatementFromText(
            "setContentView(mBinding.getRoot());",
            psiClass
        )
        changeBindingStatement(onCreateMethod, firstRLayoutStatement, afterStatement)

        // 初始化 bindViewList
        addBindViewListStatement(onCreateMethod, initStatement)

        // 替换原 ButterKnife 访问为 mBinding.xxx
        psiClass.methods.forEach { method ->
            method.body?.statements?.forEach { changeBindViewStatement(it) }
        }
        psiClass.innerClasses.forEach { inner ->
            inner.methods.forEach { method ->
                method.body?.statements?.forEach { changeBindViewStatement(it) }
            }
        }
    }

    override fun findClickInsertAnchor() {
        val onCreateMethod = psiClass.findMethodsByName("onCreate", false).firstOrNull() ?: return
        insertOnClickMethod(onCreateMethod)
    }

    /**
     * 正确生成 import 语句
     */
    private fun addImportStatement(fullClassName: String) {
        val importList = psiJavaFile.importList ?: return
        val bindingClass = JavaPsiFacade.getInstance(project).findClass(fullClassName, psiJavaFile.resolveScope)
        bindingClass?.let {
            val importStatement = elementFactory.createImportStatement(it)
            writeAction { importList.add(importStatement) }
        }
    }
}
