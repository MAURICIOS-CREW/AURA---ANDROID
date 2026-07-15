package com.mexadev.aura.ui.qr

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.mexadev.aura.R
import com.mexadev.aura.core.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QrBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var ivQrCode: ImageView
    private lateinit var pbLoading: ProgressBar
    private lateinit var llError: LinearLayout
    private lateinit var btnRetry: Button
    private lateinit var tvErrorMessage: TextView
    private lateinit var ivErrorIcon: ImageView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_qr_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ivQrCode = view.findViewById(R.id.iv_qr_code)
        pbLoading = view.findViewById(R.id.pb_loading)
        llError = view.findViewById(R.id.ll_error)
        btnRetry = view.findViewById(R.id.btn_retry)
        tvErrorMessage = view.findViewById(R.id.tv_error_message)
        ivErrorIcon = view.findViewById(R.id.iv_error_icon)

        btnRetry.setOnClickListener {
            loadQrData()
        }

        loadQrData()
    }

    private fun loadQrData() {
        showLoading()
        
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getTempQrHash()
                if (response.isSuccessful && response.body() != null) {
                    val hash = response.body()?.getQrData()
                    if (!hash.isNullOrBlank()) {
                        generateAndShowQr(hash)
                    } else {
                        showError("Formato de respuesta inválido")
                    }
                } else {
                    var errorMessage = "Se requiere conexión a internet o hubo problemas en el servidor."
                    var errorIconRes = R.drawable.ic_wifi_off
                    val code = response.code()
                    
                    if (code in 400..499) {
                        try {
                            val errorString = response.errorBody()?.string()
                            if (!errorString.isNullOrBlank()) {
                                val jsonObject = org.json.JSONObject(errorString)
                                if (jsonObject.has("message")) {
                                    errorMessage = jsonObject.getString("message")
                                    errorIconRes = R.drawable.ic_warning // Warning icon for non-server custom errors
                                }
                            }
                        } catch (e: Exception) {
                            // Si falla el parseo, mantenemos el mensaje genérico y wifi off
                        }
                    }
                    showError(errorMessage, errorIconRes)
                }
            } catch (e: Exception) {
                showError("Se requiere conexión a internet o hubo problemas con el servidor.", R.drawable.ic_wifi_off)
            }
        }
    }

    private fun generateAndShowQr(text: String) {
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val writer = QRCodeWriter()
                val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                    }
                }

                withContext(Dispatchers.Main) {
                    ivQrCode.setImageBitmap(bitmap)
                    showQr()
                    animateQrAppearance()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Error al generar el QR", R.drawable.ic_warning)
                }
            }
        }
    }

    private fun animateQrAppearance() {
        ivQrCode.scaleX = 0f
        ivQrCode.scaleY = 0f
        
        val scaleX = ObjectAnimator.ofFloat(ivQrCode, "scaleX", 0f, 1f)
        val scaleY = ObjectAnimator.ofFloat(ivQrCode, "scaleY", 0f, 1f)
        val alpha = ObjectAnimator.ofFloat(ivQrCode, "alpha", 0f, 1f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 500
            interpolator = OvershootInterpolator(1.2f)
            start()
        }
    }

    private fun showLoading() {
        ivQrCode.visibility = View.INVISIBLE
        llError.visibility = View.GONE
        pbLoading.visibility = View.VISIBLE
    }

    private fun showQr() {
        pbLoading.visibility = View.GONE
        llError.visibility = View.GONE
        ivQrCode.visibility = View.VISIBLE
    }

    private fun showError(message: String, iconRes: Int = R.drawable.ic_wifi_off) {
        pbLoading.visibility = View.GONE
        ivQrCode.visibility = View.INVISIBLE
        llError.visibility = View.VISIBLE
        tvErrorMessage.text = message
        ivErrorIcon.setImageResource(iconRes)
    }

    override fun getTheme(): Int {
        return com.google.android.material.R.style.Theme_Design_BottomSheetDialog
    }
}
