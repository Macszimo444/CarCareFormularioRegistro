package com.example.carcareformularioregistro.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.databinding.ActivityGuidanceBinding
import com.example.carcareformularioregistro.databinding.ItemGuidanceCardBinding
import com.google.android.material.button.MaterialButton

class GuidanceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGuidanceBinding
    private val model: GuidanceViewModel by viewModels()
    private val mode by lazy { intent.getStringExtra(EXTRA_MODE) ?: MODE_ASSISTANT }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityGuidanceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val padding = intArrayOf(binding.root.paddingLeft, binding.root.paddingTop,
            binding.root.paddingRight, binding.root.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(padding[0] + bars.left, padding[1] + bars.top,
                padding[2] + bars.right, padding[3] + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.btnGuidanceBack.setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this) {
            if (mode == MODE_ASSISTANT && model.answerIds.isNotEmpty()) {
                model.back()
                renderAssistant()
            } else {
                finish()
            }
        }
        when (mode) {
            MODE_GUIDE -> renderSections(R.string.guidance_guide_title,
                R.string.guidance_guide_intro, GuidanceContent.guide)
            MODE_PRIVACY -> renderSections(R.string.guidance_privacy_title,
                R.string.guidance_privacy_intro, GuidanceContent.privacy)
            MODE_HELP -> renderSections(R.string.guidance_help_title,
                R.string.guidance_help_intro, GuidanceContent.help)
            else -> renderAssistant()
        }
    }

    private fun renderAssistant() {
        binding.tvGuidanceTitle.setText(R.string.guidance_assistant_title)
        binding.tvGuidanceIntro.setText(R.string.guidance_assistant_intro)
        binding.containerGuidance.removeAllViews()
        val conversation = GuidanceFlow.conversation(model.answerIds)
        conversation.answered.forEach { answer ->
            addCard(answer.step.title, getString(R.string.guidance_answered, answer.choice.label)).apply {
                root.setCardBackgroundColor(color(R.color.carcare_field))
                tvGuidanceCardTitle.setTextColor(color(R.color.carcare_text_secondary))
                tvGuidanceCardTitle.textSize = 13f
            }
        }
        val step = conversation.current
        val currentCard = addCard(step.title, step.body)
        when (step.attention) {
            GuidanceFlow.Attention.STOP -> {
                currentCard.root.strokeColor = color(R.color.carcare_danger)
                currentCard.tvGuidanceCardTitle.setTextColor(color(R.color.carcare_danger))
            }
            GuidanceFlow.Attention.REVIEW -> {
                currentCard.root.strokeColor = color(R.color.carcare_warning)
                currentCard.tvGuidanceCardTitle.setTextColor(color(R.color.carcare_warning))
            }
            GuidanceFlow.Attention.INFORMATION -> Unit
        }
        step.choices.forEach { choice ->
            addAction(currentCard.containerGuidanceCardActions, choice.label) {
                model.answer(choice.id)
                renderAssistant()
            }
        }
        if (step.isResult) {
            addCard(
                getString(if (step.attention == GuidanceFlow.Attention.STOP)
                    R.string.guidance_stop_label else R.string.guidance_review_label),
                getString(R.string.guidance_result_limit)
            )
            addAction(binding.containerGuidance, getString(R.string.guidance_restart)) {
                model.restart()
                renderAssistant()
            }
        }
        if (conversation.answered.isNotEmpty()) {
            addAction(binding.containerGuidance, getString(R.string.guidance_previous)) {
                model.back()
                renderAssistant()
            }
        }
        if (step.isResult && step.sourceIds.isNotEmpty()) addSources(step.sourceIds)
        // Wait for the rebuilt transcript to be measured. Animated auto-scroll can consume
        // the next answer's touch while settling, and a posted runnable may see top == 0.
        binding.scrollGuidance.doOnPreDraw {
            if (currentCard.root.parent === binding.containerGuidance) {
                binding.scrollGuidance.scrollTo(0, currentCard.root.top)
            }
        }
    }

    private fun renderSections(title: Int, intro: Int, sections: List<GuidanceContent.Section>) {
        binding.tvGuidanceTitle.setText(title)
        binding.tvGuidanceIntro.setText(intro)
        binding.containerGuidance.removeAllViews()
        sections.forEach { section ->
            val card = addCard(section.title, section.body)
            if (mode != MODE_PRIVACY) {
                fun updateExpanded(button: MaterialButton) {
                    val expanded = model.isExpanded(section.title)
                    card.tvGuidanceCardBody.visibility = if (expanded) View.VISIBLE else View.GONE
                    button.setText(if (expanded) R.string.guidance_collapse else R.string.guidance_expand)
                    button.contentDescription = getString(
                        if (expanded) R.string.guidance_collapse_named else R.string.guidance_expand_named,
                        section.title
                    )
                }
                val button = addAction(card.containerGuidanceCardActions, "") { }
                button.setOnClickListener {
                    model.toggleSection(section.title)
                    updateExpanded(button)
                }
                updateExpanded(button)
            }
        }
        val sourceIds = sections.flatMap { it.sourceIds }.distinct()
        if (sourceIds.isNotEmpty()) addSources(sourceIds)
    }

    private fun addCard(title: String, body: String): ItemGuidanceCardBinding =
        ItemGuidanceCardBinding.inflate(layoutInflater, binding.containerGuidance, false).also {
            it.tvGuidanceCardTitle.text = title
            it.tvGuidanceCardBody.text = body
            ViewCompat.setAccessibilityHeading(it.tvGuidanceCardTitle, true)
            binding.containerGuidance.addView(it.root)
        }

    private fun addAction(container: LinearLayout, label: String, onClick: () -> Unit): MaterialButton =
        (layoutInflater.inflate(R.layout.item_guidance_action, container, false) as MaterialButton).also {
            it.text = label
            it.setOnClickListener { onClick() }
            container.addView(it)
        }

    private fun addSources(ids: List<String>) {
        val card = addCard(getString(R.string.guidance_sources_title), getString(R.string.guidance_sources_body))
        GuidanceContent.sources.filter { it.id in ids }.forEach { source ->
            addAction(card.containerGuidanceCardActions, source.title) { openSource(source) }
                .contentDescription = getString(R.string.guidance_external_source, source.title)
        }
    }

    private fun openSource(source: GuidanceContent.Source) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            })
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.guidance_source_unavailable, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.guidance_source_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun color(resource: Int) = ContextCompat.getColor(this, resource)

    companion object {
        const val EXTRA_MODE = "guidance_mode"
        const val MODE_ASSISTANT = "assistant"
        const val MODE_GUIDE = "guide"
        const val MODE_PRIVACY = "privacy"
        const val MODE_HELP = "help"
    }
}
