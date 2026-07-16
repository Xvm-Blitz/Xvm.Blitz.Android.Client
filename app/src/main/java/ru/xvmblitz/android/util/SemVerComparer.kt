package ru.xvmblitz.android.util

object SemVerComparer {
    fun isLessThan(left: String?, right: String?): Boolean = compare(left, right) < 0

    fun compare(left: String?, right: String?): Int {
        val leftVersion = parse(left) ?: return 0
        val rightVersion = parse(right) ?: return 0

        for (index in 0 until 3) {
            val comparison = leftVersion.core[index].compareTo(rightVersion.core[index])
            if (comparison != 0) {
                return comparison
            }
        }

        val leftHasPreRelease = leftVersion.preRelease != null
        val rightHasPreRelease = rightVersion.preRelease != null

        if (!leftHasPreRelease && !rightHasPreRelease) {
            return 0
        }

        if (!leftHasPreRelease) {
            return 1
        }

        if (!rightHasPreRelease) {
            return -1
        }

        return comparePreRelease(leftVersion.preRelease, rightVersion.preRelease)
    }

    private fun comparePreRelease(left: String, right: String): Int {
        val leftParts = left.split('.')
        val rightParts = right.split('.')
        val maxLength = maxOf(leftParts.size, rightParts.size)

        for (index in 0 until maxLength) {
            if (index >= leftParts.size) {
                return -1
            }

            if (index >= rightParts.size) {
                return 1
            }

            val leftPart = leftParts[index]
            val rightPart = rightParts[index]
            val leftNumber = leftPart.toIntOrNull()
            val rightNumber = rightPart.toIntOrNull()

            if (leftNumber != null && rightNumber != null) {
                val numericComparison = leftNumber.compareTo(rightNumber)
                if (numericComparison != 0) {
                    return numericComparison
                }
                continue
            }

            if (leftNumber != null) {
                return -1
            }

            if (rightNumber != null) {
                return 1
            }

            val stringComparison = leftPart.compareTo(rightPart)
            if (stringComparison != 0) {
                return stringComparison
            }
        }

        return 0
    }

    private fun parse(value: String?): SemVersion? {
        if (value.isNullOrBlank()) {
            return null
        }

        var sanitized = value.trim()
        if (sanitized.startsWith('v') || sanitized.startsWith('V')) {
            sanitized = sanitized.substring(1)
        }

        val plusIndex = sanitized.indexOf('+')
        if (plusIndex >= 0) {
            sanitized = sanitized.substring(0, plusIndex)
        }

        var preRelease: String? = null
        val dashIndex = sanitized.indexOf('-')
        if (dashIndex >= 0) {
            preRelease = sanitized.substring(dashIndex + 1)
            sanitized = sanitized.substring(0, dashIndex)
        }

        val parts = sanitized.split('.')
        if (parts.size !in 1..3) {
            return null
        }

        val core = IntArray(3)
        for (index in parts.indices) {
            val number = parts[index].toIntOrNull()
            if (number == null || number < 0) {
                return null
            }
            core[index] = number
        }

        return SemVersion(core, preRelease)
    }

    private class SemVersion(
        val core: IntArray,
        val preRelease: String?,
    )
}
