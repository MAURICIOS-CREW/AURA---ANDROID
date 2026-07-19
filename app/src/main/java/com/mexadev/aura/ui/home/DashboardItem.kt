package com.mexadev.aura.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.mexadev.aura.R

sealed class DashboardItem(
    @param:IdRes val id: Int,
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int
) {
    /**
     * Devuelve la instancia del Fragmento que maneja el detalle de este módulo.
     * Si el módulo aún no está implementado, devuelve null para mostrar "Próximamente".
     */
    open fun createFragment(): androidx.fragment.app.Fragment? = null

    /**
     * Etiqueta única para registrar el fragmento en el FragmentManager.
     */
    open val fragmentTag: String? = null

    object Accesos : DashboardItem(R.id.btnAccesos, R.string.dashboard_accesos, R.drawable.ic_lock) {
        override fun createFragment() = com.mexadev.aura.ui.accesses.AccessesFragment()
        override val fragmentTag = "AccessesFragment"
    }

    object Vehiculos : DashboardItem(R.id.btnVehiculos, R.string.dashboard_vehiculos, R.drawable.ic_car) {
        override fun createFragment() = com.mexadev.aura.ui.vehicles.VehiclesFragment()
        override val fragmentTag = "VehiclesFragment"
    }

    object Incidencias : DashboardItem(R.id.btnIncidencias, R.string.dashboard_incidencias, R.drawable.ic_warning) {
        override fun createFragment() = com.mexadev.aura.ui.incidents.IncidentsFragment()
        override val fragmentTag = "IncidentsFragment"
    }

    object Pagos : DashboardItem(R.id.btnPagos, R.string.dashboard_pagos, R.drawable.ic_wallet)
    object Reservas : DashboardItem(R.id.btnReservas, R.string.dashboard_reservas, R.drawable.ic_calendar)
    object Documentos : DashboardItem(R.id.btnDocumentos, R.string.dashboard_documentos, R.drawable.ic_document)
    object Comunidad : DashboardItem(R.id.btnComunidad, R.string.dashboard_comunidad, R.drawable.ic_people)
    object Encuestas : DashboardItem(R.id.btnEncuestas, R.string.dashboard_encuestas, R.drawable.ic_chart)

    companion object {
        fun fromId(@IdRes id: Int): DashboardItem? {
            return when (id) {
                R.id.btnAccesos -> Accesos
                R.id.btnVehiculos -> Vehiculos
                R.id.btnIncidencias -> Incidencias
                R.id.btnPagos -> Pagos
                R.id.btnReservas -> Reservas
                R.id.btnDocumentos -> Documentos
                R.id.btnComunidad -> Comunidad
                R.id.btnEncuestas -> Encuestas
                else -> null
            }
        }
    }
}
