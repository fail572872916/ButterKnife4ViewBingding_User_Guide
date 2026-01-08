package com.imiyar.removebutterknife.parser

import com.imiyar.removebutterknife.utils.getLayoutRes
import com.imiyar.removebutterknife.utils.underLineToHump
import com.imiyar.removebutterknife.utils.withViewBinding
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod

class CustomViewCodeParser(
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
        val layoutMethod = findLayoutMethod() ?: return
        layoutMethod.body?.statements?.forEach { stmt ->
            if (stmt.text.contains("R.layout")) {
                val layoutRes = stmt.text.trim().getLayoutRes()
                val bindingName = layoutRes.underLineToHump().withViewBinding()
                addBindingField("private $bindingName mBinding;\n")
                addImportStatement(vFile, layoutRes)
            }
        }

        val butterKnifeMethod = findButterKnifeBindMethod()
        butterKnifeMethod?.body?.statements?.forEach { stmt ->
            if (stmt.text.contains("ButterKnife.bind(")) addBindViewListStatement(butterKnifeMethod, stmt)
        }

        psiClass.methods.forEach { method -> method.body?.statements?.forEach { changeBindViewStatement(it) } }
        psiClass.innerClasses.forEach { inner -> inner.methods.forEach { m -> m.body?.statements?.forEach { changeBindViewStatement(it) } } }
    }

    private fun findLayoutMethod(): PsiMethod? {
        return psiClass.methods.firstOrNull { m -> m.body?.statements?.any { it.text.contains("R.layout") } == true }
    }

    private fun findButterKnifeBindMethod(): PsiMethod? {
        return psiClass.methods.firstOrNull { m -> m.body?.statements?.any { it.text.contains("ButterKnife.bind(") } == true }
    }

    override fun findClickInsertAnchor() {
        findLayoutMethod()?.let { insertOnClickMethod(it) }
    }
}
