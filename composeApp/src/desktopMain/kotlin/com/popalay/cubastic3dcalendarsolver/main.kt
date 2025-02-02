package com.popalay.cubastic3dcalendarsolver

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Cubastic3dCalendarSolver",
        state = rememberWindowState(
            size = DpSize(650.dp, 650.dp)
        )
    ) {
        App()
    }
}