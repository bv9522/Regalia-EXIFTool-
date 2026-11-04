package com.example.engine.policy

import com.example.data.model.DateCandidate
import com.example.data.model.MediaCategory

class DateCandidateSelector {

    private val photoPolicy = PhotoDatePolicy()
    private val videoPolicy = VideoDatePolicy()
    private val documentPolicy = DocumentDatePolicy()

    fun evaluate(
        candidates: List<DateCandidate>,
        mediaCategory: MediaCategory
    ): Pair<DateCandidate?, List<DateCandidate>> {
        val policy = when (mediaCategory) {
            MediaCategory.PHOTO -> photoPolicy
            MediaCategory.VIDEO -> videoPolicy
            MediaCategory.DOCUMENT -> documentPolicy
            MediaCategory.AUDIO, MediaCategory.OTHER -> photoPolicy // Default to photo-like ranking
        }

        return policy.selectBestCandidate(candidates, mediaCategory)
    }
}
