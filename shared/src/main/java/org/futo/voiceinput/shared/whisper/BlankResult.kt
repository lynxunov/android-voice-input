package org.futo.voiceinput.shared.whisper

private fun createBlankResultPermutations(blankResults: List<String>): HashSet<String> {
    val r = blankResults.map { it.lowercase() }.toMutableList()
    r += r.map { it.replace("(", "[").replace(")", "]") }
    r += r.map { it.replace(" ", "_") }
    return r.map { it.lowercase() }.toHashSet()
}

private val EMPTY_RESULTS = createBlankResultPermutations(
    listOf("you", "(bell dings)", "(blank audio)", "(beep)", "(bell)", "(music)",
           "(music playing)", "♪", "♪♪")
)

fun isBlankResult(result: String): Boolean = EMPTY_RESULTS.contains(result)
