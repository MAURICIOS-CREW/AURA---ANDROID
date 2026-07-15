package com.mexadev.aura.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mexadev.aura.R
import com.mexadev.aura.databinding.FragmentConfirmBottomSheetBinding

class ConfirmBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentConfirmBottomSheetBinding? = null
    private val binding get() = _binding!!

    var title: String = "Confirmación"
    var message: String = "¿Estás seguro?"
    var confirmText: String = "Confirmar"
    var cancelText: String = "Cancelar"
    
    @DrawableRes var iconRes: Int = R.drawable.ic_warning
    @ColorRes var iconColorRes: Int = R.color.aura_primary
    @ColorRes var confirmTextColorRes: Int = R.color.aura_white
    @DrawableRes var confirmBgRes: Int = R.drawable.bg_btn_primary

    var onConfirm: (() -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfirmBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.text = title
        binding.tvMessage.text = message
        binding.btnConfirm.text = confirmText
        binding.btnCancel.text = cancelText

        binding.ivIcon.setImageResource(iconRes)
        binding.ivIcon.setColorFilter(ContextCompat.getColor(requireContext(), iconColorRes))
        
        binding.btnConfirm.setTextColor(ContextCompat.getColor(requireContext(), confirmTextColorRes))
        binding.btnConfirm.setBackgroundResource(confirmBgRes)

        binding.btnConfirm.setOnClickListener {
            onConfirm?.invoke()
            dismiss()
        }

        binding.btnCancel.setOnClickListener {
            onCancel?.invoke()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            title: String,
            message: String,
            confirmText: String = "Confirmar",
            cancelText: String = "Cancelar",
            @DrawableRes iconRes: Int = R.drawable.ic_warning,
            @ColorRes iconColorRes: Int = R.color.aura_primary,
            @ColorRes confirmTextColorRes: Int = R.color.aura_white,
            @DrawableRes confirmBgRes: Int = R.drawable.bg_btn_primary,
            onConfirm: () -> Unit,
            onCancel: (() -> Unit)? = null
        ): ConfirmBottomSheetFragment {
            return ConfirmBottomSheetFragment().apply {
                this.title = title
                this.message = message
                this.confirmText = confirmText
                this.cancelText = cancelText
                this.iconRes = iconRes
                this.iconColorRes = iconColorRes
                this.confirmTextColorRes = confirmTextColorRes
                this.confirmBgRes = confirmBgRes
                this.onConfirm = onConfirm
                this.onCancel = onCancel
            }
        }
    }
}
