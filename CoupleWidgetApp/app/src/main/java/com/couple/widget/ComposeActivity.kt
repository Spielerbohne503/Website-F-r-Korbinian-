package com.couple.widget

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.couple.widget.databinding.ActivityComposeBinding
import java.io.ByteArrayOutputStream

class ComposeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComposeBinding
    private var selectedBitmap: Bitmap? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { loadImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComposeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPickImage.setOnClickListener { pickImage.launch("image/*") }
        binding.btnSend.setOnClickListener { send() }
    }

    @Suppress("DEPRECATION")
    private fun loadImage(uri: Uri) {
        try {
            val bmp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source)
            } else {
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            selectedBitmap = scaleBitmap(bmp, 800)
            binding.ivPreview.setImageBitmap(selectedBitmap)
            binding.ivPreview.visibility = View.VISIBLE
            binding.etMessage.setText("")
        } catch (e: Exception) {
            Toast.makeText(this, "Bild konnte nicht geladen werden", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scaleBitmap(bmp: Bitmap, maxSize: Int): Bitmap {
        val ratio = minOf(maxSize.toFloat() / bmp.width, maxSize.toFloat() / bmp.height)
        if (ratio >= 1f) return bmp
        return Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * ratio).toInt(),
            (bmp.height * ratio).toInt(),
            true
        )
    }

    private fun send() {
        val bitmap = selectedBitmap
        val text = binding.etMessage.text.toString().trim()

        when {
            bitmap != null -> {
                val b64 = bitmapToBase64(bitmap)
                P2PService.sendImage(this, b64)
                Toast.makeText(this, "Bild gesendet!", Toast.LENGTH_SHORT).show()
                finish()
            }
            text.isNotEmpty() -> {
                P2PService.sendText(this, text)
                Toast.makeText(this, "Nachricht gesendet!", Toast.LENGTH_SHORT).show()
                finish()
            }
            else -> Toast.makeText(this, "Text eingeben oder Bild auswählen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
    }
}
