package com.glidvio.app.sponsor

/** Categories supported by SponsorBlock's skip action. */
enum class SponsorCategory(val apiName: String, val label: String) {
    SPONSOR("sponsor", "Sponsors"),
    SELFPROMO("selfpromo", "Self promotion"),
    INTERACTION("interaction", "Subscribe and like reminders"),
    INTRO("intro", "Intros"),
    OUTRO("outro", "Outros"),
    PREVIEW("preview", "Previews and recaps"),
    MUSIC_OFFTOPIC("music_offtopic", "Non music sections"),
    FILLER("filler", "Filler"),
    ;

    companion object {
        val defaults: Set<SponsorCategory> = setOf(SPONSOR, SELFPROMO, INTERACTION)

        fun fromApiName(name: String): SponsorCategory? = entries.firstOrNull { it.apiName == name }
    }
}

data class Segment(
    val startSeconds: Double,
    val endSeconds: Double,
    val category: SponsorCategory,
    val uuid: String,
) {
    /** Invalid service data must never cause an arbitrary playback seek. */
    val isValid: Boolean
        get() = startSeconds.isFinite() && endSeconds.isFinite() &&
            startSeconds >= 0.0 && endSeconds > startSeconds && uuid.isNotBlank()
}

sealed interface SponsorLoadResult {
    /** An empty list means the service has no matching community submissions. */
    data class Available(val segments: List<Segment>) : SponsorLoadResult

    /** Distinct from no submissions: protected playback waits for a successful retry. */
    data class Unavailable(val message: String, val httpStatus: Int? = null) : SponsorLoadResult
}
