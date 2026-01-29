package com.lilyan_lefevre.puzzleit.feature.project

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.DialogProjectMetadataBinding

/**
 * Custom dialog for project metadata input
 */
class ProjectMetadataDialog(
    context: Context,
    private val onProjectCreated: (name: String, pieces: Int) -> Unit,
    private val onDismiss: () -> Unit
) : Dialog(context, R.style.Theme_PuzzleIt) {

    private lateinit var binding: DialogProjectMetadataBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogProjectMetadataBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Make dialog smaller width with transparent background
        window?.apply {
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.8).toInt(), // 80% of screen width
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
        }
        
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnCancel.setOnClickListener {
            dismiss()
            onDismiss()
        }
        
        binding.btnCreate.setOnClickListener {
            val name = binding.editTextName.text.toString().trim()
            val pieces = try {
                binding.editTextPieces.text.toString().toInt()
            } catch (e: NumberFormatException) {
                Toast.makeText(context, context.getString(R.string.please_enter_number_of_pieces), Toast.LENGTH_SHORT).show()
                null
            }
            
            if (name.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.please_enter_project_name), Toast.LENGTH_SHORT).show()
            } else if (pieces == null) {
                Toast.makeText(context, context.getString(R.string.please_enter_number_of_pieces), Toast.LENGTH_SHORT).show()
            } else {
                dismiss()
                onProjectCreated(name, pieces)
            }
        }
    }

    override fun dismiss() {
        super.dismiss()
        onDismiss()
    }
}
