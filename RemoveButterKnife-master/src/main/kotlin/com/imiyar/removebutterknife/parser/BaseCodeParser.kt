package com.imiyar.removebutterknife.parser

import com.google.gson.JsonParser
import com.imiyar.removebutterknife.utils.getAnnotationIds
import com.imiyar.removebutterknife.utils.isOnlyContainsTarget
import com.imiyar.removebutterknife.utils.underLineToHump
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader


open class BaseCodeParser(
    protected val project: Project,
    protected val psiJavaFile: PsiJavaFile,
    protected val psiClass: PsiClass
){

    // 单个字段列表
    private val bindViewFieldLists = mutableListOf<Pair<String, String>>()
    // 多个字段列表（@BindViews）
    private val bindViewListFieldLists = mutableListOf<Triple<String, String, MutableList<String>>>()
    // Listener 方法列表
    private val onClickMethodLists = mutableListOf<Pair<String, String>>()
    private val onLongClickMethodLists = mutableListOf<Pair<String, String>>()
    private val onTouchMethodLists = mutableListOf<Pair<String, String>>()
    // 内部使用 findViewById 初始化
    protected val innerBindViewFieldLists = mutableListOf<Pair<String, String>>()

    protected val elementFactory = JavaPsiFacade.getInstance(project).elementFactory

    // 安全访问 bindViewList
    protected val bindViewListSafe get() = bindViewFieldLists
    protected val bindViewListFieldSafe get() = bindViewListFieldLists

    /** 执行解析和生成 */
    fun execute() {
        findViewInsertAnchor()
        findClickInsertAnchor()
        deleteButterKnifeBindStatement()
    }

    /** 解析 @BindView / @BindViews */
    fun findBindViewAnnotation(isDelete: Boolean = true) {
        psiClass.fields.forEach { field ->
            field.annotations.forEach { annotation ->
                if (annotation.qualifiedName?.contains("BindView") == true) {
                    val valueList = annotation.findAttributeValue("value")?.text?.getAnnotationIds() ?: listOf()
                    if (valueList.size > 1) {
                        bindViewListFieldLists.add(Triple(field.type.toString(), field.name, valueList.toMutableList()))
                        writeAction { annotation.delete() }
                    } else {
                        val id = valueList.firstOrNull() ?: annotation.findAttributeValue("value")?.lastChild?.text.toString()
                        if (isDelete) bindViewFieldLists.add(Pair(field.name, id))
                        else innerBindViewFieldLists.add(Pair(field.name, id))
                        writeAction {
                            if (isDelete) field.delete() else annotation.delete()
                        }
                    }
                }
            }
        }
    }

    /** 解析 @OnClick / @OnLongClick / @OnTouch */
    fun findOnClickAnnotation() {
        psiClass.methods.forEach { method ->
            method.annotations.forEach { annotation ->
                val name = annotation.qualifiedName ?: return@forEach
                if (name.contains("OnClick") || name.contains("OnLongClick") || name.contains("OnTouch")) {
                    val ids = annotation.findAttributeValue("value")?.text?.getAnnotationIds() ?: listOf()
                    ids.forEach { id ->
                        val methodCall = buildMethodCallString(method)
                        when {
                            name.contains("OnClick") -> onClickMethodLists.add(Pair(id, methodCall))
                            name.contains("OnLongClick") -> onLongClickMethodLists.add(Pair(id, methodCall))
                            name.contains("OnTouch") -> onTouchMethodLists.add(Pair(id, methodCall))
                        }
                    }
                    writeAction { annotation.delete() }
                }
            }
        }
    }

    /** 拼接方法调用字符串 */
    private fun buildMethodCallString(method: PsiMethod): String {
        var str = "${method.name}("
        method.parameterList.parameters.forEachIndexed { i, p ->
            str += when {
                p.type.toString() == "PsiType:View" -> "view"
                p.type.toString() == "PsiType:MotionEvent" -> "event"
                else -> p.name
            }
            if (i != method.parameterList.parameters.size - 1) str += ", "
        }
        str += ")"
        return str
    }

    /** 添加 mBinding 字段 */
    protected fun addBindingField(fieldStr: String) {
        psiClass.addAfter(elementFactory.createFieldFromText(fieldStr, psiClass), psiClass.allFields.last())
    }

    /** 修改 mBinding 的初始化语句 */
    protected fun changeBindingStatement(method: PsiMethod, beforeStatement: PsiStatement, afterStatement: PsiStatement) {
        writeAction {
            method.addAfter(afterStatement, beforeStatement)
            beforeStatement.delete()
        }
    }

    /** 在方法中添加语句在目标语句后 */
    protected fun addMethodAfterStatement(method: PsiMethod, statement: PsiStatement, newStatement: PsiStatement) {
        writeAction { method.addAfter(newStatement, statement) }
    }

    /** 在方法中添加语句在目标语句前 */
    protected fun addMethodBeforeStatement(method: PsiMethod, statement: PsiStatement, newStatement: PsiStatement) {
        writeAction { method.addBefore(newStatement, statement) }
    }

    /** 添加 import */
    protected open fun addImportStatement(vFile: VirtualFile, layoutRes: String) {
        val importList = psiJavaFile.importList
        val importStatement = elementFactory.createImportStatementOnDemand(getBindingJsonFile(vFile, layoutRes))
        writeAction { importList?.add(importStatement) }
    }

    /** 安全处理 bindViewList 不存在 */
    protected fun addBindViewListStatement(psiMethod: PsiMethod, psiStatement: PsiStatement) {
        if (bindViewListFieldSafe.isEmpty()) return
        bindViewListFieldSafe.forEach { triple ->
            writeAction {
                val typeName = triple.first.removePrefix("PsiType:")
                val initStatement = if (triple.first.contains("PsiType:List")) {
                    "${triple.second} = new ArrayList<>();"
                } else {
                    "${triple.second} = new $typeName[${triple.third.size}];"
                }
                psiMethod.addAfter(elementFactory.createStatementFromText(initStatement, psiClass), psiStatement)
                psiMethod.body?.statements?.forEach { statement ->
                    if (statement.text.trim() == initStatement) {
                        triple.third.asReversed().forEachIndexed { idx, name ->
                            val assignStatement = if (triple.first.contains("PsiType:List")) {
                                "${triple.second}.add(mBinding.${name.underLineToHump()});"
                            } else {
                                "${triple.second}[${triple.third.size - 1 - idx}] = mBinding.${name.underLineToHump()};"
                            }
                            psiClass.addAfter(elementFactory.createStatementFromText(assignStatement, psiClass), statement)
                        }
                    }
                }
            }
        }
    }

    /** 修改 @BindView 字段为 mBinding.xxx */
    protected fun changeBindViewStatement(psiStatement: PsiStatement) {
        var replaceText = psiStatement.text.trim()
        bindViewListSafe.forEachIndexed { index, pair ->
            if (replaceText.isOnlyContainsTarget(pair.first) && !replaceText.isOnlyContainsTarget("R.id.${pair.first}")) {
                replaceText = replaceText.replace("\\b${pair.first}\\b".toRegex(), "mBinding.${pair.second.underLineToHump()}")
            }
            if (index == bindViewListSafe.size - 1 && replaceText != psiStatement.text.trim()) {
                val replaceStatement = elementFactory.createStatementFromText(replaceText, psiClass)
                writeAction {
                    psiStatement.addAfter(replaceStatement, psiStatement)
                    psiStatement.delete()
                }
            }
        }
    }

    /** 插入 initView 方法 */
    protected fun insertInitViewMethod(psiMethod: PsiMethod, afterStatement: PsiStatement) {
        val psiMethods = psiClass.findMethodsByName("initView", false)
        if (psiMethods.isEmpty()) {
            val createMethod = elementFactory.createMethodFromText("private void initView() {}\n", psiClass)
            val callStatement = elementFactory.createStatementFromText("initView();", psiClass)
            writeAction {
                psiMethod.addAfter(callStatement, afterStatement)
                psiClass.addAfter(createMethod, psiMethod)
                val initViewMethod = psiClass.findMethodsByName("initView", false)[0]
                innerBindViewFieldLists.forEach {
                    initViewMethod.lastChild.add(
                        elementFactory.createStatementFromText("${it.first} = findViewById(R.id.${it.second});", psiClass)
                    )
                }
            }
        }
    }

    /** 插入 initListener 方法 */
    protected fun insertOnClickMethod(psiMethod: PsiMethod, isVB: Boolean = true, parameterName: String = "") {
        val psiMethods = psiClass.findMethodsByName("initListener", false)
        if (psiMethods.isEmpty() && (onClickMethodLists.isNotEmpty() || onLongClickMethodLists.isNotEmpty() || onTouchMethodLists.isNotEmpty())) {
            val createMethod = elementFactory.createMethodFromText("private void initListener() {}\n", psiClass)
            val psiStatement = elementFactory.createStatementFromText("initListener();", psiClass)
            writeAction {
                psiMethod.addAfter(psiStatement, psiMethod.body?.statements?.last())
                psiClass.addAfter(createMethod, psiMethod)
                val listenerMethod = psiClass.findMethodsByName("initListener", false)[0]
                if (isVB) insertOnClickStatementByVB(listenerMethod)
                else insertOnClickStatementByFVB(listenerMethod, parameterName)
            }
        }
    }

    private fun insertOnClickStatementByVB(psiMethod: PsiMethod) {
        onClickMethodLists.forEach { psiMethod.lastChild.add(elementFactory.createStatementFromText(getOnClickStatement(it), psiClass)) }
        onLongClickMethodLists.forEach { psiMethod.lastChild.add(elementFactory.createStatementFromText(getOnLongClickStatement(it), psiClass)) }
        onTouchMethodLists.forEach { psiMethod.lastChild.add(elementFactory.createStatementFromText(getOnTouchStatement(it), psiClass)) }
    }

    protected fun insertOnClickStatementByFVB(psiMethod: PsiMethod, parameterName: String) {
        onClickMethodLists.forEach { psiMethod.lastChild.add(elementFactory.createStatementFromText(getOnClickStatement(it, parameterName), psiClass)) }
        onLongClickMethodLists.forEach { psiMethod.lastChild.add(elementFactory.createStatementFromText(getOnLongClickStatement(it, parameterName), psiClass)) }
        onTouchMethodLists.forEach { psiMethod.lastChild.add(elementFactory.createStatementFromText(getOnTouchStatement(it, parameterName), psiClass)) }
    }

    private fun getOnClickStatement(pair: Pair<String, String>, parameterName: String = "") =
        if (parameterName.isNotEmpty()) "${if (parameterName == "null") "findViewById" else "$parameterName.findViewById"}(R.id.${pair.first}).setOnClickListener(view -> ${pair.second});"
        else "mBinding.${pair.first.underLineToHump()}.setOnClickListener(view -> ${pair.second});"

    private fun getOnLongClickStatement(pair: Pair<String, String>, parameterName: String = "") =
        if (parameterName.isNotEmpty()) "${if (parameterName == "null") "findViewById" else "$parameterName.findViewById"}(R.id.${pair.first}).setOnLongClickListener(view -> {${pair.second}; return false;});"
        else "mBinding.${pair.first.underLineToHump()}.setOnLongClickListener(view -> {${pair.second}; return false;});"

    private fun getOnTouchStatement(pair: Pair<String, String>, parameterName: String = "") =
        if (parameterName.isNotEmpty()) "${if (parameterName == "null") "findViewById" else "$parameterName.findViewById"}(R.id.${pair.first}).setOnTouchListener((view, event) -> {${pair.second}; return false;});"
        else "mBinding.${pair.first.underLineToHump()}.setOnTouchListener((view, event) -> {${pair.second}; return false;});"

    /** 删除 ButterKnife import 与绑定语句 */
    private fun deleteButterKnifeBindStatement() {
        writeAction {
            psiJavaFile.importList?.importStatements?.forEach {
                if (it.qualifiedName?.contains("butterknife", true) == true) it.delete()
            }
            psiClass.methods.forEach { method ->
                method.body?.statements?.forEach { statement ->
                    if (statement.text.trim().contains("ButterKnife.bind(")) statement.delete()
                }
            }
            psiClass.fields.find { it.type.canonicalText.contains("Unbinder") }?.let { unBinderField ->
                psiClass.methods.forEach { method ->
                    method.body?.statements?.forEach { statement ->
                        if (statement.firstChild.text.trim().contains(unBinderField.name)) statement.delete()
                    }
                }
                unBinderField.delete()
            }
        }
    }

    /** 写操作 */
    private fun writeAction(commandName: String = "RemoveButterKnifeWriteAction", runnable: Runnable) {
        WriteCommandAction.runWriteCommandAction(project, commandName, "RemoveButterKnifeGroupID", runnable, psiJavaFile)
    }

    /** 获取 viewBinding JSON 文件的包路径 */
    private fun getBindingJsonFile(vFile: VirtualFile, layoutRes: String): String {
        val listFilesName = mutableListOf<String>()
        var fileName = vFile.parent
        while (!fileName.toString().endsWith("src")) fileName = fileName.parent

        var bindImportClass = ""
        var moduleFile = File("${fileName.parent.path}/build/intermediates/data_binding_base_class_log_artifact/debug/out/")
        if (moduleFile.isDirectory && moduleFile.listFiles()?.isNotEmpty() == true) moduleFile = moduleFile.listFiles()[0]
        if (moduleFile.isFile) {
            val jsonObject = JsonParser.parseString(readJsonFile(moduleFile)).asJsonObject
            if (jsonObject.get("mappings").asJsonObject.get(layoutRes) != null)
                bindImportClass = jsonObject.get("mappings").asJsonObject.get(layoutRes).asJsonObject.get("module_package").asString
        }

        // 遍历所有 module 查找 JSON
        if (bindImportClass.isEmpty()) {
            val dataBindingDir = "${fileName.parent.parent.path}${File.separator}"
            File(dataBindingDir).listFiles()?.filter { it.isDirectory && !it.isHidden }?.forEach { dir ->
                val jsonFilePath = "$dir/build/intermediates/data_binding_base_class_log_artifact/debug/out/"
                var jsonFile = File(jsonFilePath)
                if (jsonFile.isDirectory && jsonFile.listFiles()?.isNotEmpty() == true) jsonFile = jsonFile.listFiles()[0]
                if (jsonFile.isFile) {
                    val jsonObject = JsonParser.parseString(readJsonFile(jsonFile)).asJsonObject
                    if (jsonObject.get("mappings").asJsonObject.get(layoutRes) != null)
                        bindImportClass = jsonObject.get("mappings").asJsonObject.get(layoutRes).asJsonObject.get("module_package").asString
                }
                if (bindImportClass.isNotEmpty()) return@forEach
            }
        }
        return bindImportClass
    }

    /** 读取 JSON 文件 */
    private fun readJsonFile(jsonFile: File): String {
        val sb = StringBuffer()
        BufferedReader(InputStreamReader(FileInputStream(jsonFile), "UTF-16BE")).useLines { lines ->
            lines.forEach { sb.append(it) }
        }
        return sb.toString()
    }

    // 寻找viewBinding插入的锚点
    open fun findViewInsertAnchor() {}

    // 寻找clickListener插入的锚点
    open fun findClickInsertAnchor() {}

}