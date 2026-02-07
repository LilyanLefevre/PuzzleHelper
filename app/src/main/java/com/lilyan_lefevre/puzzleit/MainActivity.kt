package com.lilyan_lefevre.puzzleit

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.lilyan_lefevre.puzzleit.databinding.ActivityMainNavBinding
import dagger.hilt.android.AndroidEntryPoint
import org.opencv.android.OpenCVLoader

/**
 * Main Activity with Navigation Component
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainNavBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainNavBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize OpenCV
        initializeOpenCV()
    }

    /**
     * Initialize OpenCV library
     * This must be called before using any OpenCV functions
     */
    private fun initializeOpenCV() {
        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV loaded successfully")
        } else {
            Log.e(TAG, "OpenCV initialization failed!")
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}