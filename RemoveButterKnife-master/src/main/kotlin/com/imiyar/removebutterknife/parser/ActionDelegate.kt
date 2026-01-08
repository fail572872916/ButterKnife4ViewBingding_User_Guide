package com.imiyar.removebutterknife.parser

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import java.util.*

/**
 * ActionDelegate：处理单个 PsiClass
 */
class ActionDelegate(
    private val project: Project,
    private val vFile: VirtualFile,
    private val psiJavaFile: PsiJavaFile,
    private val psiClass: PsiClass
) {

    fun parse(): Boolean {
        if (!checkIsNeedModify()) return false

        processClass(psiClass)
        // 内部类也处理
        psiClass.innerClasses.forEach { processClass(it) }

        return true
    }

    private fun processClass(psiClass: PsiClass) {
        val scope = GlobalSearchScope.allScope(project)

        when {
            psiClass.isExtends("android.app.Activity", scope)
                    || psiClass.isExtends("androidx.appcompat.app.AppCompatActivity", scope)
                    || psiClass.isExtends("androidx.activity.ComponentActivity", scope) -> {
                // ActivityCodeParser 支持 onCreate + initLayout/initView
                ActivityCodeParser(project, vFile, psiJavaFile, psiClass).execute()
            }

            psiClass.isExtends("androidx.fragment.app.Fragment", scope)
                    || psiClass.isExtends("android.app.Fragment", scope) -> {
                if (!psiClass.isExtends("androidx.recyclerview.widget.RecyclerView.Adapter", scope)) {
                    FragmentCodeParser(project, vFile, psiJavaFile, psiClass).execute()
                }
            }

            psiClass.isExtends("androidx.recyclerview.widget.RecyclerView.ViewHolder", scope)
                    || psiClass.isExtends("androidx.recyclerview.widget.RecyclerView.Adapter", scope) -> {
                AdapterCodeParser(project, psiJavaFile, psiClass).execute()
            }

            psiClass.isExtends("android.app.Dialog", scope) -> {
                DialogCodeParser(project, psiJavaFile, psiClass).execute()
            }

            else -> {
                CustomViewCodeParser(project, vFile, psiJavaFile, psiClass).execute()
            }
        }
    }

    /**
     * 判断是否有 @BindView / @OnClick 注解
     */
    private fun checkIsNeedModify(): Boolean {
        val annotations = PsiTreeUtil.findChildrenOfType(psiClass, PsiAnnotation::class.java)
        return annotations.any { it.qualifiedName?.lowercase(Locale.getDefault())?.contains("butterknife") == true }
    }

    /**
     * PsiClass 是否继承指定全类名
     */
    private fun PsiClass.isExtends(fqn: String, scope: GlobalSearchScope): Boolean {
        val baseClass = JavaPsiFacade.getInstance(project).findClass(fqn, scope)
        return baseClass != null && this.isInheritor(baseClass, true)
    }
}
