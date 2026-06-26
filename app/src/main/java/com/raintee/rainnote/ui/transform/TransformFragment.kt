package com.raintee.rainnote.ui.transform

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.raintee.rainnote.databinding.FragmentTransformBinding
import com.raintee.rainnote.server.RainNoteAccessToken
import com.raintee.rainnote.server.RainNoteServerManager

class TransformFragment : Fragment() {

    private var _binding: FragmentTransformBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTransformBinding.inflate(inflater, container, false)
        setupWebView()
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        RainNoteServerManager.start(requireContext())
        val token = RainNoteAccessToken.get(requireContext())
        binding.editorWebview.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            overScrollMode = WebView.OVER_SCROLL_NEVER
            webViewClient = WebViewClient()
            loadUrl("http://127.0.0.1:${RainNoteServerManager.PORT}/web-mobile/?embedded=1&token=$token#/notes")
        }
    }

    override fun onDestroyView() {
        binding.editorWebview.stopLoading()
        _binding = null
        super.onDestroyView()
    }
}
