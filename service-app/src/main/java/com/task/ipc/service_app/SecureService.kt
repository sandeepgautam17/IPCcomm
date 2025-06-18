package com.task.ipc.service_app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.shared.CryptoHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SecureService : Service() {

    companion object {
        const val CHANNEL_ID = "SecureServiceChannel"
        const val NOTIFICATION_ID = 101

        const val MSG_REQUEST_PUBLIC_KEY = 1
        const val MSG_REPLY_PUBLIC_KEY = 2
        const val MSG_SEND_ENCRYPTED_AES_KEY = 3
        const val MSG_SEND_SECURE_DATA = 4
        const val MSG_SECURE_RESPONSE = 5
    }

    private val crypto = CryptoHelper("ServiceRSAKey")
    private var rawAES: ByteArray? = null

    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        Log.d("SecureService", "✅ onCreate")

        crypto.generateRSAKeyIfNeeded()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        messenger = Messenger(IncomingHandler())
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d("SecureService", "✅ onBind")
        return messenger.binder
    }

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val dataBundle = msg.data
            dataBundle.classLoader = javaClass.classLoader // ✅ fix Bundle deserialization

            when (msg.what) {
                MSG_REQUEST_PUBLIC_KEY -> {
                    val publicKeyBytes = crypto.getRSAPublicKey().encoded
                    val reply = Message.obtain(null, MSG_REPLY_PUBLIC_KEY)
                    val b = Bundle()
                    b.putByteArray("public_key", publicKeyBytes)
                    reply.setData(b)
                    msg.replyTo.send(reply)
                    Log.d("SecureService", "✅ Sent public key")
                }

                MSG_SEND_ENCRYPTED_AES_KEY -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        val encryptedAES = dataBundle.getByteArray("encrypted_aes")
                        if (encryptedAES == null) {
                            Log.e("SecureService", "❌ Missing 'encrypted_aes'")
                            return@launch
                        }
                        try {
                            val decryptedAES = crypto.decryptWithRSA(encryptedAES)
                            rawAES = decryptedAES
                            Log.d("SecureService", "✅ AES key decrypted & stored")
                        } catch (e: Exception) {
                            Log.e("SecureService", "❌ Failed to decrypt AES key: ${e.message}")
                        }
                    }
                }

                MSG_SEND_SECURE_DATA -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        val encryptedData = dataBundle.getByteArray("secure_data")
                        val aesKey = rawAES
                        if (encryptedData == null) {
                            Log.e("SecureService", "❌ Missing 'secure_data'")
                            return@launch
                        }
                        if (aesKey == null) {
                            Log.e("SecureService", "❌ AES key not initialized yet")
                            return@launch
                        }
                        try {
                            val plain = crypto.aesDecrypt(aesKey, encryptedData)
                            Log.d("SecureService", "✅ Decrypted text: $plain")

                            val encryptedResponse = crypto.aesEncrypt(aesKey, "Service received: $plain")
                            val reply = Message.obtain(null, MSG_SECURE_RESPONSE)
                            val b = Bundle()
                            b.putByteArray("secure_response", encryptedResponse)
                            reply.setData(b)
                            msg.replyTo.send(reply)
                            Log.d("SecureService", "✅ Sent encrypted response")
                        } catch (e: Exception) {
                            Log.e("SecureService", "❌ Failed to decrypt/process data: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Secure IPC Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Secure Service Running")
            .setContentText("Listening for secure IPC messages...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SecureService", "✅ onDestroy")
    }
}
