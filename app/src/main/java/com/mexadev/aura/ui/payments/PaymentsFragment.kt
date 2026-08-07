package com.mexadev.aura.ui.payments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.mexadev.aura.R
import com.mexadev.aura.databinding.FragmentPaymentsBinding

class PaymentsFragment : Fragment() {
    private var _binding: FragmentPaymentsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaymentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.tvBalance.text = getString(R.string.home_amount_placeholder)
        binding.tvNextPayment.text = getString(R.string.home_next_payment_date_placeholder)
        
        // Simular historial de pagos
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
