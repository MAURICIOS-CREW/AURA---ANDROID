package com.mexadev.aura.ui.home

import android.view.View

interface DashboardNavigator {
    fun navigateToTab(tabIndex: Int)
    fun navigateToDetail(cardView: View, item: DashboardItem)
    fun navigateToDetailWithFade(title: String, iconRes: Int)
}
