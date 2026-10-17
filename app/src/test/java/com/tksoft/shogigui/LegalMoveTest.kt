package com.tksoft.shogigui

import org.junit.Assert.*
import org.junit.Test

// 座標は Pair(row, col)。row 0 = 一段目, col 0 = 9筋
class LegalMoveTest {
    private fun sq(file: Int, rank: Int) = Pair(rank - 1, 9 - file)

    @Test
    fun pinnedPieceCannotLeaveKingInCheck() {
        val board = mapOf(
            sq(5, 9) to Piece(PieceType.KING, Player.SENTE),
            sq(5, 8) to Piece(PieceType.GOLD, Player.SENTE),
            sq(5, 1) to Piece(PieceType.ROOK, Player.GOTE),
            sq(1, 1) to Piece(PieceType.KING, Player.GOTE),
        )
        assertFalse(isLegalBoardMove(board, sq(5, 8), sq(4, 8)))
        assertTrue(isLegalBoardMove(board, sq(5, 8), sq(5, 7)))
    }

    @Test
    fun nifuAndDeadSquareDropsAreIllegal() {
        val board = createInitialBoard().toMutableMap().apply { remove(sq(5, 7)) } // 5筋の歩を除く
        assertFalse(isLegalDrop(board, PieceType.PAWN, Player.SENTE, sq(4, 5)))   // 4筋に歩がある
        assertTrue(isLegalDrop(board, PieceType.PAWN, Player.SENTE, sq(5, 5)))
        assertFalse(isLegalDrop(emptyMap(), PieceType.PAWN, Player.SENTE, sq(5, 1)))
        assertFalse(isLegalDrop(emptyMap(), PieceType.LANCE, Player.SENTE, sq(5, 1)))
        assertFalse(isLegalDrop(emptyMap(), PieceType.KNIGHT, Player.SENTE, sq(5, 2)))
        assertTrue(isLegalDrop(emptyMap(), PieceType.KNIGHT, Player.SENTE, sq(5, 3)))
        assertFalse(isLegalDrop(emptyMap(), PieceType.KNIGHT, Player.GOTE, sq(5, 8)))
    }

    @Test
    fun pawnDropMateIsIllegal() {
        val base = mapOf(
            sq(1, 1) to Piece(PieceType.KING, Player.GOTE),
            sq(2, 9) to Piece(PieceType.LANCE, Player.SENTE),   // 2筋を利かせて 2一・2二 へ逃げられなくする
            sq(9, 9) to Piece(PieceType.KING, Player.SENTE),
        )
        // 1二の歩に金の紐が付いていると玉で取れず詰み → 打ち歩詰め
        val withGold = base + (sq(1, 3) to Piece(PieceType.GOLD, Player.SENTE))
        assertFalse(isLegalDrop(withGold, PieceType.PAWN, Player.SENTE, sq(1, 2)))
        // 紐が無ければ玉で歩を取れるので打てる
        assertTrue(isLegalDrop(base, PieceType.PAWN, Player.SENTE, sq(1, 2)))
    }

    @Test
    fun horseMovesDiagonallyBothWays() {
        val horse = Piece(PieceType.BISHOP, Player.SENTE, isPromoted = true)
        val board = mapOf(sq(5, 5) to horse)
        assertTrue(isValidMovePattern(sq(5, 5), sq(8, 2), horse, board))
        assertTrue(isValidMovePattern(sq(5, 5), sq(2, 2), horse, board))
        assertTrue(isValidMovePattern(sq(5, 5), sq(8, 8), horse, board))
        assertTrue(isValidMovePattern(sq(5, 5), sq(2, 8), horse, board))
    }

    @Test
    fun kifFunariIsNotPromotion() {
        val kif = """
            手数----指手---------消費時間--
               1 ７六歩(77)
               2 ３四歩(33)
               3 ２二角不成(88)
        """.trimIndent()
        val root = KifuNode(createInitialBoard(), emptyMap(), emptyMap(), Player.SENTE)
        val leaf = parseKif(kif, root, {})!!
        assertEquals(Piece(PieceType.BISHOP, Player.SENTE, false), leaf.board[sq(2, 2)])
    }

    @Test
    fun kifVariationBranchesFromItsMove() {
        val kif = """
            手数----指手---------消費時間--
               1 ７六歩(77)
               2 ３四歩(33)
               3 ２六歩(27)
               4 ８四歩(83)
            まで4手で後手の勝ち

            変化：3手
               3 ６六歩(67)
               4 ８四歩(83)
        """.trimIndent()
        val root = KifuNode(createInitialBoard(), emptyMap(), emptyMap(), Player.SENTE)
        val mainEnd = parseKif(kif, root, {})!!
        assertEquals(4, mainEnd.moveCount)
        assertEquals("8四歩", mainEnd.moveLabel)
        val afterMove2 = root.children[0].children[0]
        assertEquals(listOf("2六歩", "6六歩"), afterMove2.children.map { it.moveLabel })
        assertEquals(1, afterMove2.children[1].children.size)
    }
}
