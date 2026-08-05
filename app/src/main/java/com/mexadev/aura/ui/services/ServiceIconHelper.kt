package com.mexadev.aura.ui.services

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.mexadev.aura.R
import java.util.Locale

data class ServiceCategoryTheme(
    @param:DrawableRes val iconRes: Int,
    @param:ColorRes val iconColorRes: Int,
    @param:ColorRes val bgColorRes: Int,
    val categoryName: String
)

object ServiceIconHelper {

    fun getCategoryTheme(title: String?, description: String? = null): ServiceCategoryTheme {
        val text = "${title.orEmpty()} ${description.orEmpty()}".lowercase(Locale.getDefault())

        return when {
            text.contains("jardin") || text.contains("pasto") || text.contains("planta") || text.contains("poda") -> {
                ServiceCategoryTheme(
                    iconRes = R.drawable.ic_gardening,
                    iconColorRes = R.color.aura_success,
                    bgColorRes = R.color.aura_success_light,
                    categoryName = "Jardinería"
                )
            }
            text.contains("limp") || text.contains("aseo") || text.contains("sanit") || text.contains("lavad") -> {
                ServiceCategoryTheme(
                    iconRes = R.drawable.ic_cleaning,
                    iconColorRes = R.color.aura_info,
                    bgColorRes = R.color.aura_info_light,
                    categoryName = "Limpieza"
                )
            }
            text.contains("plomer") || text.contains("agua") || text.contains("fuga") || text.contains("tuber") -> {
                ServiceCategoryTheme(
                    iconRes = R.drawable.ic_wrench,
                    iconColorRes = R.color.aura_primary,
                    bgColorRes = R.color.aura_primary_surface,
                    categoryName = "Plomería"
                )
            }
            text.contains("electr") || text.contains("luz") || text.contains("cabl") || text.contains("foco") -> {
                ServiceCategoryTheme(
                    iconRes = R.drawable.ic_lightning,
                    iconColorRes = R.color.aura_warning,
                    bgColorRes = R.color.aura_warning_light,
                    categoryName = "Electricidad"
                )
            }
            text.contains("pint") || text.contains("imper") || text.contains("resan") -> {
                ServiceCategoryTheme(
                    iconRes = R.drawable.ic_paint,
                    iconColorRes = R.color.aura_badge_blue,
                    bgColorRes = R.color.aura_primary_surface,
                    categoryName = "Mantenimiento"
                )
            }
            text.contains("segur") || text.contains("chapa") || text.contains("cerraj") || text.contains("camara") -> {
                ServiceCategoryTheme(
                    iconRes = R.drawable.ic_security,
                    iconColorRes = R.color.aura_primary_dark,
                    bgColorRes = R.color.aura_surface_variant,
                    categoryName = "Seguridad"
                )
            }
            else -> {
                ServiceCategoryTheme(
                    iconRes = R.drawable.ic_service,
                    iconColorRes = R.color.aura_primary,
                    bgColorRes = R.color.aura_primary_surface,
                    categoryName = "Hogar & Servicios"
                )
            }
        }
    }
}
