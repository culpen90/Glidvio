package com.glidvio.app.sponsor

data class SkipWindow(
    val startSeconds: Double,
    val endSeconds: Double,
    val segments: List<Segment>,
) {
    val uuids: Set<String> get() = segments.mapTo(linkedSetOf()) { it.uuid }
    val categories: Set<SponsorCategory> get() = segments.mapTo(linkedSetOf()) { it.category }
}

data class SkipDecision(
    val startSeconds: Double,
    val endSeconds: Double,
    val segments: List<Segment>,
    /** Position observed before the automatic seek, used by Undo. */
    val positionSeconds: Double,
) {
    val uuids: Set<String> get() = segments.mapTo(linkedSetOf()) { it.uuid }
    val categories: Set<SponsorCategory> get() = segments.mapTo(linkedSetOf()) { it.category }
}

data class UndoResult(val positionSeconds: Double, val ignoredUuids: Set<String>)

/** Pure planning functions. Player state and per-video Undo state belong to the caller. */
object SkipPlanner {
    /**
     * Merge overlaps and exact adjacency, without skipping any gap in unmarked content.
     * A known duration bounds seeks. Invalid duration metadata is treated as unknown.
     */
    fun normalize(segments: List<Segment>, durationSeconds: Double? = null): List<SkipWindow> {
        val duration = durationSeconds?.takeIf { it.isFinite() && it > 0.0 }
        val valid = segments.asSequence()
            .filter { it.isValid }
            .mapNotNull { segment ->
                val end = duration?.let { minOf(it, segment.endSeconds) } ?: segment.endSeconds
                if (end <= segment.startSeconds) null else segment.copy(endSeconds = end)
            }
            .distinct()
            .sortedWith(compareBy<Segment> { it.startSeconds }.thenBy { it.endSeconds }
                .thenBy { it.uuid }.thenBy { it.category.apiName })
            .toList()
        if (valid.isEmpty()) return emptyList()

        val windows = mutableListOf<SkipWindow>()
        var start = valid.first().startSeconds
        var end = valid.first().endSeconds
        var members = mutableListOf(valid.first())
        for (segment in valid.drop(1)) {
            if (segment.startSeconds <= end) {
                end = maxOf(end, segment.endSeconds)
                members += segment
            } else {
                windows += SkipWindow(start, end, members.toList())
                start = segment.startSeconds
                end = segment.endSeconds
                members = mutableListOf(segment)
            }
        }
        windows += SkipWindow(start, end, members.toList())
        return windows
    }

    /**
     * A window includes its start and excludes its end. Seeks into a marked window
     * also skip; no seek is suggested before the first marked timestamp.
     */
    fun nextSkip(
        positionSeconds: Double,
        segments: List<Segment>,
        ignoredUuids: Set<String> = emptySet(),
        durationSeconds: Double? = null,
    ): SkipDecision? {
        if (!positionSeconds.isFinite() || positionSeconds < 0.0) return null
        val window = normalize(segments, durationSeconds).firstOrNull {
            positionSeconds >= it.startSeconds && positionSeconds < it.endSeconds &&
                it.uuids.none(ignoredUuids::contains)
        } ?: return null
        return SkipDecision(window.startSeconds, window.endSeconds, window.segments, positionSeconds)
    }

    /**
     * Keep these ignored UUIDs until a different video loads. Suppress all members
     * of an overlapping window so another submission cannot immediately skip it again.
     */
    fun undo(decision: SkipDecision, alreadyIgnoredUuids: Set<String> = emptySet()): UndoResult =
        UndoResult(decision.positionSeconds, alreadyIgnoredUuids + decision.uuids)
}
