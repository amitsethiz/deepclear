package com.deepclear.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.deepclear.app.ui.home.HomeScreen
import com.deepclear.app.ui.optimizer.OptimizerScreen
import com.deepclear.app.ui.scan.ScanResultsScreen

@Composable
fun DeepClearNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onScanComplete = {
                    navController.navigate(Routes.SCAN_RESULTS)
                },
                onNavigateToOptimizer = {
                    navController.navigate(Routes.OPTIMIZER)
                }
            )
        }

        composable(Routes.SCAN_RESULTS) {
            ScanResultsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.OPTIMIZER) {
            OptimizerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
