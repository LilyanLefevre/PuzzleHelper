package com.lilyan_lefevre.puzzleit.feature.account

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import com.google.android.material.button.MaterialButton
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.databinding.FragmentLoginBinding
import com.lilyan_lefevre.puzzleit.feature.account.data.OAuthProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** First screen when nobody is signed in: password, or one button per provider the server offers (Google...). The activity leaves it once signed in. */
@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AccountViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fun signIn(create: Boolean) = viewModel.signIn(binding.editEmail.text.toString(), binding.editPassword.text.toString(), create)
        binding.buttonSignIn.setOnClickListener { signIn(false) }
        binding.buttonCreate.setOnClickListener { signIn(true) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.providers.collect(::showProviders) }
                launch { viewModel.busy.collect { binding.progressBar.isVisible = it } }
                launch {
                    viewModel.openUrl.collect { url ->
                        url ?: return@collect
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: ActivityNotFoundException) {
                            // No browser: the sign-in below times out and says so.
                        }
                        viewModel.urlOpened()
                    }
                }
                launch {
                    viewModel.notice.collect { notice ->
                        val failed = notice as? Notice.Failed
                        binding.textNotice.isVisible = failed != null
                        binding.textNotice.text = failed?.let { f -> f.reason?.let { getString(it) } ?: getString(R.string.sync_failed, f.detail) }.orEmpty()
                    }
                }
            }
        }
    }

    private fun showProviders(providers: List<OAuthProvider>) {
        binding.groupProviders.isVisible = providers.isNotEmpty()
        binding.providerButtons.removeAllViews()
        providers.forEach { provider ->
            MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.continue_with, provider.displayName)
                layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.provider_button_height))
                    .apply { topMargin = resources.getDimensionPixelSize(R.dimen.provider_button_gap) }
                setOnClickListener { viewModel.signInWith(provider) }
                binding.providerButtons.addView(this)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
