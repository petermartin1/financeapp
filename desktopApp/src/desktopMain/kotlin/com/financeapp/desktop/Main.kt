package com.financeapp.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.financeapp.App
import com.financeapp.di.appModules
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(appModules())
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "FinanceApp"
            // Note: Add icon parameter when app-icon.png is placed in resources/icons/
            // icon = painterResource("icons/app-icon.png")
        ) {
            App()
        }
    }
}
