package com.example.data

enum class ScreenshotStatus {
    NEW_DETECTED,
    SCHEDULED,
    KEPT,
    PENDING_CONFIRMATION,
    EXPIRED
}

enum class ExpirationPreset(
    val id: String,
    val label: String,
    val shortLabel: String,
    val durationMillis: Long
) {
    FIVE_MINUTES("5m", "5 Minutes", "5m", 5L * 60_000L),
    ONE_HOUR("1h", "1 Hour", "1h", 60L * 60_000L),
    TEN_HOURS("10h", "10 Hours", "10h", 10L * 60L * 60_000L),
    ONE_DAY("1d", "1 Day", "1d", 24L * 60L * 60_000L),
    THREE_DAYS("3d", "3 Days", "3d", 3L * 24L * 60L * 60_000L),
    ONE_WEEK("1w", "1 Week", "1w", 7L * 24L * 60L * 60_000L);

    companion object {
        fun fromId(id: String?): ExpirationPreset? = entries.firstOrNull { it.id == id }
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class ScreenshotFilter(val label: String) {
    ALL("All"),
    SCHEDULED("Scheduled"),
    EXPIRING_SOON("Expiring Soon"),
    PENDING_CONFIRM("Needs Action"),
    UNASSIGNED("Unassigned"),
    KEPT("Kept"),
    EXPIRED("Deleted")
}
