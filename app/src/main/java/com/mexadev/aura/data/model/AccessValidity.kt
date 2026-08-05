package com.mexadev.aura.data.model

import com.mexadev.aura.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed class AccessValidity {
    abstract val shortBadgeText: String
    abstract val detailMessage: String
    abstract val isValidNow: Boolean
    abstract val badgeBgColorRes: Int
    abstract val badgeTextColorRes: Int
    abstract val iconRes: Int

    data object Valid : AccessValidity() {
        override val shortBadgeText = "Listo"
        override val detailMessage = "Listo para funcionar y ser escaneado en caseta."
        override val isValidNow = true
        override val badgeBgColorRes = R.color.aura_success_light
        override val badgeTextColorRes = R.color.aura_success
        override val iconRes = R.drawable.ic_check
    }

    data object Paused : AccessValidity() {
        override val shortBadgeText = "Pausado"
        override val detailMessage = "Acceso pausado. El código ha sido deshabilitado manualmente."
        override val isValidNow = false
        override val badgeBgColorRes = R.color.aura_surface_variant
        override val badgeTextColorRes = R.color.aura_text_tertiary
        override val iconRes = R.drawable.ic_lock
    }

    data class UsesExceeded(val uses: Int, val maxUses: Int) : AccessValidity() {
        override val shortBadgeText = "Agotado"
        override val detailMessage = "Límite de usos alcanzado ($uses de $maxUses usos)."
        override val isValidNow = false
        override val badgeBgColorRes = R.color.aura_error_light
        override val badgeTextColorRes = R.color.aura_error
        override val iconRes = R.drawable.ic_warning
    }

    data class Expired(val validUntil: String) : AccessValidity() {
        override val shortBadgeText = "Caducado"
        override val detailMessage = "El período de validez venció el $validUntil."
        override val isValidNow = false
        override val badgeBgColorRes = R.color.aura_error_light
        override val badgeTextColorRes = R.color.aura_error
        override val iconRes = R.drawable.ic_warning
    }

    data class NotStarted(val validFrom: String) : AccessValidity() {
        override val shortBadgeText = "Aún no activo"
        override val detailMessage = "Este código estará disponible a partir del $validFrom."
        override val isValidNow = false
        override val badgeBgColorRes = R.color.aura_info_light
        override val badgeTextColorRes = R.color.aura_info
        override val iconRes = R.drawable.ic_info
    }

    data class DayNotAllowed(val dayName: String) : AccessValidity() {
        override val shortBadgeText = "Día no permitido"
        override val detailMessage = "Acceso no autorizado para el día de hoy ($dayName)."
        override val isValidNow = false
        override val badgeBgColorRes = R.color.aura_warning_light
        override val badgeTextColorRes = R.color.aura_warning
        override val iconRes = R.drawable.ic_warning
    }

    data class TimeNotAllowed(val startTime: String, val endTime: String) : AccessValidity() {
        override val shortBadgeText = "Fuera de horario"
        override val detailMessage = "Acceso permitido únicamente en el horario de $startTime a $endTime."
        override val isValidNow = false
        override val badgeBgColorRes = R.color.aura_warning_light
        override val badgeTextColorRes = R.color.aura_warning
        override val iconRes = R.drawable.ic_warning
    }
}

fun AccessCode.evaluateValidity(): AccessValidity {
    if (!isActive) {
        return AccessValidity.Paused
    }

    if (maxUses != null && uses >= maxUses) {
        return AccessValidity.UsesExceeded(uses, maxUses)
    }

    val calendar = Calendar.getInstance()

    // Check date range
    val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

    val cleanValidUntil = validUntil?.take(10)
    if (!cleanValidUntil.isNullOrEmpty() && todayIso > cleanValidUntil) {
        return AccessValidity.Expired(cleanValidUntil)
    }

    val cleanValidFrom = validFrom?.take(10)
    if (!cleanValidFrom.isNullOrEmpty() && todayIso < cleanValidFrom) {
        return AccessValidity.NotStarted(cleanValidFrom)
    }

    // Check active days
    if (!activeDays.isNullOrEmpty()) {
        val isoDay = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }

        if (!activeDays.contains(isoDay)) {
            val dayName = when (isoDay) {
                1 -> "Lunes"
                2 -> "Martes"
                3 -> "Miércoles"
                4 -> "Jueves"
                5 -> "Viernes"
                6 -> "Sábado"
                7 -> "Domingo"
                else -> "hoy"
            }
            return AccessValidity.DayNotAllowed(dayName)
        }
    }

    // Check active hours
    val cleanStart = startTime?.take(5)
    val cleanEnd = endTime?.take(5)
    if (!cleanStart.isNullOrEmpty() && !cleanEnd.isNullOrEmpty()) {
        val currentNowTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)
        val isTimeValid = if (cleanStart <= cleanEnd) {
            currentNowTime in cleanStart..cleanEnd
        } else {
            currentNowTime >= cleanStart || currentNowTime <= cleanEnd
        }

        if (!isTimeValid) {
            return AccessValidity.TimeNotAllowed(cleanStart, cleanEnd)
        }
    }

    return AccessValidity.Valid
}
