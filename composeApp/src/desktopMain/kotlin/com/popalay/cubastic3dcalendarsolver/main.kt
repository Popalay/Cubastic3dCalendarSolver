package com.popalay.cubastic3dcalendarsolver

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Cubastic3dCalendarSolver",
    ) {
        App()
    }
}