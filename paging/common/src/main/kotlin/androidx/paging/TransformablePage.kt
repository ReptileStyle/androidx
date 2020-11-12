/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.paging

internal data class TransformablePage<T : Any>(
    /**
     * List of original (pre-transformation) page offsets the original page relative to initial = 0,
     * that this [TransformablePage] depends on.
     *
     * This array is always sorted.
     */
    val originalPageOffsets: IntArray,

    /**
     * Data to present (post-transformation)
     */
    val data: List<T>,

    /**
     * Original (pre-transformation) page offset relative to initial = 0, that [hintOriginalIndices]
     * are associated with.
     */
    val hintOriginalPageOffset: Int,

    /**
     * Optional lookup table for page indices.
     *
     * If provided, this table provides a mapping from presentation index -> original,
     * pre-transformation index.
     *
     * If null, the indices of [data] map directly to their original pre-transformation index.
     *
     * Note: [hintOriginalIndices] refers to indices of the original item which can be found in the
     * loaded pages with pageOffset == [hintOriginalPageOffset].
     */
    val hintOriginalIndices: List<Int>?
) {
    /**
     * Simple constructor for creating pre-transformation pages, which don't need an index lookup
     * and only reference a single [originalPageOffset].
     */
    constructor(
        originalPageOffset: Int,
        data: List<T>
    ) : this(intArrayOf(originalPageOffset), data, originalPageOffset, null)

    init {
        require(originalPageOffsets.isNotEmpty()) {
            "originalPageOffsets cannot be empty when constructing TransformablePage"
        }

        require(hintOriginalIndices == null || hintOriginalIndices.size == data.size) {
            "If originalIndices (size = ${hintOriginalIndices!!.size}) is provided," +
                " it must be same length as data (size = ${data.size})"
        }
    }

    fun viewportHintFor(
        index: Int,
        presentedItemsBefore: Int,
        presentedItemsAfter: Int,
        originalPageOffsetFirst: Int,
        originalPageOffsetLast: Int
    ) = ViewportHint.Access(
        pageOffset = hintOriginalPageOffset,
        indexInPage = when {
            hintOriginalIndices?.indices?.contains(index) == true -> hintOriginalIndices[index]
            else -> index
        },
        presentedItemsBefore = presentedItemsBefore,
        presentedItemsAfter = presentedItemsAfter,
        originalPageOffsetFirst = originalPageOffsetFirst,
        originalPageOffsetLast = originalPageOffsetLast
    )

    // Do not edit. Implementation generated by Studio, since data class uses referential equality
    // for IntArray.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransformablePage<*>

        if (!originalPageOffsets.contentEquals(other.originalPageOffsets)) return false
        if (data != other.data) return false
        if (hintOriginalPageOffset != other.hintOriginalPageOffset) return false
        if (hintOriginalIndices != other.hintOriginalIndices) return false

        return true
    }

    // Do not edit. Implementation generated by Studio, since data class uses referential  equality
    // for IntArray.
    override fun hashCode(): Int {
        var result = originalPageOffsets.contentHashCode()
        result = 31 * result + data.hashCode()
        result = 31 * result + hintOriginalPageOffset
        result = 31 * result + (hintOriginalIndices?.hashCode() ?: 0)
        return result
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T : Any> empty() = EMPTY_INITIAL_PAGE as TransformablePage<T>
        val EMPTY_INITIAL_PAGE: TransformablePage<Any> = TransformablePage(0, emptyList())
    }
}
