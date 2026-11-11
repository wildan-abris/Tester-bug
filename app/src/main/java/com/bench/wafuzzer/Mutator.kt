package com.bench.wafuzzer

import kotlin.random.Random

object Mutator {

    private val CONTROL_CHARS = listOf(
        "\u0000", "\u0001", "\u0007", "\u001b", "\u007f",
        "%00", "%0a", "%0d", "%c0%80", "%e0%80%80"
    )

    private val PATH_TRICKS = listOf(
        "/..", "/../..", "//", "/%2e%2e/", "/./",
        "/\u0000.png", "/%00.png"
    )

    fun mutateUri(seed: String, iterations: Int): List<String> {
        val rng = Random(seed.hashCode().toLong())
        val out = mutableListOf<String>()
        for (i in 0 until iterations) {
            var m = seed
            when (i % 6) {
                0 -> m = seed + CONTROL_CHARS[rng.nextInt(CONTROL_CHARS.size)]
                1 -> m = seed + PATH_TRICKS[rng.nextInt(PATH_TRICKS.size)]
                2 -> m = seed + "?" + "k=v&".repeat(rng.nextInt(1, 64))
                3 -> m = seed + "%" + "%02X".format(rng.nextInt(256))
                4 -> m = seed + "\u0000".repeat(rng.nextInt(1, 32))
                5 -> m = seed.dropLast(1) + CONTROL_CHARS[rng.nextInt(CONTROL_CHARS.size)]
            }
            out += m
        }
        return out
    }

    fun mutateMime(seed: String, iterations: Int): List<String> {
        val rng = Random(seed.hashCode().toLong())
        val out = mutableListOf<String>()
        for (i in 0 until iterations) {
            out += when (i % 4) {
                0 -> seed
                1 -> "$seed;charset=\u0000"
                2 -> "$seed/../../.."
                3 -> seed + "x".repeat(rng.nextInt(64, 1024))
                else -> seed
            }
        }
        return out
    }
}
