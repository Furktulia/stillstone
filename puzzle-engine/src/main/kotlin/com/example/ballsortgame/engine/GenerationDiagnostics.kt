package com.example.ballsortgame.engine

enum class RejectionReason {
    ALREADY_SOLVED, TOO_FEW_BOUNDARIES, TOO_FEW_EMPTY_TUBES, REPEATED_CANDIDATE,
    PROVEN_UNSOLVABLE, SEARCH_TIME, SEARCH_EXPANSIONS, SEARCH_STORED_STATES, SEARCH_FRONTIER,
    OPTIMALITY_UNPROVEN, BELOW_MINIMUM_MOVES, ABOVE_MAXIMUM_MOVES, MINIMUM_UNPROVEN, MAXIMUM_UNPROVEN,
    STRUCTURAL_DUPLICATE, DIVERSITY_COMPARISON_LIMIT, CANCELLED,
}

/** One terminal classification per started attempt; search effort includes rejected candidates. */
data class GenerationDiagnostics(
    val attempts: Int,
    val evaluatedCandidates: Int,
    val acceptedCandidates: Int,
    val rejections: Map<RejectionReason, Int>,
    val expandedStates: Long,
    val searchMillis: Long,
) {
    init {
        require(attempts >= 0 && evaluatedCandidates in 0..attempts)
        require(acceptedCandidates in 0..1)
        require(rejections.values.all { it > 0 })
        require(attempts == acceptedCandidates + rejections.values.sum())
        require(expandedStates >= 0 && searchMillis >= 0)
    }
}
