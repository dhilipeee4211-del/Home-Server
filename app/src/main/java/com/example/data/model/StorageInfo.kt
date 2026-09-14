package com.example.data.model

data class StoragePartition(
    val mountPoint: String,
    val label: String,
    val filesystem: String,
    val totalGb: Double,
    val usedGb: Double,
    val usagePercent: Int
)

data class StorageInfo(
    val totalTb: Double = 4.0,
    val usedTb: Double = 1.2,
    val freeTb: Double = 2.8,
    val usagePercent: Int = 30,
    val partitions: List<StoragePartition> = emptyList()
)
