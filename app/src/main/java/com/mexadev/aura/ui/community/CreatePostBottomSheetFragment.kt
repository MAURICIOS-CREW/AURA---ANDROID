package com.mexadev.aura.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mexadev.aura.databinding.LayoutCreatePostBottomSheetBinding

class CreatePostBottomSheetFragment(
    private val onPostCreated: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: LayoutCreatePostBottomSheetBinding? = null
    private val binding get() = _binding!!
    private lateinit var dbHelper: CommunityDatabaseHelper

    override fun getTheme(): Int = com.google.android.material.R.style.Theme_Design_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutCreatePostBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dbHelper = CommunityDatabaseHelper(requireContext())

        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnPublish.setOnClickListener {
            val content = binding.etPostContent.text?.toString()?.trim()
            if (content.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Por favor escribe un mensaje", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newPost = PostModel(
                authorName = "Tú",
                content = content,
                timestamp = System.currentTimeMillis().toString(),
                likes = 0,
                comments = 0
            )

            dbHelper.addPost(newPost)
            onPostCreated()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
