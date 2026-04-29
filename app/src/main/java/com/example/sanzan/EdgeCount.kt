package com.example.sanzan

import org.opencv.core.Mat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

fun countEdges(edges: Mat, perpendicularDistance: Int = 5): IntArray {
    val width = edges.width()
    val height = edges.height()

    val cx = width / 2.0
    val cy = height / 2.0

    val offsets = mutableListOf<Pair<Double, Double>>()

    val buffer = ByteArray(width * height)
    edges.get(0, 0, buffer)

    for (x in 0 until width) {
        for (y in 0 until height) {
            val pixel = buffer[y * width + x].toInt() and 0xFF

            if (pixel > 0) {
                val dx = x - cx
                val dy = y - cy

                offsets.add(Pair(dx, dy))
            }
        }
    }

    val result = IntArray(360)

    for (theta in 0 until 360) {
        var count = 0

        val rad = theta * PI / 180
        val sinT = sin(rad)
        val cosT = cos(rad)

        for ((dx, dy) in offsets) {
            val distance = abs(dx * sinT - dy * cosT)
            if (distance <= perpendicularDistance) {
                count++
            }
        }

        result[theta] = count
    }

    return result
}