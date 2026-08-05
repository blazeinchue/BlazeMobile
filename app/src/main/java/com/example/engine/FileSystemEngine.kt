package com.example.engine

import com.example.data.db.LocalFileNodeEntity
import com.example.data.repository.AgentBridgeRepository
import kotlinx.coroutines.flow.first

class FileSystemEngine(private val repository: AgentBridgeRepository) {

    suspend fun listDirectoryContents(directoryPath: String = "/"): String {
        val targetPath = if (directoryPath.endsWith("/")) directoryPath.dropLast(1).ifEmpty { "/" } else directoryPath
        val normalizedPath = if (targetPath.isEmpty()) "/" else targetPath

        val files = repository.getFilesByParent(normalizedPath).first()
        if (files.isEmpty()) {
            return "Directory '$normalizedPath' is empty or does not exist."
        }

        val builder = StringBuilder("Directory Contents of '$normalizedPath' (${files.size} items):\n")
        files.forEachIndexed { index, file ->
            val typeStr = if (file.isDirectory) "<DIR>" else "<FILE>"
            val sizeStr = if (file.isDirectory) "" else "(${file.sizeBytes} B)"
            builder.append("${index + 1}. $typeStr ${file.name} $sizeStr\n")
            builder.append("   Path: ${file.path}\n")
        }
        return builder.toString()
    }

    suspend fun readFileContent(filePath: String): String {
        val node = repository.getFileByPath(filePath)
            ?: return "Error: File at '$filePath' not found."

        if (node.isDirectory) {
            return "Error: Path '$filePath' is a directory, not a readable text file."
        }

        return "--- FILE CONTENT OF '${node.name}' (${node.path}) ---\n${node.content}\n--- END OF FILE ---"
    }

    suspend fun createFileOrUpdate(
        parentPath: String,
        fileName: String,
        content: String,
        isDirectory: Boolean = false
    ): String {
        val parent = if (parentPath.endsWith("/")) parentPath.dropLast(1).ifEmpty { "/" } else parentPath
        val fullPath = if (parent == "/") "/$fileName" else "$parent/$fileName"
        val ext = if (fileName.contains(".")) fileName.substringAfterLast(".") else ""

        val existing = repository.getFileByPath(fullPath)
        if (existing != null) {
            val updated = existing.copy(
                content = content,
                sizeBytes = content.toByteArray().size.toLong(),
                lastModifiedMillis = System.currentTimeMillis()
            )
            repository.updateFileNode(updated)
            return "Updated file at '$fullPath'."
        } else {
            val newFile = LocalFileNodeEntity(
                name = fileName,
                path = fullPath,
                isDirectory = isDirectory,
                sizeBytes = content.toByteArray().size.toLong(),
                content = content,
                extension = ext,
                parentPath = parent,
                lastModifiedMillis = System.currentTimeMillis()
            )
            repository.addFileNode(newFile)
            return "Created new ${if (isDirectory) "directory" else "file"} at '$fullPath'."
        }
    }
}
