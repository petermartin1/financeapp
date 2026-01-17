package com.financeapp.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.financeapp.App
import com.financeapp.di.appModules
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(appModules())
    }

    application {
        val windowState = rememberWindowState(
            width = 1400.dp,
            height = 900.dp
        )
        Window(
            onCloseRequest = ::exitApplication,
            title = "FinanceApp",
            state = windowState
            // Note: Add icon parameter when app-icon.png is placed in resources/icons/
            // icon = painterResource("icons/app-icon.png")
        ) {
            App()
        }
    }
}
