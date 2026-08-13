package ru.xvmblitz.android.overlay

import android.graphics.RectF
import kotlin.math.abs

class OverlayInteractiveHitTester {
    private val lock = Any()
    private val regions = mutableListOf<RectF>()
    private val players = mutableMapOf<Long, RectF>()
    private var padX = 0f
    private var extraY = 0f

    fun setSlop(horizontalPx: Float, verticalPx: Float) {
        synchronized(lock) {
            padX = horizontalPx
            extraY = verticalPx
        }
    }

    fun replace(newRegions: Collection<RectF>) {
        synchronized(lock) {
            regions.clear()
            regions.addAll(newRegions.map { rect -> RectF(rect) })
        }
    }

    fun replacePlayers(newPlayers: Map<Long, RectF>) {
        synchronized(lock) {
            players.clear()
            newPlayers.forEach { (playerId, rect) ->
                players[playerId] = RectF(rect)
            }
        }
    }

    fun contains(x: Float, y: Float): Boolean {
        synchronized(lock) {
            return regions.any { rect -> rect.contains(x, y) }
        }
    }

    fun playerAt(x: Float, y: Float): Long? {
        synchronized(lock) {
            if (players.isEmpty()) {
                return null
            }
            val inHorizontal = players.values.any { rect ->
                x >= rect.left - padX && x <= rect.right + padX
            }
            if (!inHorizontal) {
                return null
            }
            val minTop = players.values.minOf { rect -> rect.top } - extraY
            val maxBottom = players.values.maxOf { rect -> rect.bottom } + extraY
            if (y < minTop || y > maxBottom) {
                return null
            }
            return players.minBy { (_, rect) -> abs(y - rect.centerY()) }.key
        }
    }
}
