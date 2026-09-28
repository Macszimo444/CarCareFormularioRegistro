package com.example.carcareformularioregistro.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class GuidanceViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    val answerIds: List<String>
        get() = savedState.get<ArrayList<String>>(ANSWERS).orEmpty()

    fun answer(choiceId: String) {
        savedState[ANSWERS] = ArrayList(GuidanceFlow.answer(answerIds, choiceId))
    }

    fun back() {
        savedState[ANSWERS] = ArrayList(GuidanceFlow.back(answerIds))
    }

    fun restart() {
        savedState[ANSWERS] = arrayListOf<String>()
    }

    fun isExpanded(section: String): Boolean =
        savedState.get<ArrayList<String>>(EXPANDED).orEmpty().contains(section)

    fun toggleSection(section: String): Boolean {
        val expanded = savedState.get<ArrayList<String>>(EXPANDED).orEmpty().toMutableSet()
        val nowExpanded = if (section in expanded) {
            expanded.remove(section)
            false
        } else {
            expanded.add(section)
            true
        }
        savedState[EXPANDED] = ArrayList(expanded)
        return nowExpanded
    }

    companion object {
        private const val ANSWERS = "guidance_answer_ids"
        private const val EXPANDED = "guidance_expanded_sections"
    }
}
