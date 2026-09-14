package com.example.data.model

enum class ServiceState(val label: String) {
    RUNNING("Running"),
    STOPPED("Stopped"),
    UNKNOWN("Unknown")
}

data class ServiceStatus(
    val name: String,
    val serviceUnit: String,
    val state: ServiceState,
    val port: Int? = null,
    val description: String,
    val uptime: String = "14 days",
    val memoryUsageMb: Int = 128
)
