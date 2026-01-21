package com.lilyan_lefevre.puzzleit

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import org.opencv.android.OpenCVLoader

/**
 * Main Activity for PuzzleHelper Application
 * Initializes OpenCV and sets up the basic application structure
 */
class MainActivity : Activity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
}