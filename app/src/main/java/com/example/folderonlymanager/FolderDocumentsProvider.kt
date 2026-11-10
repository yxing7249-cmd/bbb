package com.example.folderonlymanager

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException

/**
 * Exposes the user-authorized directory as a location in Android's SAF picker.
 * The provider is intended to be queried by the system DocumentsUI.
 */
class FolderDocumentsProvider : DocumentsProvider() {
    companion object {
        private const val ROOT_ID = "folder_only_root"
        private const val ROOT_DOCUMENT_ID = "root"
        private val ROOT_COLUMNS = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_SUMMARY
        )
        private val DOC_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }

    private val authority: String get() = "${context!!.packageName}.documents"

    private fun selectedTreeUri(): Uri? {
        val raw = context?.getSharedPreferences("folder_manager", Context.MODE_PRIVATE)
            ?.getString("tree_uri", null) ?: return null
        return try { Uri.parse(raw) } catch (_: Exception) { null }
    }

    private fun rootFile(): DocumentFile? {
        val uri = selectedTreeUri() ?: return null
        return try { DocumentFile.fromTreeUri(context!!, uri)?.takeIf { it.isDirectory } }
        catch (_: Exception) { null }
    }

    private fun documentId(file: DocumentFile): String {
        val tree = selectedTreeUri() ?: throw FileNotFoundException("No folder selected")
        return try { DocumentsContract.getDocumentId(file.uri) }
        catch (_: Exception) { DocumentsContract.getTreeDocumentId(tree) }
    }

    private fun fileForId(id: String): DocumentFile {
        val tree = selectedTreeUri()
            ?: throw FileNotFoundException("Open Folder Only Manager and select a folder first")
        if (id == ROOT_DOCUMENT_ID) {
            return rootFile() ?: throw FileNotFoundException("Selected folder is unavailable")
        }
        val childUri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        return DocumentFile.fromTreeUri(context!!, childUri)
            ?: throw FileNotFoundException("Document not found")
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection?.map { it }.orEmpty()
            .ifEmpty { ROOT_COLUMNS.toList() }.toTypedArray()
        val result = MatrixCursor(columns)
        val root = rootFile() ?: return result
        val values = mapOf(
            DocumentsContract.Root.COLUMN_ROOT_ID to ROOT_ID,
            DocumentsContract.Root.COLUMN_FLAGS to (
                DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                    DocumentsContract.Root.FLAG_LOCAL_ONLY
            ),
            DocumentsContract.Root.COLUMN_TITLE to "Folder Only Manager",
            DocumentsContract.Root.COLUMN_DOCUMENT_ID to ROOT_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES to "*/*\n${DocumentsContract.Document.MIME_TYPE_DIR}",
            DocumentsContract.Root.COLUMN_SUMMARY to (root.name ?: "已授权文件夹")
        )
        result.addRow(columns.map { values[it] }.toTypedArray())
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val columns = projection?.map { it }.orEmpty()
            .ifEmpty { DOC_COLUMNS.toList() }.toTypedArray()
        val result = MatrixCursor(columns)
        val file = fileForId(documentId)
        result.addRow(documentRow(file, documentId, columns))
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val columns = projection?.map { it }.orEmpty()
            .ifEmpty { DOC_COLUMNS.toList() }.toTypedArray()
        val result = MatrixCursor(columns)
        val parent = fileForId(parentDocumentId)
        if (!parent.isDirectory) return result
        parent.listFiles().sortedBy { it.name?.lowercase().orEmpty() }.forEach { child ->
            val id = documentId(child)
            result.addRow(documentRow(child, id, columns))
        }
        return result
    }

    private fun documentRow(file: DocumentFile, id: String, columns: Array<String>): Array<Any?> {
        val isDir = file.isDirectory
        val flags = if (isDir) {
            DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or
                DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                DocumentsContract.Document.FLAG_SUPPORTS_RENAME
        } else {
            DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                DocumentsContract.Document.FLAG_SUPPORTS_RENAME
        }
        val values = mapOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID to id,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME to (file.name ?: "未命名"),
            DocumentsContract.Document.COLUMN_MIME_TYPE to if (isDir) {
                DocumentsContract.Document.MIME_TYPE_DIR
            } else {
                file.type ?: "application/octet-stream"
            },
            DocumentsContract.Document.COLUMN_FLAGS to flags,
            DocumentsContract.Document.COLUMN_SIZE to file.length(),
            DocumentsContract.Document.COLUMN_LAST_MODIFIED to file.lastModified()
        )
        return columns.map { values[it] }.toTypedArray()
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = fileForId(documentId)
        if (file.isDirectory) throw FileNotFoundException("A folder cannot be opened as a file")
        if (mode.contains("w") || mode.contains("a") || mode.contains("+")) {
            throw FileNotFoundException("This provider currently offers read-only file access")
        }
        return context!!.contentResolver.openFileDescriptor(file.uri, "r", signal)
            ?: throw FileNotFoundException("Unable to open document")
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = fileForId(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException("Parent is not a folder")
        val created = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            parent.createDirectory(displayName)
        } else {
            parent.createFile(mimeType, displayName)
        } ?: throw FileNotFoundException("Unable to create document")
        context!!.contentResolver.notifyChange(
            DocumentsContract.buildChildDocumentsUri(authority, parentDocumentId), null
        )
        return documentId(created)
    }

    override fun deleteDocument(documentId: String) {
        val file = fileForId(documentId)
        if (!file.delete()) throw FileNotFoundException("Unable to delete document")
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = fileForId(documentId)
        if (!file.renameTo(displayName)) throw FileNotFoundException("Unable to rename document")
        return documentId(file)
    }
}
