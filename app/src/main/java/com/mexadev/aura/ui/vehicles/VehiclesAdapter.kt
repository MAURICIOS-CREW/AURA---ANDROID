package com.mexadev.aura.ui.vehicles

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import com.mexadev.aura.data.model.Vehicle
import com.mexadev.aura.databinding.ItemVehicleBinding
import com.mexadev.aura.databinding.ItemVehicleSkeletonBinding
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

/**
 * VehiclesAdapter
 *
 * Tipos de vista:
 *  - VIEW_TYPE_SKELETON : placeholder animado mientras carga
 *  - VIEW_TYPE_NORMAL   : card colapsada / formulario expandido
 *
 * Animación FAB → Card nueva:
 *  El Fragment pasa las coordenadas del centro del FAB en pantalla.
 *  El adapter registra la posición pendiente de entrada y, en
 *  onBindViewHolder, pone alpha=0 / scale=0 ANTES del primer frame
 *  (elimina el parpadeo). Tras el layout, pivotX/Y se fijan al punto
 *  donde estaba el FAB y se hace scale 0→1 con DecelerateInterpolator,
 *  dando la ilusión de que la card "nace" desde el FAB.
 */
class VehiclesAdapter(
    private var items: MutableList<Vehicle>,
    private val recyclerView: RecyclerView,
    private val onSave: (Vehicle, String, String, String, (Boolean) -> Unit) -> Unit,
    private val onDelete: (Vehicle, (Boolean) -> Unit) -> Unit,
    private val onFormMinimize: (Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_NORMAL   = 1
        const val VIEW_TYPE_SKELETON = 2

        private const val TRANSITION_DURATION_MS     = 320L
        private const val CARD_ENTRANCE_DURATION_MS  = 380L

        val STANDARD_COLORS = listOf(
            "#FFFFFF", "#000000", "#2196F3", "#F44336", "#9C27B0", "#FF9800", "#4CAF50", "#C0C0C0", "#FFD700"
        )

        fun parseColorSafe(colorStr: String, default: String): Int {
            val clean = if (!colorStr.startsWith("#")) "#$colorStr" else colorStr
            return try { Color.parseColor(clean) } catch (e: Exception) { Color.parseColor(default) }
        }

        fun getPastelColor(colorStr: String): Int {
            val color = parseColorSafe(colorStr, "#E3F2FD")
            return ColorUtils.blendARGB(color, Color.WHITE, 0.85f)
        }

        fun getStrongColor(colorStr: String): Int {
            val color = parseColorSafe(colorStr, "#1976D2")
            return ColorUtils.blendARGB(color, Color.BLACK, 0.2f)
        }
    }

    /** Posición actualmente expandida; -1 si ninguna. */
    var expandedPosition = -1
        private set

    private var isSkeletonMode = false

    /**
     * Posición de la card cuya animación de entrada está pendiente.
     * Se asigna ANTES de notifyItemInserted para que onBindViewHolder
     * pueda leer el flag en el mismo frame y evitar el parpadeo.
     */
    private var pendingEntrancePos = -1

    /** Coordenadas del centro del FAB en pantalla (window coordinates). */
    private var fabCenterX = 0
    private var fabCenterY = 0

    // ─────────────────────────────────────────────────────────────────
    // Data
    // ─────────────────────────────────────────────────────────────────

    fun showSkeletons(count: Int) {
        isSkeletonMode = true
        items.clear()
        expandedPosition = -1
        pendingEntrancePos = -1
        repeat(count) {
            items.add(Vehicle(id = -2L, residenceId = -1L, plate = "",
                brand = null, color = null, createdAt = null, updatedAt = null))
        }
        notifyDataSetChanged()
    }

    fun updateData(newItems: List<Vehicle>) {
        isSkeletonMode = false
        items.clear()
        items.addAll(newItems)
        expandedPosition = -1
        pendingEntrancePos = -1
        notifyDataSetChanged()
    }

    /**
     * Añade un vehículo nuevo vacío y lo expande.
     *
     * @param fabWindowCenterX  Centro X del FAB en coordenadas de ventana.
     * @param fabWindowCenterY  Centro Y del FAB en coordenadas de ventana.
     */
    fun addEmptyVehicle(fabWindowCenterX: Int, fabWindowCenterY: Int) {
        if (isSkeletonMode) return

        fabCenterX = fabWindowCenterX
        fabCenterY = fabWindowCenterY

        // Colapsar el anterior sin animación de transición para no interferir
        if (expandedPosition != -1) {
            val prev = expandedPosition
            expandedPosition = -1
            notifyItemChanged(prev)
        }

        val emptyVehicle = Vehicle(
            id = -1, residenceId = -1,
            plate = "", brand = "", color = "",
            createdAt = null, updatedAt = null
        )
        items.add(emptyVehicle)
        val pos = items.size - 1

        // ⚠️ CRÍTICO: asignar ANTES de notify para que onBindViewHolder
        //    lo lea en el mismo frame y ponga alpha=0 sin parpadeo.
        expandedPosition   = pos
        pendingEntrancePos = pos

        notifyItemInserted(pos)

        // Scroll y luego disparar la animación de entrada desde el FAB
        recyclerView.post {
            recyclerView.smoothScrollToPosition(pos)
            // Esperar el layout después del scroll
            recyclerView.postOnAnimation {
                recyclerView.postOnAnimation {
                    triggerEntranceAnimation(pos)
                }
            }
        }
    }

    fun collapseCurrent() {
        if (expandedPosition == -1) return
        val prev = expandedPosition
        expandedPosition = -1
        val vehicle = items[prev]
        if (vehicle.id == -1L) {
            items.removeAt(prev)
            beginExpansionTransition()
            notifyItemRemoved(prev)
        } else {
            beginExpansionTransition()
            notifyItemChanged(prev)
        }
    }

    fun vehicleCreated(newVehicle: Vehicle) {
        if (expandedPosition != -1 && items[expandedPosition].id == -1L) {
            items[expandedPosition] = newVehicle
            val pos = expandedPosition
            expandedPosition = -1 // collapse it
            beginExpansionTransition()
            notifyItemChanged(pos)
        }
    }

    fun vehicleUpdated(updatedVehicle: Vehicle) {
        if (expandedPosition != -1 && items[expandedPosition].id == updatedVehicle.id) {
            items[expandedPosition] = updatedVehicle
            val pos = expandedPosition
            expandedPosition = -1
            beginExpansionTransition()
            notifyItemChanged(pos)
        } else {
            val idx = items.indexOfFirst { it.id == updatedVehicle.id }
            if (idx != -1) {
                items[idx] = updatedVehicle
                notifyItemChanged(idx)
            }
        }
    }

    fun vehicleDeleted(vehicle: Vehicle) {
        val idx = items.indexOfFirst { it.id == vehicle.id }
        if (idx != -1) {
            if (expandedPosition == idx) {
                expandedPosition = -1
                beginExpansionTransition()
            } else if (expandedPosition > idx) {
                expandedPosition--
            }
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Adapter overrides
    // ─────────────────────────────────────────────────────────────────

    override fun getItemViewType(position: Int): Int =
        if (items[position].id == -2L) VIEW_TYPE_SKELETON else VIEW_TYPE_NORMAL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SKELETON) {
            SkeletonViewHolder(ItemVehicleSkeletonBinding.inflate(inflater, parent, false))
        } else {
            VehicleViewHolder(ItemVehicleBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is VehicleViewHolder -> {
                // ⚠️ Poner invisible ANTES de bind() para que el primer frame
                //    ya tenga alpha=0 → sin parpadeo alguno.
                if (position == pendingEntrancePos) {
                    holder.itemView.alpha  = 0f
                    holder.itemView.scaleX = 0f
                    holder.itemView.scaleY = 0f
                } else {
                    // Asegurarse de que vistas reutilizadas no hereden estado anterior
                    holder.itemView.alpha  = 1f
                    holder.itemView.scaleX = 1f
                    holder.itemView.scaleY = 1f
                    holder.itemView.translationY = 0f
                }
                holder.bind(items[position], position)
            }
            is SkeletonViewHolder -> holder.bind()
        }
    }

    override fun getItemCount(): Int = items.size

    // ─────────────────────────────────────────────────────────────────
    // Animations
    // ─────────────────────────────────────────────────────────────────

    /**
     * Dispara la animación de entrada de la card recién insertada.
     * Mapea el centro del FAB a coordenadas locales de la card,
     * y escala desde 0 usando ese punto como pivote.
     */
    private fun triggerEntranceAnimation(position: Int) {
        val vh = recyclerView.findViewHolderForAdapterPosition(position) ?: return
        val cardView = vh.itemView

        if (!cardView.isLaidOut || cardView.width == 0) {
            // Aún no medida → reintentar en el próximo frame
            cardView.post { triggerEntranceAnimation(position) }
            return
        }

        // Coordenadas de la card en ventana
        val cardLoc = IntArray(2)
        cardView.getLocationInWindow(cardLoc)

        // Centro del FAB relativo a la card
        val pivotX = (fabCenterX - cardLoc[0]).toFloat().coerceIn(0f, cardView.width.toFloat())
        val pivotY = (fabCenterY - cardLoc[1]).toFloat().coerceIn(0f, cardView.height.toFloat())

        // Fijar pivote al punto FAB y animar escala 0 → 1
        cardView.pivotX = pivotX
        cardView.pivotY = pivotY
        cardView.scaleX = 0f
        cardView.scaleY = 0f
        cardView.alpha  = 0f

        pendingEntrancePos = -1   // ya no se necesita el flag

        cardView.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(CARD_ENTRANCE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .withEndAction {
                // Restaurar pivote al centro para que transforms futuras sean normales
                cardView.pivotX = cardView.width  / 2f
                cardView.pivotY = cardView.height / 2f
            }
            .start()
    }

    /**
     * Inicia una transición de expansión/colapso suave.
     * ChangeBounds mueve/redimensiona las cards vecinas;
     * Fade muestra/oculta las secciones internas.
     */
    private fun beginExpansionTransition() {
        val set = TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(ChangeBounds().apply {
                interpolator = FastOutSlowInInterpolator()
                duration = TRANSITION_DURATION_MS
            })
            addTransition(Fade().apply {
                duration = (TRANSITION_DURATION_MS * 0.75).toLong()
            })
        }
        TransitionManager.beginDelayedTransition(recyclerView, set)
    }

    // ─────────────────────────────────────────────────────────────────
    // ViewHolders
    // ─────────────────────────────────────────────────────────────────

    inner class SkeletonViewHolder(
        private val binding: ItemVehicleSkeletonBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            ObjectAnimator.ofFloat(binding.root, "alpha", 1f, 0.4f, 1f).apply {
                duration     = 1200
                repeatCount  = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }.start()
        }
    }

    inner class VehicleViewHolder(
        private val binding: ItemVehicleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var selectedColorHex: String = ""

        fun bind(vehicle: Vehicle, position: Int) {
            val isExpanded = position == expandedPosition

            // ── Estado colapsado ──
            binding.tvPlate.text = vehicle.plate.takeIf { it.isNotBlank() } ?: "Nuevo Vehículo"
            val brand = vehicle.brand.takeIf { !it.isNullOrBlank() } ?: "Sin Marca"
            val color = vehicle.color.takeIf { !it.isNullOrBlank() }
            
            if (vehicle.id == -1L) {
                binding.tvBrandColor.text = "Completar datos"
                binding.vColorDot.visibility = View.GONE
            } else {
                binding.tvBrandColor.text = brand
                if (color != null) {
                    binding.vColorDot.visibility = View.VISIBLE
                    binding.vColorDot.backgroundTintList = ColorStateList.valueOf(parseColorSafe(color, "#FFFFFF"))
                } else {
                    binding.vColorDot.visibility = View.GONE
                }
            }

            selectedColorHex = vehicle.color ?: ""
            setupColorSelector()
            applyColorsToCard(selectedColorHex)

            // ── Estado expandido: prellenar formulario ──
            if (isExpanded) {
                binding.tvFormTitle.text = if (vehicle.id == -1L) "Nuevo Vehículo" else "Editar Vehículo"
                binding.etPlate.setText(vehicle.plate)
                binding.etBrand.setText(vehicle.brand)
                binding.layoutDelete.visibility = if (vehicle.id == -1L) View.GONE else View.VISIBLE
            }

            binding.layoutCollapsed.visibility = if (isExpanded) View.GONE else View.VISIBLE
            binding.layoutExpanded.visibility  = if (isExpanded) View.VISIBLE else View.GONE

            // Resetear estados de carga por reciclaje
            setLoadingSave(false)
            setLoadingDelete(false)

            // ── Listeners ──
            binding.layoutCollapsed.setOnClickListener { onCardClick(position) }

            binding.btnFormMinimize.setOnClickListener {
                if (detectChanges(vehicle)) onFormMinimize(true)
                else collapseCurrent()
            }

            binding.btnSave.setOnClickListener {
                onSave(
                    vehicle,
                    binding.etPlate.text.toString().trim(),
                    binding.etBrand.text.toString().trim(),
                    selectedColorHex
                ) { isLoading ->
                    setLoadingSave(isLoading)
                }
            }

            binding.btnDelete.setOnClickListener {
                onDelete(vehicle) { isLoading ->
                    setLoadingDelete(isLoading)
                }
            }
        }

        private fun setupColorSelector() {
            binding.llColorContainer.removeAllViews()
            val context = binding.root.context
            val size = (36 * context.resources.displayMetrics.density).toInt()
            val margin = (8 * context.resources.displayMetrics.density).toInt()

            for (colorHex in STANDARD_COLORS) {
                val dot = ImageView(context)
                val params = LinearLayout.LayoutParams(size, size)
                params.marginEnd = margin
                dot.layoutParams = params
                
                val bg = android.graphics.drawable.GradientDrawable()
                bg.shape = android.graphics.drawable.GradientDrawable.OVAL
                bg.setColor(Color.parseColor(colorHex))
                
                val isSelected = colorHex.equals(selectedColorHex, ignoreCase = true) || 
                                 (!selectedColorHex.startsWith("#") && ("#$selectedColorHex").equals(colorHex, ignoreCase = true))
                if (isSelected) {
                    bg.setStroke((3 * context.resources.displayMetrics.density).toInt(), Color.parseColor("#1976D2"))
                } else {
                    bg.setStroke((1 * context.resources.displayMetrics.density).toInt(), Color.LTGRAY)
                }
                dot.background = bg
                dot.setOnClickListener {
                    selectedColorHex = colorHex
                    setupColorSelector()
                    applyColorsToCard(selectedColorHex)
                }
                binding.llColorContainer.addView(dot)
            }

            // Botón Custom
            val customDot = ImageView(context)
            val customParams = LinearLayout.LayoutParams(size, size)
            customParams.marginEnd = margin
            customDot.layoutParams = customParams
            
            val customBg = android.graphics.drawable.GradientDrawable()
            customBg.shape = android.graphics.drawable.GradientDrawable.OVAL
            
            val isCustomSelected = selectedColorHex.isNotEmpty() && STANDARD_COLORS.none { 
                it.equals(selectedColorHex, ignoreCase = true) || it.equals("#$selectedColorHex", ignoreCase = true) 
            }
            
            if (isCustomSelected) {
                customBg.setColor(parseColorSafe(selectedColorHex, "#FFFFFF"))
                customBg.setStroke((3 * context.resources.displayMetrics.density).toInt(), Color.parseColor("#1976D2"))
                customDot.setImageDrawable(null)
            } else {
                customBg.setColor(Color.WHITE)
                customBg.setStroke((1 * context.resources.displayMetrics.density).toInt(), Color.LTGRAY)
                customDot.setImageResource(com.mexadev.aura.R.drawable.ic_add)
                val padding = (8 * context.resources.displayMetrics.density).toInt()
                customDot.setPadding(padding, padding, padding, padding)
            }
            
            customDot.background = customBg
            customDot.setOnClickListener {
                showColorPickerDialog()
            }
            binding.llColorContainer.addView(customDot)
        }

        private fun showColorPickerDialog() {
            val context = binding.root.context
            ColorPickerDialog.Builder(context)
                .setTitle("Elige un color")
                .setPreferenceName("ColorPickerDialog")
                .setPositiveButton("Confirmar", ColorEnvelopeListener { envelope, _ ->
                    var hex = envelope.hexCode
                    if (hex.length >= 8) {
                        hex = "#" + hex.substring(2)
                    } else if (!hex.startsWith("#")) {
                        hex = "#$hex"
                    }
                    selectedColorHex = hex
                    setupColorSelector()
                    applyColorsToCard(selectedColorHex)
                })
                .setNegativeButton("Cancelar") { dialogInterface, _ ->
                    dialogInterface.dismiss()
                }
                .attachAlphaSlideBar(false)
                .attachBrightnessSlideBar(true)
                .show()
        }

        private fun applyColorsToCard(colorHex: String) {
            val icon = binding.iconContainer.getChildAt(0) as? ImageView ?: return
            if (colorHex.isEmpty()) {
                binding.iconContainer.backgroundTintList = null
                icon.imageTintList = null
                return
            }
            
            val pastel = getPastelColor(colorHex)
            val strong = getStrongColor(colorHex)
            
            binding.iconContainer.backgroundTintList = ColorStateList.valueOf(pastel)
            icon.imageTintList = ColorStateList.valueOf(strong)
        }

        private fun setLoadingSave(isLoading: Boolean) {
            binding.btnSave.text = if (isLoading) "" else "Guardar"
            binding.btnSave.isEnabled = !isLoading
            binding.pbSave.visibility = if (isLoading) View.VISIBLE else View.GONE
            
            binding.btnDelete.isEnabled = !isLoading
            binding.btnFormMinimize.isEnabled = !isLoading
            binding.etPlate.isEnabled = !isLoading
            binding.etBrand.isEnabled = !isLoading
        }

        private fun setLoadingDelete(isLoading: Boolean) {
            binding.btnDelete.text = if (isLoading) "" else "Eliminar vehículo"
            binding.btnDelete.isEnabled = !isLoading
            binding.pbDelete.visibility = if (isLoading) View.VISIBLE else View.GONE
            
            binding.btnSave.isEnabled = !isLoading
            binding.btnFormMinimize.isEnabled = !isLoading
            binding.etPlate.isEnabled = !isLoading
            binding.etBrand.isEnabled = !isLoading
        }

        private fun onCardClick(position: Int) {
            val prev = expandedPosition
            expandedPosition = position
            beginExpansionTransition()
            if (prev != -1 && prev != position) notifyItemChanged(prev)
            notifyItemChanged(position)
            recyclerView.post { recyclerView.smoothScrollToPosition(position) }
        }

        private fun detectChanges(vehicle: Vehicle): Boolean {
            val p = binding.etPlate.text.toString().trim()
            val b = binding.etBrand.text.toString().trim()
            val c = selectedColorHex
            return if (vehicle.id != -1L) {
                p != vehicle.plate || b != (vehicle.brand ?: "") || c != (vehicle.color ?: "")
            } else {
                p.isNotEmpty() || b.isNotEmpty() || c.isNotEmpty()
            }
        }
    }
}
