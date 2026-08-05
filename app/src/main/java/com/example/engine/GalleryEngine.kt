package com.example.engine

import com.example.data.repository.AgentBridgeRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryEngine(private val repository: AgentBridgeRepository) {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    suspend fun getGalleryAssetsSummary(): String {
        val assets = repository.mediaAssets.first()
        if (assets.isEmpty()) {
            return "No photos or media assets found in gallery."
        }
        val builder = StringBuilder("Gallery & Media Assets (${assets.size} items):\n")
        assets.forEachIndexed { index, media ->
            val dateStr = dateFormat.format(Date(media.dateAddedMillis))
            val sizeMb = String.format(Locale.US, "%.2f MB", media.sizeBytes / (1024f * 1024f))
            builder.append("${index + 1}. [${media.mediaType}] ${media.title}\n")
            builder.append("   Album: ${media.album} | Size: $sizeMb | Date: $dateStr\n")
            builder.append("   Path: ${media.relativePath}\n")
        }
        return builder.toString()
    }

    suspend fun inspectMediaAsset(titleOrPath: String): String {
        val assets = repository.mediaAssets.first()
        val match = assets.find { it.title.contains(titleOrPath, ignoreCase = true) || it.relativePath.contains(titleOrPath, ignoreCase = true) }
            ?: return "Media asset containing '$titleOrPath' was not found."

        val dateStr = dateFormat.format(Date(match.dateAddedMillis))
        return """
            Media Details for '${match.title}':
            - MediaType: ${match.mediaType}
            - Album: ${match.album}
            - MimeType: ${match.mimeType}
            - File Path: ${match.relativePath}
            - File Size: ${match.sizeBytes} bytes
            - Date Added: $dateStr
            - Agent Accessible: ${match.isAgentAccessible}
        """.trimIndent()
    }
}
