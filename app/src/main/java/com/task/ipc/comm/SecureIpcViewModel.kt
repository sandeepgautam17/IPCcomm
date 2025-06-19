package com.task.ipc.comm

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.shared.CryptoHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SecureIpcViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MSG_REQUEST_PUBLIC_KEY = 1
        const val MSG_REPLY_PUBLIC_KEY = 2
        const val MSG_SEND_ENCRYPTED_AES_KEY = 3
        const val MSG_SEND_SECURE_DATA = 4
        const val MSG_SECURE_RESPONSE = 5
    }

    private val crypto = CryptoHelper("ClientRSAKey")
    private var serviceMessenger: Messenger? = null
    private val rawAES = ByteArray(16)  // Will store the AES key for this session

    private val _responses = MutableStateFlow<List<String>>(emptyList())
    val responses = _responses.asStateFlow()

    private lateinit var replyMessenger: Messenger

    init {
        replyMessenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
            when (msg.what) {
                MSG_REPLY_PUBLIC_KEY -> {
                    val publicKey = msg.data.getByteArray("public_key")!!
                    CoroutineScope(Dispatchers.Default).launch {
                        val newAES = crypto.generateRawAESKey()
                        newAES.copyInto(rawAES)
                        val encryptedAES = crypto.encryptWithRSA(publicKey, newAES)
                        Log.d("SecureIpcVM", "🔑 Sending encrypted AES key")
                        val m = Message.obtain(null, MSG_SEND_ENCRYPTED_AES_KEY)
                        val b = Bundle()
                        b.putByteArray("encrypted_aes", encryptedAES)
                        m.setData(b)
                        m.replyTo = replyMessenger
                        serviceMessenger?.send(m)
                    }
                }

                MSG_SECURE_RESPONSE -> {
                    val encrypted = msg.data.getByteArray("secure_response")!!
                    CoroutineScope(Dispatchers.Default).launch {
                        val plain = crypto.aesDecrypt(rawAES, encrypted)
                        Log.d("SecureIpcVM", "✅ Decrypted response: $plain")
                        _responses.value = _responses.value + plain
                    }
                }
            }
            true
        })
    }

    fun bindService() {
        val appContext = getApplication<Application>().applicationContext
        val intent = Intent().apply {
            component = ComponentName(
                "com.task.ipc.service_app",
                "com.task.ipc.service_app.SecureService"
            )
        }
        ContextCompat.startForegroundService(appContext, intent)
        val bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        Log.d("SecureIpcVM", "Service bound: $bound")
    }

    fun sendSecureMessage(text: String) {
        serviceMessenger?.let {
            CoroutineScope(Dispatchers.Default).launch {
                val encrypted = crypto.aesEncrypt(rawAES, text)
                val msg = Message.obtain(null, MSG_SEND_SECURE_DATA)
                val b = Bundle()
                b.putByteArray("secure_data", encrypted)
                msg.setData(b)
                msg.replyTo = replyMessenger
                serviceMessenger?.send(msg)
            }
        } ?: run {
            Toast.makeText(getApplication(), "Could not connect to Secure Service.", Toast.LENGTH_LONG).show()
            Log.e("SecureIpcVM", "❌ Service not bound")
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("SecureIpcVM", "✅ Service connected: $name")
            serviceMessenger = Messenger(service)
            val msg = Message.obtain(null, MSG_REQUEST_PUBLIC_KEY)
            msg.replyTo = replyMessenger
            serviceMessenger?.send(msg)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("SecureIpcVM", "Service disconnected: $name")
            serviceMessenger = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unbindService(connection)
    }
}
