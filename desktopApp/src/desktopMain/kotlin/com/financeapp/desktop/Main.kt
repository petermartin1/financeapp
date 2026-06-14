package com.financeapp.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.financeapp.App
import com.financeapp.di.appModules
import com.financeapp.domain.service.PriceRefreshService
import com.financeapp.domain.service.SnapshotScheduler
import org.koin.core.context.startKoin

fun main() {
    val koin = startKoin {
        modules(appModules())
    }.koin

    application {
        val windowState = rememberWindowState(
            width = 1400.dp,
            height = 900.dp
        )
        Window(
            onCloseRequest = {
                // Stop the background service scopes before exiting (R33).
                koin.get<PriceRefreshService>().shutdown()
                koin.get<SnapshotScheduler>().shutdown()
                exitApplication()
            },
            title = "FinanceApp",
            state = windowState
            // Note: Add icon parameter when app-icon.png is placed in resources/icons/
            // icon = painterResource("icons/app-icon.png")
        ) {
            App()
        }
    }
}
