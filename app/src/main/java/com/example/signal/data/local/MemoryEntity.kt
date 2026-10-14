package com.example.signal.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    val content: String,

    val sourceUrl: String,

    val sourceType: String,

    val summary: String = "",

    val keywords: String = "",

    val captureLocation: String = "",

    val targetLocation: String = "",

    val targetLatitude: Double? = null,

    val targetLongitude: Double? = null,

    val createdAt: Long = System.currentTimeMillis(),

    val reminderDate: String? = null,

    val reminderAt: Long? = null,

    val reminderTime: String? = null,

    val detectedUrl: String? = null
)
