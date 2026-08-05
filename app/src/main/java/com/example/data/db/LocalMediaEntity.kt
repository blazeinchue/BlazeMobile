package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_media_assets")
data class LocalMediaAssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val album: String,
    val mediaType: String, // IMAGE, VIDEO
    val relativePath: String,
    val mimeType: String = "image/jpeg",
    val sizeBytes: Long = 1024 * 500,
    val dateAddedMillis: Long = System.currentTimeMillis(),
    val isAgentAccessible: Boolean = true
)
