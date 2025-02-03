package com.popalay.cubastic3dcalendarsolver

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

// ----------------------------------------------------------------
// Опис структури ігрового поля
// ----------------------------------------------------------------
// Ми використовуємо нерегулярну сітку – кожен рядок має свою кількість клітинок.
private val boardStructure = listOf(6, 6, 7, 7, 7, 7, 3) // 7 рядків: 6,6,7,7,7,7,3 клітинок відповідно

// Тип для ігрового поля – список рядків, кожен з яких є змінним списком чисел.
// Значення:
//   -1 – зафіксована (орієнтир, наприклад, літера/число)
//    0 – порожня (можна заповнювати фігурами)
//    1..8 – заповнена деталлю з відповідним ідентифікатором
private typealias Board = MutableList<MutableList<Int>>

// Розмір однієї клітинки (як на зображенні)
private val cellSize: Dp = 40.dp

// Максимальна кількість клітинок у рядку – для визначення ширини Canvas
private val maxCols = boardStructure.maxOrNull() ?: 0

// ----------------------------------------------------------------
// Опис фігур (деталей головоломки)
// ----------------------------------------------------------------
// Кожна деталь задається списком координат (відносно "якоря" (0,0)) та кольором.
// Загальна площа клітинок деталей повинна дорівнювати кількості незаповнених клітинок.
// Припустимо, що фігури займають сумарно 30 клітинок, а решта (43 – 30 = 13)
// має бути зафіксовано (наприклад, для назви місяця або інших орієнтирів).
private data class Piece(
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
private val pieces = listOf(
    Piece(1, listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 3 to 1), Color(0xFFFF5733)),       // L-подібна (5 клітинок)
    Piece(2, listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 2 to 0, 2 to 1), Color(0xFF33FF57)), // Прямокутник (6)
    Piece(3, listOf(0 to 0, 0 to 1, 1 to 1, 2 to 0, 2 to 1), Color(0xFF3357FF)),       // U-подібна (5)
    Piece(4, listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2, 2 to 2), Color(0xFFFF33A1)),               // Z-подібна (5)
    Piece(5, listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1, 1 to 0), Color(0xFFA133FF)), // T-подібна (5 клітинок)
    Piece(6, listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 2 to 0), Color(0xFFFE9957)),       // Неправильний прямокутник (5)
    Piece(7, listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 2), Color(0xFF33B0FF)), // L-подібна (5 клітинки)
    Piece(8, listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 1, 3 to 1), Color(0xFFC0FA49)) // S-подібна (5 клітинки)
)

private val months = setOf(
    "Січ", "Лют", "Бер", "Кві", "Тра", "Чер", "Лип", "Сер", "Вер", "Жов", "Лис", "Гру",
)

// ----------------------------------------------------------------
// Функція розв’язання головоломки (метод backtracking)
// ----------------------------------------------------------------
private fun solvePuzzle(
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
        mutableStateOf(boardStructure.map { MutableList(it) { 0 } }.toMutableList())
    }
    // Режим позначення зафіксованих клітинок (орієнтирів)
    var markingFixed by remember { mutableStateOf(true) }
    // Повідомлення для користувача
    var message by remember { mutableStateOf("Клацніть по клітинках для позначення орієнтирів\n(назва місяця та дата).") }
    // Збереження знайденого рішення
    var solution by remember { mutableStateOf<Board?>(null) }

    // Розміри Canvas: ширина = maxCols * cellSize, висота = (кількість рядків) * cellSize
    val canvasWidth = cellSize * maxCols
    val canvasHeight = cellSize * boardStructure.size

    val textMeasure = rememberTextMeasurer()

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF5F5F5)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = "Cubastic 3D – Головоломка",
                    style = MaterialTheme.typography.h4,
                    color = MaterialTheme.colors.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                PieceList(pieces)
                // Canvas для відтворення ігрового поля з округленими кутами та тінню
                Canvas(
                    modifier = Modifier
                        .shadow(12.dp, RoundedCornerShape(6.dp))
                        .background(Color.Gray, RoundedCornerShape(6.dp))
                        .border(2.dp, Color.Black, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .padding(12.dp)
                        .requiredSize(canvasWidth, canvasHeight)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                if (!markingFixed) return@detectTapGestures
                                // Обчислюємо індекс рядка та стовпчика за координатами кліку
                                val row = (offset.y / cellSize.toPx()).toInt()
                                val col = (offset.x / cellSize.toPx()).toInt()

                                val fixedCellsCount = boardState.sumOf { row -> row.count { it == -1 } }

                                // Перевіряємо, чи існує така клітинка (не всі рядки мають однакову кількість клітинок)
                                if (row in boardState.indices && col in boardState[row].indices) {
                                    // Якщо клітинка порожня, зафіксуємо її (значення -1)
                                    if (boardState[row][col] == 0 && fixedCellsCount < 2) {
                                        boardState = boardState.map { it.toMutableList() }.toMutableList().also {
                                            it[row][col] = -1
                                        }
                                    } else if (boardState[row][col] == -1) {
                                        boardState = boardState.map { it.toMutableList() }.toMutableList().also {
                                            it[row][col] = 0
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
                            drawCell(cellValue, textMeasure, c, r)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    if (markingFixed) {
                        Button(onClick = {
                            val fixedCellsCount = boardState.sumOf { row -> row.count { it == -1 } }
                            if (fixedCellsCount != 2) {
                                message = "Помилка!\nВи повинні вибрати рівно дві зафіксовані клітинки."
                            } else {
                                markingFixed = false
                                message = "Фіксація завершена.\nНатисніть «Розв’язати» для пошуку рішення."
                            }
                        }) {
                            Text("Готово")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (!markingFixed && solution == null) {
                        Button(
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF33FF57),
                            ),
                            onClick = {
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
                    }
                    if (solution != null) {
                        Button(
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFFFF5733),
                            ),
                            onClick = {
                                boardState = boardStructure.map { MutableList(it) { 0 } }.toMutableList()
                                markingFixed = true
                                solution = null
                                message =
                                    "Клацніть по клітинках для позначення орієнтирів\n(назва місяця та дата)."
                            }) {
                            Text("Скинути")
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawCell(
    cellValue: Int,
    textMeasurer: TextMeasurer,
    column: Int,
    row: Int
) {
    val topLeft = Offset(column * cellSize.toPx(), row * cellSize.toPx())
    val fillColor = when (cellValue) {
        -1 -> Color.Gray // зафіксована клітинка
        0 -> Color.Gray       // порожня
        else -> pieces.find { it.id == cellValue }?.color ?: Color.LightGray
    }

    drawRoundRect(
        color = fillColor,
        topLeft = topLeft + Offset(2F, 2F),
        size = Size(cellSize.toPx() - 4F, cellSize.toPx() - 4F),
        style = Stroke(width = 4f),
        cornerRadius = CornerRadius(4F, 4F)
    )
    drawRoundRect(
        color = fillColor.darken(15),
        topLeft = topLeft + Offset(4F, 4F),
        size = Size(cellSize.toPx() - 8F, cellSize.toPx() - 8F),
        style = Stroke(width = 5f),
        cornerRadius = CornerRadius(4F, 4F)
    )
    drawRoundRect(
        color = fillColor,
        topLeft = topLeft + Offset(5F, 5F),
        size = Size(cellSize.toPx() - 10F, cellSize.toPx() - 10F),
        cornerRadius = CornerRadius(4F, 4F)
    )

    if (cellValue == -1) {
        // Обводка вибраної клітинки
        drawRoundRect(
            color = Color.Red,
            topLeft = topLeft + Offset(6F, 6F),
            size = Size(cellSize.toPx() - 12F, cellSize.toPx() - 12F),
            style = Stroke(width = 12F),
            cornerRadius = CornerRadius(4F, 4F)
        )
    }

//    // Обводка клітинки
//    drawRect(
//        color = Color.Black,
//        topLeft = topLeft,
//        size = Size(cellSize.toPx(), cellSize.toPx()),
//        style = Stroke(width = 1.5f)
//    )

    if (cellValue <= 0) {
        if (row < 2) {
            val text = months.elementAt(row * boardStructure[row] + column)
            val textSize = textMeasurer.measure(text)
            drawText(
                textMeasurer = textMeasurer,
                text = text,
                style = TextStyle.Default.copy(color = Color.White, fontWeight = FontWeight.Bold),
                topLeft = Offset(
                    topLeft.x + (cellSize.toPx() - textSize.size.width) / 2,
                    topLeft.y + (cellSize.toPx() - textSize.size.height) / 2
                ),
            )
        } else {
            val currentNumber = boardStructure.take(row).sum() + column - 11
            val text = currentNumber.toString()
            val textSize = textMeasurer.measure(text)
            drawText(
                textMeasurer = textMeasurer,
                text = text,
                style = TextStyle.Default.copy(color = Color.White, fontWeight = FontWeight.Bold),
                topLeft = Offset(
                    topLeft.x + (cellSize.toPx() - textSize.size.width) / 2,
                    topLeft.y + (cellSize.toPx() - textSize.size.height) / 2
                ),
            )
        }
    }
}

@Composable
private fun PieceView(piece: Piece) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .size(40.dp, 30.dp),
        contentAlignment = Alignment.Center
    ) {
        piece.cells.forEach { (x, y) ->
            Box(
                modifier = Modifier
                    .offset(x = (x * 11).dp, y = (y * 11).dp)
                    .size(10.dp)
                    .background(piece.color)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PieceList(pieces: List<Piece>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.Center
    ) {
        pieces.forEach { piece ->
            PieceView(piece)
        }
    }
}

private fun Color.darken(percent: Int): Color {
    val newColor = Color(
        red = (red * (100 - percent) / 100).coerceIn(0f, 1f),
        green = (green * (100 - percent) / 100).coerceIn(0f, 1f),
        blue = (blue * (100 - percent) / 100).coerceIn(0f, 1f),
        alpha = alpha
    )

    return newColor
}