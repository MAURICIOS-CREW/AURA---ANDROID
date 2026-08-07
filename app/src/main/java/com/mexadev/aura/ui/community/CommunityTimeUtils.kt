package com.mexadev.aura.ui.community

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CommunityTimeUtils {

    /**
     * Formats a given timestamp string (in epoch milliseconds) into a user-friendly relative time string in Spanish.
     * If the string cannot be parsed to Long, it returns the string as is for backward compatibility.
     */
    fun formatRelativeTime(timestampStr: String?): String {
        if (timestampStr.isNullOrBlank()) return "Hace un momento"

        val millis = timestampStr.toLongOrNull() ?: return timestampStr
        val now = System.currentTimeMillis()
        val diff = now - millis

        if (diff < 30_000) {
            return "Hace un momento"
        }

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> "Hace un momento"
            minutes < 60 -> "Hace $minutes min"
            hours < 24 -> if (hours == 1L) "Hace 1 hora" else "Hace $hours h"
            days == 1L -> "Ayer"
            days < 7 -> "Hace $days días"
            else -> {
                val date = Date(millis)
                val format = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("es-ES"))
                format.format(date)
            }
        }
    }
}
