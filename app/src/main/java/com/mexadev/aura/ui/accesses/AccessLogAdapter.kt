package com.mexadev.aura.ui.accesses

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.mexadev.aura.R
import com.mexadev.aura.data.model.AccessLog
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
    private val onItemClick: (AccessLog) -> Unit
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
        if (holder is ItemViewHolder) {
            holder.bind(items[position], onItemClick)
        }
    }

    // ── ViewHolders ────────────────────────────────────────────────────

    class SkeletonViewHolder(view: View) : RecyclerView.ViewHolder(view)
    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view)

    class ItemViewHolder(private val b: ItemAccessLogBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(log: AccessLog, onClick: (AccessLog) -> Unit) {
            val ctx = b.root.context

            // Nombre principal: preferimos el nombre del invitado del código, luego tipo
            val guestLabel = log.accessCode?.guestName?.takeIf { it.isNotBlank() }
                ?: when (log.accessType.lowercase()) {
                    "qr"       -> "Acceso QR"
                    "vehicle"  -> "Acceso vehicular"
                    "facial"   -> "Reconocimiento facial"
                    "pin"      -> "Acceso PIN"
                    else       -> log.accessType.replaceFirstChar { it.uppercase() }
                }
            b.tvGuestName.text = guestLabel

            // Residencia
            val residenceName = log.residence?.name?.takeIf { it.isNotBlank() }
                ?: "Residencia #${log.residenceId ?: "–"}"
            b.tvResidence.text = residenceName

            // Timestamp relativo
            b.tvTimestamp.text = formatTimestamp(log.timestamp)

            // Status badge
            val (statusLabel, textColorRes, bgColorRes, iconRes) = when (log.status.lowercase()) {
                "granted" -> StatusStyle(
                    ctx.getString(R.string.access_log_status_granted),
                    R.color.aura_success, R.color.aura_success_light, R.drawable.ic_check
                )
                "denied" -> StatusStyle(
                    ctx.getString(R.string.access_log_status_denied),
                    R.color.aura_error, R.color.aura_error_light, R.drawable.ic_warning
                )
                else -> StatusStyle(
                    log.status.replaceFirstChar { it.uppercase() },
                    R.color.aura_text_tertiary, R.color.aura_surface_variant, R.drawable.ic_lock
                )
            }

            b.tvStatus.text = statusLabel
            b.tvStatus.setTextColor(ContextCompat.getColor(ctx, textColorRes))
            b.tvStatus.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24 * ctx.resources.displayMetrics.density
                setColor(ContextCompat.getColor(ctx, bgColorRes))
            }
            b.ivAccessLogIcon.setColorFilter(ContextCompat.getColor(ctx, textColorRes))
            b.ivAccessLogIcon.setImageResource(iconRes)

            b.root.setOnClickListener { onClick(log) }
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

    private data class StatusStyle(
        val label: String,
        val textColorRes: Int,
        val bgColorRes: Int,
        val iconRes: Int
    )
}
