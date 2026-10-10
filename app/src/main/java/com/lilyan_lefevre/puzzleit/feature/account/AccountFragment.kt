package com.lilyan_lefevre.puzzleit.feature.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.transition.MaterialSharedAxis
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.FragmentAccountBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Sign in to your own server and sync: the app works the same without an account. */
@AndroidEntryPoint
class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AccountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.editServer.setText(viewModel.lastServer)
        binding.buttonBack.setOnClickListener { findNavController().navigateUp() }
        fun signIn(create: Boolean) = viewModel.signIn(binding.editServer.text.toString(), binding.editEmail.text.toString(), binding.editPassword.text.toString(), create)
        binding.buttonSignIn.setOnClickListener { signIn(false) }
        binding.buttonCreate.setOnClickListener { signIn(true) }
        binding.buttonSync.setOnClickListener { viewModel.syncNow() }
        binding.buttonSignOut.setOnClickListener { viewModel.signOut() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.account.collect { account ->
                        binding.groupSignedOut.isVisible = account == null
                        binding.groupSignedIn.isVisible = account != null
                        account?.let { binding.textAccount.text = getString(R.string.signed_in_as, it.email, it.server) }
                    }
                }
                launch { viewModel.busy.collect { binding.progressBar.isVisible = it } }
                launch {
                    viewModel.notice.collect { notice ->
                        binding.textNotice.isVisible = notice != null
                        binding.textNotice.text = when (notice) {
                            is Notice.Synced -> getString(R.string.synced, notice.uploaded, notice.downloaded)
                            is Notice.Failed -> notice.reason?.let { getString(it) } ?: getString(R.string.sync_failed, notice.detail)
                            null -> ""
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
