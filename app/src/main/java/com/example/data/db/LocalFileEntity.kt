package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_file_nodes")
data class LocalFileNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0,
    val content: String = "",
    val extension: String = "",
    val parentPath: String = "/",
    val lastModifiedMillis: Long = System.currentTimeMillis()
)
