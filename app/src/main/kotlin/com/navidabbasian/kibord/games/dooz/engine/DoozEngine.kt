package com.navidabbasian.kibord.games.dooz.engine

import kotlin.random.Random

/** مهره‌ی دوز: ضربدر یا دایره */
enum class DoozMark {
    X, O;

    val other: DoozMark get() = if (this == X) O else X
}

/** سختی ربات */
enum class DoozDifficulty { EASY, MEDIUM, HARD }

/** نتیجه‌ی یک برد: چه کسی و روی کدام خط (سه خانه‌ی ۰ تا ۸) */
data class DoozWin(val mark: DoozMark, val line: List<Int>)

/**
 * صفحه‌ی ۳×۳ دوز — فهرستی تغییرناپذیر از ۹ خانه (ردیف‌به‌ردیف، از بالا-چپ).
 * کل منطق قوانین همین‌جاست و هیچ وابستگی اندرویدی ندارد.
 */
class DoozBoard private constructor(val cells: List<DoozMark?>) {

    constructor() : this(List(9) { null })

    operator fun get(index: Int): DoozMark? = cells[index]

    /** خانه‌های خالی */
    val emptyCells: List<Int> get() = cells.indices.filter { cells[it] == null }

    val isFull: Boolean get() = cells.all { it != null }

    /** برنده (اگر هست) همراه با خط برنده */
    val winner: DoozWin?
        get() {
            for (line in LINES) {
                val a = cells[line[0]] ?: continue
                if (a == cells[line[1]] && a == cells[line[2]]) return DoozWin(a, line)
            }
            return null
        }

    /** مساوی: پر شده و برنده‌ای نیست */
    val isDraw: Boolean get() = winner == null && isFull

    /** دست تمام شده؟ (برد یا مساوی) */
    val isOver: Boolean get() = winner != null || isFull

    fun canPlace(index: Int): Boolean = index in 0..8 && cells[index] == null

    /** گذاشتن مهره؛ روی خانه‌ی پر یا خارج از صفحه خطا می‌دهد */
    fun place(index: Int, mark: DoozMark): DoozBoard {
        require(canPlace(index)) { "خانه‌ی $index آزاد نیست" }
        return DoozBoard(cells.mapIndexed { i, m -> if (i == index) mark else m })
    }

    override fun equals(other: Any?): Boolean = other is DoozBoard && other.cells == cells
    override fun hashCode(): Int = cells.hashCode()
    override fun toString(): String = cells.chunked(3).joinToString("\n") { row ->
        row.joinToString(" ") { it?.name ?: "." }
    }

    companion object {
        /** هشت خط برد: سه سطر، سه ستون، دو قطر */
        val LINES: List<List<Int>> = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6),
        )

        /** ساخت از رشته‌ی ۹ حرفی مثل "XO..X.O.." — برای تست و دیباگ */
        fun of(pattern: String): DoozBoard {
            require(pattern.length == 9) { "الگو باید ۹ حرف باشد" }
            return DoozBoard(pattern.map { c ->
                when (c) {
                    'X', 'x' -> DoozMark.X
                    'O', 'o' -> DoozMark.O
                    else -> null
                }
            })
        }
    }
}

/**
 * ربات دوز در سه سطح:
 * - آسون: بیشتر تصادفی، گاهی (یک‌سوم مواقع) برد/سد فوری را می‌بیند
 * - معمولی: اگر بتواند می‌بَرد، اگر باید سد می‌کند، وگرنه تصادفی (با کمی علاقه به مرکز)
 * - سخت: مینی‌مکس کامل — هرگز نمی‌بازد
 */
class DoozBot(private val random: Random = Random.Default) {

    fun chooseMove(board: DoozBoard, me: DoozMark, difficulty: DoozDifficulty): Int {
        val empties = board.emptyCells
        require(empties.isNotEmpty()) { "صفحه پر است" }
        if (empties.size == 1) return empties.first()
        return when (difficulty) {
            DoozDifficulty.EASY -> easyMove(board, me, empties)
            DoozDifficulty.MEDIUM -> mediumMove(board, me, empties)
            DoozDifficulty.HARD -> hardMove(board, me)
        }
    }

    private fun easyMove(board: DoozBoard, me: DoozMark, empties: List<Int>): Int {
        if (random.nextInt(3) == 0) {
            immediateWin(board, me)?.let { return it }
            immediateWin(board, me.other)?.let { return it }
        }
        return empties[random.nextInt(empties.size)]
    }

    private fun mediumMove(board: DoozBoard, me: DoozMark, empties: List<Int>): Int {
        immediateWin(board, me)?.let { return it }
        immediateWin(board, me.other)?.let { return it }
        if (board.canPlace(4) && random.nextInt(2) == 0) return 4
        return empties[random.nextInt(empties.size)]
    }

    /** بهترین حرکت با مینی‌مکس؛ بین حرکت‌های هم‌ارزش تصادفی انتخاب می‌کند تا تکراری نباشد */
    private fun hardMove(board: DoozBoard, me: DoozMark): Int {
        // صفحه‌ی خالی: همه‌ی گوشه‌ها و مرکز هم‌ارزش‌اند؛ بی‌خودی درخت کامل را نگردیم
        if (board.emptyCells.size == 9) {
            val openers = listOf(0, 2, 4, 6, 8)
            return openers[random.nextInt(openers.size)]
        }
        var bestScore = Int.MIN_VALUE
        val best = mutableListOf<Int>()
        for (cell in board.emptyCells) {
            val score = minimax(board.place(cell, me), me, me.other, depth = 1)
            if (score > bestScore) {
                bestScore = score
                best.clear()
                best += cell
            } else if (score == bestScore) {
                best += cell
            }
        }
        return best[random.nextInt(best.size)]
    }

    /** امتیاز صفحه از دید [me]؛ نوبتِ [turn] است. برد زودتر امتیاز بیشتری دارد */
    private fun minimax(board: DoozBoard, me: DoozMark, turn: DoozMark, depth: Int): Int {
        board.winner?.let { win -> return if (win.mark == me) 10 - depth else depth - 10 }
        if (board.isFull) return 0
        val maximizing = turn == me
        var best = if (maximizing) Int.MIN_VALUE else Int.MAX_VALUE
        for (cell in board.emptyCells) {
            val score = minimax(board.place(cell, turn), me, turn.other, depth + 1)
            best = if (maximizing) maxOf(best, score) else minOf(best, score)
            // هرس ساده: بهتر از این نمی‌شود
            if (maximizing && best >= 10 - depth - 1) break
            if (!maximizing && best <= depth + 1 - 10) break
        }
        return best
    }

    companion object {
        /** خانه‌ای که گذاشتن [mark] در آن همین حالا می‌بَرد، یا null */
        fun immediateWin(board: DoozBoard, mark: DoozMark): Int? =
            board.emptyCells.firstOrNull { board.place(it, mark).winner?.mark == mark }
    }
}
