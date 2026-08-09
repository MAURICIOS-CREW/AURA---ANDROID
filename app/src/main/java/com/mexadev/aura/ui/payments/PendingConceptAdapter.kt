package com.mexadev.aura.ui.payments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mexadev.aura.R
import com.mexadev.aura.data.model.PendingPaymentItem
import com.mexadev.aura.databinding.ItemPendingPaymentSelectionBinding

/**
 * Adapter para seleccionar conceptos a pagar.
 * Mantiene en memoria el estado de selección de cada item.
 */
class PendingConceptAdapter(
    private val items: List<PendingPaymentItem>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<PendingConceptAdapter.ConceptViewHolder>() {

    private val selectedItemIds = mutableSetOf<String>()

    init {
        // Por defecto todos los conceptos están seleccionados
        items.forEachIndexed { index, item ->
            selectedItemIds.add(getItemKey(item, index))
        }
    }

    private fun getItemKey(item: PendingPaymentItem, position: Int): String {
        return item.id?.toString() ?: "${item.type}_$position"
    }

    fun getSelectedItems(): List<PendingPaymentItem> {
        return items.filterIndexed { index, item ->
            selectedItemIds.contains(getItemKey(item, index))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConceptViewHolder {
        val binding = ItemPendingPaymentSelectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ConceptViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConceptViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class ConceptViewHolder(
        private val binding: ItemPendingPaymentSelectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PendingPaymentItem, position: Int) {
            val context = binding.root.context
            val itemKey = getItemKey(item, position)

            binding.tvConceptTitle.text = item.title
            binding.tvConceptAmount.text = context.getString(R.string.payments_balance_format, item.amount)

            binding.tvConceptBadge.text = when (item.type) {
                "monthly_fee" -> "Cuota Mensual"
                "contracted_service" -> "Servicio Contratado"
                "financial_charge" -> "Cargo Financiero"
                else -> item.type.replaceFirstChar { it.uppercase() }
            }

            binding.cbConceptSelect.setOnCheckedChangeListener(null)
            binding.cbConceptSelect.isChecked = selectedItemIds.contains(itemKey)

            binding.cbConceptSelect.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedItemIds.add(itemKey)
                } else {
                    selectedItemIds.remove(itemKey)
                }
                onSelectionChanged()
            }

            binding.cardPendingConcept.setOnClickListener {
                binding.cbConceptSelect.isChecked = !binding.cbConceptSelect.isChecked
            }
        }
    }
}
