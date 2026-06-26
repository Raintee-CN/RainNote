package com.raintee.rainnote.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.raintee.rainnote.databinding.FragmentSettingsBinding
import com.raintee.rainnote.server.RainNoteAccessToken
import com.raintee.rainnote.server.RainNoteServerManager
import java.net.NetworkInterface

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val settingsViewModel =
            ViewModelProvider(this).get(SettingsViewModel::class.java)

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        settingsViewModel.text.observe(viewLifecycleOwner) {
            binding.textSettings.text = it
        }
        renderServerInfo()
        binding.buttonRefreshToken.setOnClickListener {
            RainNoteAccessToken.reset(requireContext())
            renderServerInfo()
        }
        return root
    }

    private fun renderServerInfo() {
        val host = localIpv4Address() ?: "手机IP"
        binding.textServerUrl.text = "访问地址：http://$host:${RainNoteServerManager.PORT}/web"
        binding.textAccessToken.text = "访问码：${RainNoteAccessToken.get(requireContext())}"
    }

    private fun localIpv4Address(): String? {
        return NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
            ?.hostAddress
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
