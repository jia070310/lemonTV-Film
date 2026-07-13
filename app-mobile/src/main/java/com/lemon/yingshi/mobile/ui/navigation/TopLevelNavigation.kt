package com.lemon.yingshi.mobile.ui.navigation

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.lemon.yingshi.mobile.R

object TopLevelNavigation {

    private val topLevelDestinations = setOf(
        R.id.homeFragment,
        R.id.recommendationFragment,
        R.id.profileFragment
    )

    fun navigate(fragment: Fragment, @IdRes destinationId: Int) {
        if (destinationId !in topLevelDestinations) return

        val navController = fragment.findNavController()
        val bottomNav = fragment.requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
        val menuItem = bottomNav?.menu?.findItem(destinationId)
        if (menuItem != null && NavigationUI.onNavDestinationSelected(menuItem, navController)) {
            return
        }

        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(navController.graph.startDestinationId, false, true)
            .build()
        navController.navigate(destinationId, null, options)
    }
}
