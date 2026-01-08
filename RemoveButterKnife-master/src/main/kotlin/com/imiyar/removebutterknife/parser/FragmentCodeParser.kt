package com.imiyar.removebutterknife.parser

import com.imiyar.removebutterknife.utils.getLayoutRes
import com.imiyar.removebutterknife.utils.underLineToHump
import com.imiyar.removebutterknife.utils.withViewBinding
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile

class FragmentCodeParser(
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
        val onCreateViewMethod = psiClass.findMethodsByName("onCreateView", false).firstOrNull() ?: return
        var layoutRes = ""
        var bindingName = ""

        onCreateViewMethod.body?.statements?.forEach { statement ->
            if (statement.text.contains("R.layout.")) {
                layoutRes = statement.text.getLayoutRes()
                bindingName = layoutRes.underLineToHump().withViewBinding()
                addBindingField("private $bindingName mBinding;\n")
                addImportStatement(vFile, layoutRes)
            }
        }

        onCreateViewMethod.body?.statements?.forEach { statement ->
            if (statement.text.contains("return inflater.inflate(")) {
                val bindingStatement = elementFactory.createStatementFromText(
                    "mBinding = $bindingName.inflate(${onCreateViewMethod.parameterList.parameters[0].name}, ${onCreateViewMethod.parameterList.parameters[1].name}, false);",
                    psiClass
                )
                val returnStatement = elementFactory.createStatementFromText("return mBinding.getRoot();", psiClass)
                addMethodAfterStatement(onCreateViewMethod, statement, returnStatement)
                changeBindingStatement(onCreateViewMethod, statement, bindingStatement)
            }
        }

        val onViewCreatedMethod = psiClass.findMethodsByName("onViewCreated", false).firstOrNull() ?: return
        onViewCreatedMethod.body?.statements?.forEach { statement ->
            if (statement.text.contains("super.onViewCreated(")) {
                addBindViewListStatement(onViewCreatedMethod, statement)
            }
        }

        psiClass.methods.forEach { method ->
            method.body?.statements?.forEach { statement ->
                changeBindViewStatement(statement)
            }
        }

        psiClass.innerClasses.forEach { inner ->
            inner.methods.forEach { method ->
                method.body?.statements?.forEach { statement ->
                    changeBindViewStatement(statement)
                }
            }
        }
    }

    override fun findClickInsertAnchor() {
        val onViewCreatedMethod = psiClass.findMethodsByName("onViewCreated", false).firstOrNull() ?: return
        insertOnClickMethod(onViewCreatedMethod)
    }
}
