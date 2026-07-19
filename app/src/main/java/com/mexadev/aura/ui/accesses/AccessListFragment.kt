package com.mexadev.aura.ui.accesses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.core.app.SharedElementCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mexadev.aura.R
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.data.model.AccessCode
import com.mexadev.aura.databinding.FragmentAccessListBinding
import kotlinx.coroutines.launch

class AccessListFragment : Fragment() {

    private var _binding: FragmentAccessListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AccessesAdapter
    private var accessList = mutableListOf<AccessCode>()
    
    private lateinit var successBanner: com.mexadev.aura.ui.common.BannerManager

    // Tracks the last transition name used so we can remap it on return
    private var lastClickedTransitionName: String? = null
    private var lastClickedAccessId: Long = -1L

    private val detailLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        // Remap the shared element after return: the RecyclerView may have recycled the
        // clicked card's ViewHolder, so we need to find the correct view again and set
        // its transitionName before the framework tries to animate it back.
        // We do this in setExitSharedElementCallback (registered in onViewCreated).
        
        viewLifecycleOwner.lifecycleScope.launch {
            // Wait for the return transition to fully finish (350ms duration + small buffer)
            // before triggering any data refresh that would call notifyDataSetChanged.
            // This prevents the RecyclerView from redrawing while the morph animation is
            // still running, which was the root cause of the card "flicker".
            kotlinx.coroutines.delay(450)
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val json = result.data?.getStringExtra("access_data")
                if (json != null) {
                    try {
                        val access = com.google.gson.Gson().fromJson(json, AccessCode::class.java)
                        val index = accessList.indexOfFirst { it.id == access.id }
                        if (index != -1) {
                            accessList[index] = access
                            adapter.updateItem(index, access)
                        } else {
                            accessList.add(0, access)
                            adapter.addItem(0, access)
                            binding.rvAccesses.scrollToPosition(0)
                            
                            val prefs = requireContext().getSharedPreferences("aura_cache", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putInt("last_access_count", accessList.size).apply()
                            successBanner.show("Código QR creado")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            fetchAccesses(silent = true)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccessListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        successBanner = com.mexadev.aura.ui.common.BannerManager(binding.successBanner, binding.tvSuccessBannerMessage)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val basePadding = (16 * resources.displayMetrics.density).toInt()
            binding.successBanner.setPadding(
                basePadding,
                systemBars.top + basePadding,
                basePadding,
                basePadding
            )
            insets
        }

        // Set up the exit shared element callback so that when we return from
        // AccessDetailActivity the framework can remap the shared element transition
        // to the correct card in the RecyclerView (which may have been recycled/rebound).
        // Without this, the framework cannot find the view and falls back to a
        // plain fade which manifests as the "flicker" visible at the end of the animation.
        requireActivity().setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(
                names: MutableList<String>,
                sharedElements: MutableMap<String, View>
            ) {
                if (names.isEmpty()) return
                val name = names[0]
                // Try to find the matching item view in the RecyclerView by its transitionName
                val layoutManager = binding.rvAccesses.layoutManager as? LinearLayoutManager
                    ?: return
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible  = layoutManager.findLastVisibleItemPosition()
                for (i in firstVisible..lastVisible) {
                    val holder = binding.rvAccesses.findViewHolderForAdapterPosition(i)
                    val itemView = holder?.itemView ?: continue
                    if (itemView.transitionName == name) {
                        sharedElements[name] = itemView
                        return
                    }
                }
                // If not found in list (e.g. FAB was the source), try the FAB
                if (binding.fabAdd.transitionName == name) {
                    sharedElements[name] = binding.fabAdd
                }
            }
        })
        
        setupRecyclerView()
        setupSwipeRefresh()
        setupFab()
        populateDynamicSkeleton()

        binding.layoutCenterLoading.visibility = View.VISIBLE
        android.animation.ObjectAnimator.ofFloat(binding.layoutCenterLoading, "alpha", 1f, 0.4f, 1f).apply {
            duration = 1200
            repeatCount = android.animation.ValueAnimator.INFINITE
            start()
        }
        fetchAccesses()
    }

    private fun populateDynamicSkeleton() {
        val prefs = requireContext().getSharedPreferences("aura_cache", android.content.Context.MODE_PRIVATE)
        val lastCount = prefs.getInt("last_access_count", 3).coerceIn(1, 10)
        
        binding.layoutCenterLoading.removeAllViews()
        val inflater = android.view.LayoutInflater.from(requireContext())
        for (i in 0 until lastCount) {
            inflater.inflate(com.mexadev.aura.R.layout.item_access_skeleton, binding.layoutCenterLoading, true)
        }
    }

    private fun setupRecyclerView() {
        binding.rvAccesses.layoutManager = LinearLayoutManager(requireContext())
        adapter = AccessesAdapter(
            items = mutableListOf(),
            onItemClick = { accessCode, cardView -> 
                val tName = "transition_access_${accessCode.id}"
                // Ensure the transition name is set on the view right before launching
                // (the adapter sets it in onBind, but we confirm it here)
                cardView.transitionName = tName
                lastClickedTransitionName = tName
                lastClickedAccessId = accessCode.id
                val intent = android.content.Intent(requireContext(), AccessDetailActivity::class.java)
                intent.putExtra(AccessDetailActivity.EXTRA_ACCESS_ID, accessCode.id)
                intent.putExtra("extra_transition_name", tName)
                val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(),
                    cardView,
                    tName
                )
                detailLauncher.launch(intent, options)
            }
        )
        binding.rvAccesses.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.aura_primary)
        binding.swipeRefreshLayout.setOnRefreshListener { fetchAccesses() }
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            val tName = "transition_fab_add"
            binding.fabAdd.transitionName = tName
            lastClickedTransitionName = tName
            lastClickedAccessId = -1L
            val intent = android.content.Intent(requireContext(), AccessDetailActivity::class.java)
            intent.putExtra("extra_transition_name", tName)
            val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                requireActivity(),
                binding.fabAdd,
                tName
            )
            detailLauncher.launch(intent, options)
        }
    }

    private fun fetchAccesses(silent: Boolean = false) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getAccessCodes()
                if (_binding == null) return@launch
                
                if (!silent) binding.layoutCenterLoading.visibility = View.GONE
                
                if (!response.isSuccessful) {
                    if (!silent) Toast.makeText(requireContext(), "Error al obtener códigos", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val list = response.body()?.data ?: emptyList()
                handleFetchSuccess(list, silent)
                
            } catch (e: Exception) {
                if (_binding == null) return@launch
                if (!silent) {
                    binding.layoutCenterLoading.visibility = View.GONE
                    Toast.makeText(requireContext(), "Sin conexión a internet", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _binding?.swipeRefreshLayout?.isRefreshing = false
            }
        }
    }

    private fun handleFetchSuccess(list: List<AccessCode>, silent: Boolean) {
        if (list.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvAccesses.visibility = View.GONE
            return
        }
        
        binding.layoutEmpty.visibility = View.GONE
        binding.rvAccesses.visibility = View.VISIBLE
        
        if (silent) {
            updateList(list)
        } else {
            updateListWithAnimation(list)
        }
    }

    private fun updateList(list: List<AccessCode>) {
        val prefs = requireContext().getSharedPreferences("aura_cache", android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt("last_access_count", list.size).apply()

        accessList.clear()
        accessList.addAll(list)
        adapter.updateData(accessList)
    }

    private fun updateListWithAnimation(list: List<AccessCode>) {
        val prefs = requireContext().getSharedPreferences("aura_cache", android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt("last_access_count", list.size).apply()

        accessList.clear()
        accessList.addAll(list)
        adapter.updateData(accessList)

        binding.rvAccesses.alpha = 0f
        binding.rvAccesses.translationY = -30f
        binding.rvAccesses.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(1.0f))
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        successBanner.destroy()
        _binding = null
    }
}
