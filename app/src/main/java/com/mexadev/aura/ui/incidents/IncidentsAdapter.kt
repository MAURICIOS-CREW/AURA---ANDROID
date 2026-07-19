package com.mexadev.aura.ui.incidents

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.mexadev.aura.R
import com.mexadev.aura.data.model.Incident
import com.mexadev.aura.databinding.ItemIncidentBinding
import com.mexadev.aura.databinding.ItemIncidentSkeletonBinding
import com.mexadev.aura.databinding.LayoutIncidentFormBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * IncidentsAdapter
 *
 * Tipos de vista:
 *  - VIEW_TYPE_SKELETON : placeholder animado mientras carga
 *  - VIEW_TYPE_FORM     : formulario de creación (card expandida desde el FAB)
 *  - VIEW_TYPE_INCIDENT : card de incidente existente (toca → abre IncidentDetailActivity)
 *
 * Animación FAB → Card nueva:
 *  Mismo patrón que VehiclesAdapter: el Fragment pasa las coordenadas del centro
 *  del FAB; la card escala desde ese punto (scale 0→1 con pivote en el FAB).
 *
 * Principios SOLID:
 *  - SRP: solo renderiza la lista de incidentes
 *  - OCP: extensible con nuevos view types sin modificar los existentes
 *  - DIP: lambdas en lugar de referencias directas a Fragment/Activity
 */
class IncidentsAdapter(
    private var items: MutableList<Incident>,
    private val recyclerView: RecyclerView,
    private val onSubmit: (title: String, description: String?, setLoading: (Boolean) -> Unit) -> Unit,
    private val onFormMinimize: (hasContent: Boolean) -> Unit,
    private val onIncidentClick: (Incident, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_SKELETON = 0
        const val VIEW_TYPE_FORM     = 1
        const val VIEW_TYPE_INCIDENT = 2

        private const val FORM_ID = -1L
        private const val SKELETON_ID = -2L

        private const val TRANSITION_DURATION_MS    = 300L
        private const val CARD_ENTRANCE_DURATION_MS = 380L
    }

    private var isSkeletonMode = false
    private var pendingEntrancePos = -1
    private var fabCenterX = 0
    private var fabCenterY = 0

    // ─────────────────────────────────────────────────────────────────
    // Data helpers
    // ─────────────────────────────────────────────────────────────────

    fun showSkeletons(count: Int) {
        val oldSize = items.size
        isSkeletonMode = true
        items.clear()
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize)
        
        repeat(count) {
            items.add(Incident(SKELETON_ID, -1, "", null, "", null, null, null))
        }
        notifyItemRangeInserted(0, count)
    }

    fun updateData(newItems: List<Incident>) {
        val oldSize = items.size
        isSkeletonMode = false
        items.clear()
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize)
        
        items.addAll(newItems)
        pendingEntrancePos = -1
        notifyItemRangeInserted(0, newItems.size)
    }

    fun updateIncident(updatedIncident: Incident) {
        val index = items.indexOfFirst { it.id == updatedIncident.id && it.id != SKELETON_ID && it.id != FORM_ID }
        if (index != -1) {
            items[index] = updatedIncident
            notifyItemChanged(index)
        }
    }

    /**
     * Añade el formulario vacío al final y lanza la animación de entrada desde el FAB.
     */
    fun addFormCard(fabWindowCenterX: Int, fabWindowCenterY: Int) {
        if (isSkeletonMode) return
        // Si ya hay un formulario, no agregar otro
        if (items.any { it.id == FORM_ID }) return

        fabCenterX = fabWindowCenterX
        fabCenterY = fabWindowCenterY

        val formPlaceholder = Incident(FORM_ID, -1, "", null, "", null, null, null)
        items.add(formPlaceholder)
        val pos = items.size - 1

        pendingEntrancePos = pos
        notifyItemInserted(pos)

        recyclerView.post {
            recyclerView.smoothScrollToPosition(pos)
            recyclerView.postOnAnimation {
                recyclerView.postOnAnimation {
                    triggerEntranceAnimation(pos)
                }
            }
        }
    }

    fun removeFormCard() {
        val idx = items.indexOfFirst { it.id == FORM_ID }
        if (idx != -1) {
            items.removeAt(idx)
            beginTransition()
            notifyItemRemoved(idx)
        }
    }

    fun incidentCreated(newIncident: Incident) {
        val idx = items.indexOfFirst { it.id == FORM_ID }
        if (idx != -1) {
            items[idx] = newIncident
            beginTransition()
            notifyItemChanged(idx)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Adapter overrides
    // ─────────────────────────────────────────────────────────────────

    override fun getItemViewType(position: Int): Int = when (items[position].id) {
        SKELETON_ID -> VIEW_TYPE_SKELETON
        FORM_ID     -> VIEW_TYPE_FORM
        else        -> VIEW_TYPE_INCIDENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SKELETON -> SkeletonViewHolder(
                ItemIncidentSkeletonBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_FORM -> FormViewHolder(
                LayoutIncidentFormBinding.inflate(inflater, parent, false)
            )
            else -> IncidentViewHolder(
                ItemIncidentBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // Ocultar ANTES del primer frame si es la card de entrada pendiente
        if (position == pendingEntrancePos && holder !is SkeletonViewHolder) {
            holder.itemView.alpha  = 0f
            holder.itemView.scaleX = 0f
            holder.itemView.scaleY = 0f
        } else {
            holder.itemView.alpha  = 1f
            holder.itemView.scaleX = 1f
            holder.itemView.scaleY = 1f
            holder.itemView.translationY = 0f
        }

        when (holder) {
            is SkeletonViewHolder  -> holder.bind()
            is FormViewHolder      -> holder.bind()
            is IncidentViewHolder  -> holder.bind(items[position])
        }
    }

    override fun getItemCount(): Int = items.size

    // ─────────────────────────────────────────────────────────────────
    // Animations
    // ─────────────────────────────────────────────────────────────────

    private fun triggerEntranceAnimation(position: Int) {
        val vh = recyclerView.findViewHolderForAdapterPosition(position) ?: return
        val cardView = vh.itemView

        if (!cardView.isLaidOut || cardView.width == 0) {
            cardView.post { triggerEntranceAnimation(position) }
            return
        }

        val cardLoc = IntArray(2)
        cardView.getLocationInWindow(cardLoc)

        val pivotX = (fabCenterX - cardLoc[0]).toFloat().coerceIn(0f, cardView.width.toFloat())
        val pivotY = (fabCenterY - cardLoc[1]).toFloat().coerceIn(0f, cardView.height.toFloat())

        cardView.pivotX = pivotX
        cardView.pivotY = pivotY
        cardView.scaleX = 0f
        cardView.scaleY = 0f
        cardView.alpha  = 0f
        pendingEntrancePos = -1

        cardView.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(CARD_ENTRANCE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .withEndAction {
                cardView.pivotX = cardView.width  / 2f
                cardView.pivotY = cardView.height / 2f
            }
            .start()
    }

    private fun beginTransition() {
        val set = TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(ChangeBounds().apply {
                interpolator = FastOutSlowInInterpolator()
                duration = TRANSITION_DURATION_MS
            })
            addTransition(Fade().apply {
                duration = (TRANSITION_DURATION_MS * 0.7).toLong()
            })
        }
        TransitionManager.beginDelayedTransition(recyclerView, set)
    }

    // ─────────────────────────────────────────────────────────────────
    // ViewHolders
    // ─────────────────────────────────────────────────────────────────

    inner class SkeletonViewHolder(
        private val binding: ItemIncidentSkeletonBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            ObjectAnimator.ofFloat(binding.root, "alpha", 1f, 0.4f, 1f).apply {
                duration     = 1200
                repeatCount  = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }.start()
        }
    }

    inner class FormViewHolder(
        private val binding: LayoutIncidentFormBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            setLoading(false)

            binding.btnFormMinimize.setOnClickListener {
                val hasContent = binding.etTitle.text.isNotBlank() ||
                                 binding.etDescription.text.isNotBlank()
                onFormMinimize(hasContent)
            }

            binding.btnSubmit.setOnClickListener {
                val title = binding.etTitle.text.toString().trim()
                val desc  = binding.etDescription.text.toString().trim().takeIf { it.isNotBlank() }
                onSubmit(title, desc) { loading -> setLoading(loading) }
            }
        }

        private fun setLoading(loading: Boolean) {
            binding.btnSubmit.text = if (loading) "" else "Enviar Reporte"
            binding.btnSubmit.isEnabled = !loading
            binding.pbSubmit.visibility = if (loading) View.VISIBLE else View.GONE
            binding.etTitle.isEnabled = !loading
            binding.etDescription.isEnabled = !loading
            binding.btnFormMinimize.isEnabled = !loading
        }
    }

    inner class IncidentViewHolder(
        private val binding: ItemIncidentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(incident: Incident) {
            binding.tvIncidentTitle.text = incident.title
            binding.tvCreatedAt.text = formatDate(incident.createdAt)

            applyStatusStyle(incident.status)

            if (incident.comments != null && incident.comments.isNotEmpty()) {
                binding.layoutCommentsBadge.visibility = View.VISIBLE
                binding.tvCommentsCount.text = incident.comments.size.toString()
            } else {
                binding.layoutCommentsBadge.visibility = View.GONE
            }

            binding.cardIncident.setOnClickListener {
                onIncidentClick(incident, binding.cardIncident)
            }
        }

        private fun applyStatusStyle(status: String) {
            val context = binding.root.context
            val (label, textColor, bgColor) = statusStyle(status, context)

            binding.tvStatus.text = label
            binding.tvStatus.setTextColor(textColor)

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24 * context.resources.displayMetrics.density
                setColor(bgColor)
            }
            binding.tvStatus.background = bg

            // Ícono y color del contenedor cambian según el estado
            val (iconTint, containerBg) = iconStyle(status, context)
            binding.ivStatusIcon.imageTintList = ColorStateList.valueOf(iconTint)
            binding.iconContainer.backgroundTintList = ColorStateList.valueOf(containerBg)
        }

        private fun statusStyle(status: String, context: android.content.Context): Triple<String, Int, Int> =
            when (status) {
                "open"        -> Triple("Abierto",     context.getColor(R.color.aura_warning),      context.getColor(R.color.aura_warning_light))
                "viewed"      -> Triple("Visto",       context.getColor(R.color.aura_info),         context.getColor(R.color.aura_info_light))
                "in_progress" -> Triple("En proceso",  context.getColor(R.color.aura_primary),      context.getColor(R.color.aura_primary_surface))
                "attended"    -> Triple("Atendido",    context.getColor(R.color.aura_success),      context.getColor(R.color.aura_success_light))
                "cancelled"   -> Triple("Cancelado",   context.getColor(R.color.aura_text_tertiary),context.getColor(R.color.aura_surface_variant))
                else          -> Triple(status.replaceFirstChar { it.uppercase() },
                                        context.getColor(R.color.aura_text_secondary),
                                        context.getColor(R.color.aura_surface_variant))
            }

        private fun iconStyle(status: String, context: android.content.Context): Pair<Int, Int> =
            when (status) {
                "attended"  -> context.getColor(R.color.aura_success) to context.getColor(R.color.aura_success_light)
                "cancelled" -> context.getColor(R.color.aura_text_tertiary) to context.getColor(R.color.aura_surface_variant)
                else        -> context.getColor(R.color.aura_warning) to context.getColor(R.color.aura_warning_light)
            }

        private fun formatDate(isoDate: String?): String {
            if (isoDate == null) return ""
            return try {
                val inputFormat  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
                val outputFormat = SimpleDateFormat("d MMM yyyy", Locale("es", "MX"))
                val date = inputFormat.parse(isoDate) ?: return ""
                outputFormat.format(date)
            } catch (e: Exception) { "" }
        }
    }
}
