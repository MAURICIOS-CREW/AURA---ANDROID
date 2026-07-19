package com.mexadev.aura.ui.incidents

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mexadev.aura.R
import com.mexadev.aura.data.model.IncidentComment
import com.mexadev.aura.databinding.ItemCommentBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * CommentsAdapter
 *
 * Renderiza el hilo de comentarios de un incidente en estilo chat:
 *  - Comentarios del usuario autenticado → burbuja DERECHA (color primario)
 *  - Comentarios de otros (admin) → burbuja IZQUIERDA (fondo neutro)
 *
 * Principios SOLID:
 *  - SRP: solo responsable de renderizar comentarios
 *  - OCP: extensible vía currentUserId sin modificar la clase
 */
class CommentsAdapter(
    private val items: MutableList<IncidentComment>,
    var currentUserId: Long
) : RecyclerView.Adapter<CommentsAdapter.CommentViewHolder>() {

    fun updateData(newItems: List<IncidentComment>) {
        val oldSize = items.size
        items.clear()
        items.addAll(newItems)
        if (oldSize == 0) {
            notifyDataSetChanged()
        } else {
            notifyItemRangeInserted(oldSize, newItems.size - oldSize)
        }
    }

    fun addComment(comment: IncidentComment) {
        items.add(comment)
        notifyItemInserted(items.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CommentViewHolder(
        private val binding: ItemCommentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: IncidentComment) {
            // Si comment.user es null (ej. respuesta de un POST nuestro sin relaciones), asumimos que es nuestro.
            val isMine = comment.user == null || comment.user.id == currentUserId
            val context = binding.root.context

            binding.tvCommentContent.text = comment.content
            binding.tvCommentTime.text = formatTime(comment.createdAt)

            // Todos los comentarios se alinean a la izquierda (zona de comentarios, no chat)
            binding.commentRoot.gravity = Gravity.START
            
            // Nombre del autor visible para todos
            val authorName = comment.user?.name ?: if (isMine) "Tú" else "Administrador"
            binding.tvAuthorName.text = authorName
            binding.tvAuthorName.visibility = View.VISIBLE

            if (isMine) {
                // Burbuja propia: color primario
                binding.bubbleContainer.backgroundTintList = ColorStateList.valueOf(
                    context.getColor(R.color.aura_primary_surface)
                )
                binding.tvCommentContent.setTextColor(
                    context.getColor(R.color.aura_text_primary)
                )
                binding.tvCommentTime.setTextColor(
                    context.getColor(R.color.aura_text_tertiary)
                )
            } else {
                // Burbuja de admin/otros: color institucional
                binding.bubbleContainer.backgroundTintList = ColorStateList.valueOf(
                    context.getColor(R.color.aura_info_light)
                )
                binding.tvCommentContent.setTextColor(
                    context.getColor(R.color.aura_text_primary)
                )
                binding.tvCommentTime.setTextColor(
                    context.getColor(R.color.aura_text_tertiary)
                )
            }
        }

        /**
         * Formatea el timestamp ISO 8601 a "HH:mm" para mostrar en la burbuja.
         * Si el comentario es de hoy, solo muestra la hora; si es de otro día,
         * muestra "d MMM".
         */
        private fun formatTime(isoDate: String?): String {
            if (isoDate == null) return ""
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
                val date = inputFormat.parse(isoDate) ?: return ""

                val now = java.util.Calendar.getInstance()
                val cal = java.util.Calendar.getInstance().apply { time = date }

                val isToday = now.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR) &&
                              now.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR)

                val outputFormat = if (isToday) {
                    SimpleDateFormat("HH:mm", Locale.getDefault())
                } else {
                    SimpleDateFormat("d MMM", Locale.getDefault())
                }
                outputFormat.format(date)
            } catch (e: Exception) {
                ""
            }
        }
    }
}
