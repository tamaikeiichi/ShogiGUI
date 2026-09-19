package com.tksoft.shogigui

import org.junit.Test
import org.junit.Assert.*

class CsaHandicapTest {
    @Test
    fun piShorthandRemovesCorrectSquares_twoPieceHandicap() {
        val csa = """
            V2.2
            N+Sente
            N-Gote
            PI82HI22KA
            +
            +2726FU
            -3334FU
            %TORYO
        """.trimIndent()
        val root = KifuNode(createInitialBoard(), emptyMap(), emptyMap(), Player.SENTE)
        val leaf = parseCsa(csa, root, {})
        assertNotNull(leaf)

        // gote's rook (file8 rank2) and bishop (file2 rank2) must be removed
        assertNull(root.board[Pair(1, 1)])
        assertNull(root.board[Pair(1, 7)])
        // sente's own rook/bishop remain untouched
        assertEquals(PieceType.ROOK, root.board[Pair(7, 7)]?.type)
        assertEquals(PieceType.BISHOP, root.board[Pair(7, 1)]?.type)
        // rest of gote's back rank untouched
        assertEquals(PieceType.KING, root.board[Pair(0, 4)]?.type)
        assertEquals(Player.SENTE, root.currentPlayer)
        assertEquals(2, leaf!!.moveCount)
    }

    @Test
    fun bulkFormatRoundTrip_lanceHandicap() {
        val customBoard = createInitialBoard().toMutableMap()
        customBoard.remove(Pair(0, 0)) // remove one of gote's lances
        val originalRoot = KifuNode(customBoard, emptyMap(), emptyMap(), Player.SENTE)
        val csa = exportMainLineToCsa(originalRoot, "Sente", "Gote")

        val importedRoot = KifuNode(createInitialBoard(), emptyMap(), emptyMap(), Player.SENTE)
        parseCsa(csa, importedRoot, {})

        assertEquals(customBoard.toMap(), importedRoot.board)
    }
}
