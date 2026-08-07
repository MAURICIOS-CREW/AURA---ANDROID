package com.mexadev.aura.ui.documents

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.core.app.ActivityOptionsCompat
import androidx.core.app.SharedElementCallback
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mexadev.aura.R
import com.mexadev.aura.core.preferences.PreferencesManager
import com.mexadev.aura.databinding.FragmentDocumentsBinding
import com.mexadev.aura.databinding.ItemDocumentBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

data class DocumentModel(
    val id: String,
    val title: String,
    val description: String,
    val fileName: String
)

class DocumentsFragment : Fragment() {

    private var _binding: FragmentDocumentsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DocumentAdapter
    private val documentsList = mutableListOf<DocumentModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDocumentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupWindowInsets()
        setupSharedElementCallback()
        setupListeners()
        setupRecyclerView()
        setupSwipeRefresh()

        showSkeletonLoaders()
        loadDocuments()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.documentsRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.detailHeader.setPadding(
                binding.detailHeader.paddingLeft,
                systemBars.top,
                binding.detailHeader.paddingRight,
                binding.detailHeader.paddingBottom
            )
            insets
        }
    }

    private fun setupSharedElementCallback() {
        requireActivity().setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(
                names: MutableList<String>,
                sharedElements: MutableMap<String, View>
            ) {
                val currentBinding = _binding ?: return
                if (names.isEmpty()) return
                val name = names[0]
                val layoutManager = currentBinding.rvDocuments.layoutManager as? LinearLayoutManager
                    ?: return
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible  = layoutManager.findLastVisibleItemPosition()
                for (i in firstVisible..lastVisible) {
                    val holder = currentBinding.rvDocuments.findViewHolderForAdapterPosition(i) as? DocumentAdapter.DocumentViewHolder
                    val cardView = holder?.binding?.cardDoc ?: continue
                    if (cardView.transitionName == name) {
                        sharedElements[name] = cardView
                        return
                    }
                }
            }
        })
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.aura_primary)
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadDocuments()
        }
    }

    private fun setupRecyclerView() {
        adapter = DocumentAdapter(documentsList) { doc, itemBinding ->
            openDocumentDetail(doc, itemBinding)
        }
        binding.rvDocuments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDocuments.adapter = adapter
    }

    private fun showSkeletonLoaders() {
        val prefs = PreferencesManager(requireContext())
        val savedCount = prefs.documentsCount

        binding.swipeRefreshLayout.isRefreshing = false
        binding.rvDocuments.visibility = View.GONE
        binding.layoutCenterLoading.visibility = View.VISIBLE
        binding.layoutCenterLoading.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())
        val count = savedCount.coerceIn(1, 10)
        repeat(count) {
            inflater.inflate(R.layout.item_document_skeleton, binding.layoutCenterLoading, true)
        }

        ObjectAnimator.ofFloat(binding.layoutCenterLoading, "alpha", 1f, 0.4f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun loadDocuments() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Simulación de tiempo de carga asíncrono para skeleton loader
            delay(600)

            if (_binding == null) return@launch

            val items = listOf(
                DocumentModel(
                    "1",
                    "Reglamento de Convivencia",
                    "Normas para áreas comunes, mascotas, horarios y seguridad.",
                    "Reglas_de_Convivencia.txt"
                ),
                DocumentModel(
                    "2",
                    "Información General",
                    "Horarios de administración, recolección de basura, etc.",
                    "Informacion_General.txt"
                ),
                DocumentModel(
                    "3",
                    "Pago de Mantenimiento",
                    "Cuentas, fechas límite, penalizaciones y métodos de pago.",
                    "Pago_de_Mantenimiento.txt"
                )
            )

            val prefs = PreferencesManager(requireContext())
            prefs.documentsCount = items.size

            documentsList.clear()
            documentsList.addAll(items)

            binding.layoutCenterLoading.visibility = View.GONE
            binding.swipeRefreshLayout.isRefreshing = false
            binding.rvDocuments.visibility = View.VISIBLE

            adapter.updateData(documentsList)

            binding.rvDocuments.alpha = 0f
            binding.rvDocuments.translationY = -20f
            binding.rvDocuments.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(OvershootInterpolator(1.0f))
                .start()
        }
    }

    private fun readAssetFile(fileName: String): String {
        return try {
            val inputStream = requireContext().assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val text = reader.readText()
            reader.close()
            text
        } catch (_: Exception) {
            "No se pudo cargar el contenido del documento."
        }
    }

    private fun openDocumentDetail(doc: DocumentModel, itemBinding: ItemDocumentBinding) {
        val content = readAssetFile(doc.fileName)
        val cardTransName = "doc_card_transition_${doc.id}"
        val titleTransName = "doc_title_transition_${doc.id}"
        val descTransName = "doc_desc_transition_${doc.id}"
        val iconTransName = "doc_icon_transition_${doc.id}"

        itemBinding.cardDoc.transitionName = cardTransName
        itemBinding.tvDocTitle.transitionName = titleTransName
        itemBinding.tvDocDesc.transitionName = descTransName
        itemBinding.ivDocIcon.transitionName = iconTransName

        val intent = Intent(requireContext(), DocumentDetailActivity::class.java).apply {
            putExtra(DocumentDetailActivity.EXTRA_TITLE, doc.title)
            putExtra(DocumentDetailActivity.EXTRA_DESCRIPTION, doc.description)
            putExtra(DocumentDetailActivity.EXTRA_CONTENT, content)
            putExtra(DocumentDetailActivity.EXTRA_TRANSITION_NAME, cardTransName)
            putExtra("transition_title_name", titleTransName)
            putExtra("transition_desc_name", descTransName)
            putExtra("transition_icon_name", iconTransName)
        }

        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            requireActivity(),
            Pair(itemBinding.cardDoc, cardTransName),
            Pair(itemBinding.tvDocTitle, titleTransName),
            Pair(itemBinding.tvDocDesc, descTransName),
            Pair(itemBinding.ivDocIcon, iconTransName)
        )

        startActivity(intent, options.toBundle())
    }

    override fun onDestroyView() {
        activity?.setExitSharedElementCallback(null as SharedElementCallback?)
        super.onDestroyView()
        _binding = null
    }
}

class DocumentAdapter(
    private var items: List<DocumentModel>,
    private val onClick: (DocumentModel, ItemDocumentBinding) -> Unit
) : RecyclerView.Adapter<DocumentAdapter.DocumentViewHolder>() {

    class DocumentViewHolder(val binding: ItemDocumentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val binding = ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DocumentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvDocTitle.text = item.title
        holder.binding.tvDocDesc.text = item.description

        val cardTransName = "doc_card_transition_${item.id}"
        val titleTransName = "doc_title_transition_${item.id}"
        val descTransName = "doc_desc_transition_${item.id}"
        val iconTransName = "doc_icon_transition_${item.id}"

        holder.binding.cardDoc.transitionName = cardTransName
        holder.binding.tvDocTitle.transitionName = titleTransName
        holder.binding.tvDocDesc.transitionName = descTransName
        holder.binding.ivDocIcon.transitionName = iconTransName

        holder.binding.cardDoc.setOnClickListener {
            onClick(item, holder.binding)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newItems: List<DocumentModel>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size
}
