package com.example.folderonlymanager

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {
    private lateinit var content: LinearLayout
    private lateinit var pathLabel: TextView
    private var rootUri: Uri? = null
    private var current: DocumentFile? = null
    private val prefs by lazy { getSharedPreferences("folder_manager", MODE_PRIVATE) }

    private val pickTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) {}
            rootUri = uri
            prefs.edit().putString("tree_uri", uri.toString()).apply()
            current = DocumentFile.fromTreeUri(this, uri)
            render()
        }
    }

    private val createZip = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val folder = current
            if (folder == null) return@registerForActivityResult
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                        addFolderToZip(folder, folder.name?.ifBlank { "folder" } ?: "folder", zip)
                    }
                }
                toast("ZIP 压缩包已保存")
            } catch (e: Exception) { toast("压缩失败：${e.localizedMessage}") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        prefs.getString("tree_uri", null)?.let {
            try {
                rootUri = Uri.parse(it)
                current = DocumentFile.fromTreeUri(this, rootUri!!)
            } catch (_: Exception) { rootUri = null }
        }
        render()
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 24, 18, 16)
            setBackgroundColor(0xFF0D0F18.toInt())
        }
        val title = TextView(this).apply {
            text = "FOLDER ONLY  PRO"
            textSize = 22f
            setTextColor(0xFF9C91FF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        page.addView(title)
        page.addView(TextView(this).apply {
            text = "文件夹管理 · 创建 · 重命名 · 删除 · ZIP 导出"
            textSize = 12f
            setTextColor(0xFFB9B9C8.toInt())
            setPadding(0, 5, 0, 14)
        })
        val choose = Button(this).apply {
            text = "选择 / 更换管理目录"
            setOnClickListener { pickTree.launch(null) }
        }
        page.addView(choose)
        pathLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFB6B4D0.toInt())
            setPadding(2, 12, 2, 8)
        }
        page.addView(pathLabel)
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        toolbar.addView(toolButton("新建文件夹") { createFolder() }, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(toolButton("压缩当前目录") { compressCurrent() }, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(toolbar)
        val second = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        second.addView(toolButton("返回根目录") {
            rootUri?.let { current = DocumentFile.fromTreeUri(this@MainActivity, it) }
            render()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        second.addView(toolButton("刷新") { render() }, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(second)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 0) }
        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(TextView(this).apply {
            text = "提示：本应用只在自身界面中限制为文件夹操作。Android 普通 APK 无法接管或强制改变其他应用的上传选择器。"
            textSize = 11f
            setTextColor(0xFF8D8FA2.toInt())
            setPadding(2, 10, 2, 0)
        })
        setContentView(page)
    }

    private fun toolButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        setOnClickListener { action() }
    }

    private fun render() {
        content.removeAllViews()
        val folder = current
        if (folder == null || !folder.isDirectory) {
            pathLabel.text = "尚未授权目录"
            info("请点击“选择 / 更换管理目录”，在系统界面中选择一个文件夹并授予访问权限。")
            return
        }
        pathLabel.text = "当前：${folder.name ?: "已授权目录"}"
        val children = try {
            folder.listFiles().sortedWith(compareBy<DocumentFile>({ !it.isDirectory }, { it.name?.lowercase() ?: "" }))
        } catch (_: Exception) { emptyList() }

        if (folder.uri != rootUri) {
            val up = Button(this).apply {
                text = "⬆ 返回上一级"
                setOnClickListener {
                    val parent = folder.parentFile
                    if (parent != null && parent.isDirectory) current = parent
                    else rootUri?.let { current = DocumentFile.fromTreeUri(this@MainActivity, it) }
                    render()
                }
            }
            content.addView(up)
        }
        if (children.isEmpty()) info("此文件夹为空。")
        children.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 10, 12, 8)
                setBackgroundColor(0xFF1A1D2B.toInt())
            }
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val name = TextView(this).apply {
                text = (if (item.isDirectory) "📁  " else "📄  ") + (item.name ?: "未命名")
                textSize = 15f
                setTextColor(0xFFF0F0FA.toInt())
                setPadding(2, 4, 4, 4)
            }
            line.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            if (item.isDirectory) {
                line.addView(smallButton("打开") { current = item; render() })
                line.addView(smallButton("⋮") { showFolderActions(item) })
            }
            card.addView(line)
            card.addView(TextView(this).apply {
                text = if (item.isDirectory) "文件夹" else "文件（仅显示，不提供单文件选择）"
                textSize = 11f
                setTextColor(0xFF999DB2.toInt())
            })
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
        }
    }

    private fun smallButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 10f
        minWidth = 0
        setPadding(8, 0, 8, 0)
        setOnClickListener { action() }
    }

    private fun showFolderActions(folder: DocumentFile) {
        val options = arrayOf("重命名文件夹", "删除文件夹")
        AlertDialog.Builder(this)
            .setTitle(folder.name ?: "文件夹操作")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> renameFolder(folder)
                    1 -> confirmDelete(folder)
                }
            }.show()
    }

    private fun createFolder() {
        val parent = current ?: return toast("请先选择管理目录")
        val input = EditText(this).apply { hint = "例如：我的资料"; inputType = InputType.TYPE_CLASS_TEXT }
        AlertDialog.Builder(this).setTitle("新建文件夹").setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank() || name.contains("/")) toast("请输入有效的文件夹名称")
                else if (parent.findFile(name) != null) toast("同名项目已存在")
                else {
                    val made = parent.createDirectory(name)
                    toast(if (made != null) "文件夹已创建" else "创建失败")
                    render()
                }
            }.show()
    }

    private fun renameFolder(folder: DocumentFile) {
        val input = EditText(this).apply {
            setText(folder.name ?: "")
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this).setTitle("重命名文件夹").setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank() || name.contains("/")) toast("名称无效")
                else {
                    val ok = folder.renameTo(name)
                    toast(if (ok) "重命名成功" else "重命名失败（系统提供者可能不支持）")
                    render()
                }
            }.show()
    }

    private fun confirmDelete(folder: DocumentFile) {
        AlertDialog.Builder(this)
            .setTitle("删除文件夹？")
            .setMessage("将删除“${folder.name}”及其内容。此操作无法撤销。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                val ok = folder.delete()
                toast(if (ok) "已删除" else "删除失败")
                render()
            }.show()
    }

    private fun compressCurrent() {
        val folder = current ?: return toast("请先选择管理目录")
        val name = (folder.name?.ifBlank { "folder" } ?: "folder") + ".zip"
        createZip.launch(name)
    }

    private fun addFolderToZip(folder: DocumentFile, prefix: String, zip: ZipOutputStream) {
        zip.putNextEntry(ZipEntry("$prefix/"))
        zip.closeEntry()
        folder.listFiles().forEach { child ->
            val childName = "$prefix/${child.name ?: "unnamed"}"
            if (child.isDirectory) addFolderToZip(child, childName, zip)
            else {
                zip.putNextEntry(ZipEntry(childName))
                contentResolver.openInputStream(child.uri)?.use { input ->
                    BufferedInputStream(input).use { it.copyTo(zip) }
                }
                zip.closeEntry()
            }
        }
    }

    private fun info(message: String) {
        content.addView(TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(0xFFD0D0DE.toInt())
            setPadding(8, 18, 8, 18)
        })
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
