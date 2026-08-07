package com.mexadev.aura.ui.accesses

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.mexadev.aura.R
import com.mexadev.aura.data.model.*
import com.mexadev.aura.databinding.ItemAccessLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * AccessLogAdapter
 *
 * Adapter para la lista de registros del historial de accesos.
 * Soporta tres tipos de vistas:
 *   - VIEW_TYPE_SKELETON: placeholder animado mientras carga la primera página
 *   - VIEW_TYPE_ITEM: tarjeta de datos real del AccessLog
 *   - VIEW_TYPE_LOADING_FOOTER: spinner al fondo para indicar carga de página siguiente
 *
 * Usa DiffUtil para actualizaciones atómicas sin parpadeos.
 */
class AccessLogAdapter(
    private val onItemClick: (AccessLog, ItemAccessLogBinding) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SKELETON = 0
        private const val VIEW_TYPE_ITEM     = 1
        private const val VIEW_TYPE_FOOTER   = 2
    }

    private val items = mutableListOf<AccessLog>()
    private var skeletonCount = 0
    private var showLoadingFooter = false

    // ── Skeleton ───────────────────────────────────────────────────────

    /**
     * Muestra [count] skeletons en lugar de los items reales.
     * Usa notificaciones específicas para evitar un rebind completo del LayoutManager.
     */
    fun showSkeletons(count: Int) {
        val oldCount = itemCount   // puede incluir footer
        skeletonCount = count
        showLoadingFooter = false
        items.clear()
        val newCount = itemCount   // == count

        when {
            newCount > oldCount -> {
                // Cambiamos los que ya había y añadimos los extras
                notifyItemRangeChanged(0, oldCount)
                notifyItemRangeInserted(oldCount, newCount - oldCount)
            }
            newCount < oldCount -> {
                notifyItemRangeChanged(0, newCount)
                notifyItemRangeRemoved(newCount, oldCount - newCount)
            }
            else -> notifyItemRangeChanged(0, newCount)
        }
    }

    // ── Actualización atómica de datos con DiffUtil ────────────────────

    /**
     * Reemplaza la lista completa usando DiffUtil para actualizaciones atómicas.
     * El parámetro [animate] controla si se detecta y transmite el movimiento de items;
     * en ambos casos se usan notificaciones específicas (nunca notifyDataSetChanged).
     */
    fun updateData(newItems: List<AccessLog>, showFooter: Boolean = false, animate: Boolean = true) {
        val oldSkeletonCount = skeletonCount
        skeletonCount = 0
        showLoadingFooter = showFooter

        // Si veníamos de modo skeleton, los view types cambian por completo:
        // necesitamos comunicarle al adapter que las posiciones anteriores
        // (que eran skeletons) ahora son items reales.
        if (oldSkeletonCount > 0) {
            // Eliminamos los skeletons y luego insertamos los items reales
            val realCount = newItems.size + if (showFooter) 1 else 0
            items.clear()
            items.addAll(newItems)
            notifyItemRangeRemoved(0, oldSkeletonCount)
            if (realCount > 0) notifyItemRangeInserted(0, realCount)
            return
        }

        // Transición normal item→item: usamos DiffUtil
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos].id == newItems[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos] == newItems[newPos]
        }, animate /* detectMoves */)
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)

        // Sincronizar footer después de diff dispatch (si la lista no está vacía)
        if (itemCount > 0) notifyItemChanged(itemCount - 1)
    }

    /**
     * Agrega la siguiente página al final de la lista.
     * El footer (si existe) está en la posición items.size del adapter.
     */
    fun appendPage(newItems: List<AccessLog>, hasMore: Boolean) {
        // La posición de inserción es el tamaño actual de items (el footer estaba ahí)
        val insertStart = items.size
        val hadFooter = showLoadingFooter
        showLoadingFooter = hasMore
        items.addAll(newItems)

        if (hadFooter) {
            // El footer se convierte en el primer item nuevo; los demás son inserciones
            notifyItemChanged(insertStart)                              // footer → primer item
            if (newItems.size > 1) {
                notifyItemRangeInserted(insertStart + 1, newItems.size - 1)
            }
        } else {
            notifyItemRangeInserted(insertStart, newItems.size)
        }

        // Si ahora hay footer nuevo, notificar
        if (hasMore) notifyItemChanged(itemCount - 1)
    }

    fun setLoadingFooter(visible: Boolean) {
        val changed = showLoadingFooter != visible
        showLoadingFooter = visible
        if (changed) notifyItemChanged(itemCount - 1)
    }

    // ── RecyclerView overrides ─────────────────────────────────────────

    override fun getItemViewType(position: Int): Int = when {
        skeletonCount > 0 -> VIEW_TYPE_SKELETON
        position == items.size && showLoadingFooter -> VIEW_TYPE_FOOTER
        else -> VIEW_TYPE_ITEM
    }

    override fun getItemCount(): Int = when {
        skeletonCount > 0 -> skeletonCount
        showLoadingFooter -> items.size + 1
        else -> items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SKELETON -> SkeletonViewHolder(
                inflater.inflate(R.layout.item_access_log_skeleton, parent, false)
            )
            VIEW_TYPE_FOOTER -> FooterViewHolder(
                inflater.inflate(R.layout.item_access_log_skeleton, parent, false)
            )
            else -> {
                val binding = ItemAccessLogBinding.inflate(inflater, parent, false)
                ItemViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ItemViewHolder -> holder.bind(items[position], onItemClick)
            is SkeletonViewHolder -> holder.bind()
            is FooterViewHolder -> holder.bind()
        }
    }

    // ── ViewHolders ────────────────────────────────────────────────────

    class SkeletonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            ObjectAnimator.ofFloat(itemView, "alpha", 1f, 0.4f, 1f).apply {
                duration     = 1200
                repeatCount  = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }.start()
        }
    }

    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            ObjectAnimator.ofFloat(itemView, "alpha", 1f, 0.4f, 1f).apply {
                duration     = 1200
                repeatCount  = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }.start()
        }
    }

    class ItemViewHolder(val b: ItemAccessLogBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(log: AccessLog, onClick: (AccessLog, ItemAccessLogBinding) -> Unit) {
            val ctx = b.root.context

            val cardTransName = "access_log_card_${log.id}"
            val nameTransName = "access_log_name_${log.id}"
            val iconTransName = "access_log_icon_${log.id}"
            val statusTransName = "access_log_status_${log.id}"

            b.cardAccessLog.transitionName = cardTransName
            b.tvGuestName.transitionName = nameTransName
            b.ivAccessLogIcon.transitionName = iconTransName
            b.tvStatus.transitionName = statusTransName

            // Nombre principal: vehículo con detalles o nombre del invitado / tipo de acceso
            b.tvGuestName.text = log.getDisplayTitle()

            // Residencia formateada
            b.tvResidence.text = log.getFormattedResidence()

            // Timestamp relativo
            b.tvTimestamp.text = formatTimestamp(log.timestamp)

            // Icono según tipo de acceso: vehículo -> ic_car, QR -> ic_qr_code
            val iconRes = when {
                log.isVehicleAccess() -> R.drawable.ic_car
                log.isQrAccess() -> R.drawable.ic_qr_code
                log.accessType.equals("facial", ignoreCase = true) -> R.drawable.ic_profile
                else -> R.drawable.ic_qr_code
            }

            // Estilo de status (Permitido = Verde, Denegado = Rojo)
            val (statusLabel, textColorRes, bgColorRes) = when (log.status.lowercase()) {
                "granted", "permitido" -> Triple(
                    ctx.getString(R.string.access_log_status_granted),
                    R.color.aura_success, R.color.aura_success_light
                )
                "denied", "denegado" -> Triple(
                    ctx.getString(R.string.access_log_status_denied),
                    R.color.aura_error, R.color.aura_error_light
                )
                else -> Triple(
                    log.status.replaceFirstChar { it.uppercase() },
                    R.color.aura_text_tertiary, R.color.aura_surface_variant
                )
            }

            b.tvStatus.text = statusLabel
            b.tvStatus.setTextColor(ContextCompat.getColor(ctx, textColorRes))
            b.tvStatus.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24 * ctx.resources.displayMetrics.density
                setColor(ContextCompat.getColor(ctx, bgColorRes))
            }

            // Ícono tintado y contenedor de ícono con color de fondo del status (Verde o Rojo)
            b.ivAccessLogIcon.setImageResource(iconRes)
            b.ivAccessLogIcon.setColorFilter(ContextCompat.getColor(ctx, textColorRes))
            b.iconContainer.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * ctx.resources.displayMetrics.density
                setColor(ContextCompat.getColor(ctx, bgColorRes))
            }

            b.root.setOnClickListener { onClick(log, b) }
        }

        /**
         * Formatea el timestamp ISO en texto relativo legible:
         * "Hoy, HH:mm" / "Ayer, HH:mm" / "dd MMM, HH:mm"
         */
        private fun formatTimestamp(raw: String): String {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val date = sdf.parse(raw) ?: return raw
                val now = Date()
                val diffMs = now.time - date.time
                val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)
                val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                val timeStr = timeSdf.format(date)
                when {
                    diffDays == 0L -> "Hoy, $timeStr"
                    diffDays == 1L -> "Ayer, $timeStr"
                    diffDays < 7   -> {
                        val daySdf = SimpleDateFormat("EEE", Locale.Builder().setLanguage("es").setRegion("MX").build())
                        "${daySdf.format(date).replaceFirstChar { it.uppercase() }}, $timeStr"
                    }
                    else -> {
                        val dateSdf = SimpleDateFormat("dd MMM", Locale.Builder().setLanguage("es").setRegion("MX").build())
                        "${dateSdf.format(date)}, $timeStr"
                    }
                }
            } catch (_: Exception) {
                raw
            }
        }
    }

}
