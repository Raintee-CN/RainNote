package com.raintee.rainnote.sync

import android.content.Context
import android.nfc.NfcAdapter
import android.provider.Settings
import java.util.UUID

class NfcPairingManager(context: Context) {

    private val appContext = context.applicationContext
    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(appContext)

    val isNfcAvailable: Boolean get() = nfcAdapter != null
    val isNfcEnabled: Boolean get() = nfcAdapter?.isEnabled == true

    fun localHandshake(): PairedDevice {
        return PairedDevice(
            deviceId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) ?: UUID.randomUUID().toString(),
            deviceName = android.os.Build.MODEL ?: "Android Device",
            token = UUID.randomUUID().toString(),
            pairedAt = System.currentTimeMillis()
        )
    }

    fun statusText(): String = when {
        !isNfcAvailable -> "此设备不支持 NFC，后续可使用局域网同步。"
        !isNfcEnabled -> "NFC 未开启，请在系统设置中开启后碰一碰配对。"
        else -> "NFC 已就绪：碰一碰用于交换设备信息，卡片数据由后续同步通道传输。"
    }
}
