package com.imiyar.removebutterknife.parser

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiStatement

class AdapterCodeParser(
    project: Project,
    psiJavaFile: PsiJavaFile,
    psiClass: PsiClass
) : BaseCodeParser(project, psiJavaFile, psiClass) {

    init {
        findBindViewAnnotation(false)
        findOnClickAnnotation()
    }

    private var resultMethod: PsiMethod? = null
    private var resultStatement: PsiStatement? = null

    override fun findViewInsertAnchor() {
        findMethodByButterKnifeBind()
        val parameterName = findMethodParameterName()
        resultMethod?.let { method ->
            innerBindViewFieldLists.forEach { pair ->
                resultStatement?.let { stmt ->
                    val text = if (parameterName.isNotEmpty())
                        "${pair.first} = $parameterName.findViewById(R.id.${pair.second});"
                    else "${pair.first} = itemView.findViewById(R.id.${pair.second});"
                    addMethodAfterStatement(method, stmt, elementFactory.createStatementFromText(text, psiClass))
                }
            }
        }
    }

    private fun findMethodParameterName(): String {
        var param = ""
        resultMethod?.parameterList?.parameters?.forEach { if (it.type.toString() == "PsiType:View") param = it.name }
        return param
    }

    private fun findMethodByButterKnifeBind() {
        resultMethod = psiClass.methods.firstOrNull { method ->
            method.body?.statements?.any { it.text.trim().contains("ButterKnife.bind(") } == true
        }
        resultStatement = resultMethod?.body?.statements?.firstOrNull { it.text.trim().contains("ButterKnife.bind(") }
    }

    override fun findClickInsertAnchor() {
        val paramName = findMethodParameterName()
        resultMethod?.let { insertOnClickMethod(it, false, if (paramName.isNotEmpty()) paramName else "itemView") }
    }
}
