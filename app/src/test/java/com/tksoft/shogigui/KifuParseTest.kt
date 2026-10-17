package com.tksoft.shogigui

import org.junit.Assert.*
import org.junit.Test

// 座標は Pair(row, col)。row 0 = 一段目, col 0 = 9筋
class KifuParseTest {
    private fun sq(file: Int, rank: Int) = Pair(rank - 1, 9 - file)
    private fun freshRoot() = KifuNode(createInitialBoard(), emptyMap(), emptyMap(), Player.SENTE)

    @Test
    fun usiSfenSetsStartPosition() {
        val root = freshRoot()
        val leaf = parseKifu("position sfen lnsgkgsn1/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL w - 1 moves 3c3d 7g7f", root, {})!!
        assertNull(root.board[sq(1, 1)])
        assertEquals(Player.GOTE, root.currentPlayer)
        assertEquals(2, leaf.moveCount)
        assertEquals(Player.GOTE, leaf.currentPlayer)
    }

    @Test
    fun usiSfenWithoutMovesLoadsPosition() {
        val root = freshRoot()
        assertNotNull(parseKifu("sfen 4k4/9/4P4/9/9/9/9/9/4K4 b G 1", root, {}))
        assertEquals(mapOf(PieceType.GOLD to 1), root.senteHand)
        assertEquals(3, root.board.size)
    }

    @Test
    fun kifHandicapRemovesPiecesAndGoteMovesFirst() {
        val kif = """
            手合割：二枚落ち
            手数----指手---------消費時間--
               1 ６二銀(71)
               2 ７六歩(77)
        """.trimIndent()
        val root = freshRoot()
        val leaf = parseKif(kif, root, {})!!
        assertNull(root.board[sq(8, 2)])
        assertNull(root.board[sq(2, 2)])
        assertEquals(Player.GOTE, root.currentPlayer)
        assertEquals(2, leaf.moveCount)
        assertEquals(Piece(PieceType.SILVER, Player.GOTE), leaf.board[sq(6, 2)])
    }

    @Test
    fun kifAcceptsHalfWidthFileDigits() {
        val kif = """
            手数----指手---------消費時間--
               1 7六歩(77)
               2 3四歩(33)
        """.trimIndent()
        val leaf = parseKif(kif, freshRoot(), {})!!
        assertEquals(2, leaf.moveCount)
        assertEquals(Piece(PieceType.PAWN, Player.GOTE), leaf.board[sq(3, 4)])
    }

    @Test
    fun csaCommaSeparatedMovesAreAllRead() {
        val csa = "PI\n+\n+7776FU,T3\n-3334FU,T5\n+2726FU,-8384FU\n%TORYO\n"
        val leaf = parseCsa(csa, freshRoot(), {})!!
        assertEquals(4, leaf.moveCount)
        assertEquals("後手勝ち（投了）", extractGameResult(csa))
    }

    @Test
    fun csaBoardRowsWithTrailingEmptySquareClearThe1File() {
        val e = " * "
        val rows = (1..9).joinToString("\n") { r ->
            val cells = MutableList(9) { e }
            if (r == 1) cells[4] = "-OU"
            if (r == 9) cells[4] = "+OU"
            "P$r" + cells.joinToString("")
        }
        val csa = "$rows\n+\n+5958OU\n"
        val root = freshRoot()
        parseCsa(csa, root, {})
        assertEquals(2, root.board.size)
    }
}
