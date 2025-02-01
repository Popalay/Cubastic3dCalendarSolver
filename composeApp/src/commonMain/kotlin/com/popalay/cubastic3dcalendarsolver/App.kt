package com.popalay.cubastic3dcalendarsolver

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

// ----------------------------------------------------------------
// Опис структури ігрового поля
// ----------------------------------------------------------------
// Ми використовуємо нерегулярну сітку – кожен рядок має свою кількість клітинок.
val boardStructure = listOf(6, 6, 7, 7, 7, 7, 3) // 7 рядків: 6,6,7,7,7,7,3 клітинок відповідно

// Тип для ігрового поля – список рядків, кожен з яких є змінним списком чисел.
// Значення:
//   -1 – зафіксована (орієнтир, наприклад, літера/число)
//    0 – порожня (можна заповнювати фігурами)
//    1..8 – заповнена деталлю з відповідним ідентифікатором
typealias Board = MutableList<MutableList<Int>>

// Розмір однієї клітинки (як на зображенні)
val cellSize: Dp = 60.dp

// Максимальна кількість клітинок у рядку – для визначення ширини Canvas
val maxCols = boardStructure.maxOrNull() ?: 0

// ----------------------------------------------------------------
// Опис фігур (деталей головоломки)
// ----------------------------------------------------------------
// Кожна деталь задається списком координат (відносно "якоря" (0,0)) та кольором.
// Загальна площа клітинок деталей повинна дорівнювати кількості незаповнених клітинок.
// Припустимо, що фігури займають сумарно 30 клітинок, а решта (43 – 30 = 13)
// має бути зафіксовано (наприклад, для назви місяця або інших орієнтирів).
data class Piece(
    val id: Int,
    val cells: List<Pair<Int, Int>>, // координати клітинок фігури
    val color: Color
) {
    // Функція повертає всі можливі трансформації (повороти на 90° та горизонтальне віддзеркалення)
    fun transformations(): List<List<Pair<Int, Int>>> {
        val shapes = mutableSetOf<List<Pair<Int, Int>>>()
        fun normalize(shape: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
            val minX = shape.minOf { it.first }
            val minY = shape.minOf { it.second }
            return shape.map { (x, y) -> x - minX to y - minY }
                .sortedWith(compareBy({ it.first }, { it.second }))
        }

        var shape = cells
        repeat(4) {
            shape = shape.map { (x, y) -> y to -x } // поворот на 90°
            shapes.add(normalize(shape))
            // Віддзеркалення по горизонталі
            shapes.add(normalize(shape.map { (x, y) -> -x to y }))
        }
        return shapes.toList()
    }
}

// Визначаємо 8 деталей з різними формами та кольорами.
// (Примітка. Фігури залишено без зміни – їх сумарна площа становить 30 клітинок)
val pieces = listOf(
    Piece(1, listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 3 to 1), MaterialTheme.colors.),       // L-подібна (5 клітинок)
    Piece(2, listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 2 to 0, 2 to 1), Color.Blue), // Прямокутник (6)
    Piece(3, listOf(0 to 0, 0 to 1, 1 to 1, 2 to 0, 2 to 1), Color.Cyan),       // U-подібна (5)
    Piece(4, listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2, 2 to 2), Color.Yellow),               // Z-подібна (5)
    Piece(5, listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1, 1 to 0), Color.Magenta), // T-подібна (5 клітинок)
    Piece(6, listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 2 to 0), Color.Gray),       // Неправильний прямокутник (5)
    Piece(7, listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 2), Color.Red), // L-подібна (4 клітинки)
    Piece(7, listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 1, 3 to 1), Color.Green) // L-подібна (4 клітинки)

)

// ----------------------------------------------------------------
// Функція розв’язання головоломки (метод backtracking)
// ----------------------------------------------------------------
fun solvePuzzle(
    board: Board,
    piecesRemaining: List<Piece>
): Board? {
    // Знаходимо першу вільну клітинку (значення 0) серед існуючих клітинок
    var emptyRow = -1
    var emptyCol = -1
    loop@ for (r in board.indices) {
        for (c in board[r].indices) {
            if (board[r][c] == 0) {
                emptyRow = r
                emptyCol = c
                break@loop
            }
        }
    }
    // Якщо вільних клітинок немає – повертаємо знайдений розв’язок
    if (emptyRow == -1) return board

    // Перебір деталей, що залишилися
    for ((index, piece) in piecesRemaining.withIndex()) {
        for (shape in piece.transformations()) {
            // Для кожної трансформації спробуємо розмістити деталь так,
            // щоб один із її квадратів потрапив у (emptyRow, emptyCol)
            for (cell in shape) {
                val offsetRow = emptyRow - cell.first
                val offsetCol = emptyCol - cell.second
                var canPlace = true
                for ((dx, dy) in shape) {
                    val r = offsetRow + dx
                    val c = offsetCol + dy
                    // Перевірка: чи існує рядок r і чи є клітинка c у цьому ряді, а також чи вона порожня
                    if (r !in board.indices || c !in board[r].indices || board[r][c] != 0) {
                        canPlace = false
                        break
                    }
                }
                if (!canPlace) continue

                // Створюємо копію поля
                val boardCopy: Board = board.map { row -> row.toMutableList() }.toMutableList()
                // Розміщуємо деталь: кожна клітинка отримує ідентифікатор деталі
                for ((dx, dy) in shape) {
                    val r = offsetRow + dx
                    val c = offsetCol + dy
                    boardCopy[r][c] = piece.id
                }
                val nextPieces = piecesRemaining.toMutableList().apply { removeAt(index) }
                val result = solvePuzzle(boardCopy, nextPieces)
                if (result != null) return result
            }
        }
    }
    return null // якщо розташувати жодну деталь неможливо
}

// ----------------------------------------------------------------
// UI – інтерфейс користувача
// ----------------------------------------------------------------
@Composable
@Preview
fun App() {
    // Створюємо початкове поле відповідно до boardStructure:
    // Кожен рядок – список з початковими значеннями 0 (порожні клітинки)
    var boardState by remember {
        mutableStateOf<Board>(boardStructure.map { MutableList(it) { 0 } }.toMutableList())
    }
    // Режим позначення зафіксованих клітинок (орієнтирів)
    var markingFixed by remember { mutableStateOf(true) }
    // Повідомлення для користувача
    var message by remember { mutableStateOf("Клацніть по клітинках для позначення орієнтирів (наприклад, назва місяця або дата).") }
    // Збереження знайденого рішення
    var solution by remember { mutableStateOf<Board?>(null) }

    // Розміри Canvas: ширина = maxCols * cellSize, висота = (кількість рядків) * cellSize
    val canvasWidth = cellSize * maxCols
    val canvasHeight = cellSize * boardStructure.size

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF5F5F5)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Cubastic 3D – Головоломка",
                    style = MaterialTheme.typography.h4,
                    color = Color(0xFF424242)
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Canvas для відтворення ігрового поля з округленими кутами та тінню
                Row(
                    modifier = Modifier
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                        .border(2.dp, Color.Black, RoundedCornerShape(12.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(canvasWidth, canvasHeight)
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    if (!markingFixed) return@detectTapGestures
                                    // Обчислюємо індекс рядка та стовпчика за координатами кліку
                                    val row = (offset.y / cellSize.toPx()).toInt()
                                    val col = (offset.x / cellSize.toPx()).toInt()
                                    // Перевіряємо, чи існує така клітинка (не всі рядки мають однакову кількість клітинок)
                                    if (row in boardState.indices && col in boardState[row].indices) {
                                        // Якщо клітинка порожня, зафіксуємо її (значення -1)
                                        if (boardState[row][col] == 0) {
                                            boardState = boardState.map { it.toMutableList() }.toMutableList().also {
                                                it[row][col] = -1
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        // Малюємо кожну клітинку окремо
                        for (r in boardState.indices) {
                            for (c in boardState[r].indices) {
                                val cellValue = boardState[r][c]
                                val topLeft = Offset(c * cellSize.toPx(), r * cellSize.toPx())
                                val fillColor = when {
                                    cellValue == -1 -> Color.DarkGray // зафіксована клітинка
                                    cellValue == 0 -> Color.White       // порожня
                                    else -> pieces.find { it.id == cellValue }?.color ?: Color.LightGray
                                }
                                drawRect(
                                    color = fillColor,
                                    topLeft = topLeft,
                                    size = Size(cellSize.toPx(), cellSize.toPx())
                                )
                                // Обводка клітинки
                                drawRect(
                                    color = Color.Black,
                                    topLeft = topLeft,
                                    size = Size(cellSize.toPx(), cellSize.toPx()),
                                    style = Stroke(width = 1.5f)
                                )
                            }
                        }
                    }

//                    PieceList(pieces)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = message, color = Color(0xFF424242))
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    if (markingFixed) {
                        Button(onClick = {
                            markingFixed = false
                            message = "Фіксація завершена. Натисніть «Розв’язати» для пошуку рішення."
                        }) {
                            Text("Готово")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Button(onClick = {
                        // Готуємо копію поля для розв’язувача:
                        // Зафіксовані клітинки (значення -1) не змінюються, інші – 0.
                        val boardForSolver: Board = boardState.map { row ->
                            row.map { if (it == -1) -1 else 0 }.toMutableList()
                        }.toMutableList()
                        solution = solvePuzzle(boardForSolver, pieces)
                        if (solution != null) {
                            message = "Розв’язок знайдено!"
                            boardState = solution!!
                        } else {
                            message = "Розв’язок не знайдено."
                        }
                    }) {
                        Text("Розв’язати")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        // Скидання поля: створюємо нове поле за boardStructure та повертаємо режим позначення
                        boardState = boardStructure.map { MutableList(it) { 0 } }.toMutableList()
                        markingFixed = true
                        solution = null
                        message = "Клацніть по клітинках для позначення орієнтирів (наприклад, назва місяця або дата)."
                    }) {
                        Text("Скинути")
                    }
                }
            }
        }
    }
}

@Composable
fun PieceView(piece: Piece) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .size(width = 80.dp, height = 60.dp)
    ) {
        piece.cells.forEach { (x, y) ->
            Box(
                modifier = Modifier
                    .offset(x = (x * 20).dp, y = (y * 20).dp)
                    .size(18.dp)
                    .background(piece.color)
            )
        }
    }
}

@Composable
fun PieceList(pieces: List<Piece>) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        pieces.forEach { piece ->
            PieceView(piece)
        }
    }
}