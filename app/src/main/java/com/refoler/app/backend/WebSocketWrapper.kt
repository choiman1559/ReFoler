package com.refoler.app.backend

import android.content.Context
import com.refoler.Refoler.RequestPacket
import com.refoler.app.Applications
import com.refoler.app.backend.consts.PacketConst
import com.refoler.app.ui.PrefsKeyConst
import com.refoler.app.utils.JsonRequest
import com.refoler.app.utils.JsonRequest.Companion.getGoogleOAuthAccessToken
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headers
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.runBlocking

@Suppress("PrivatePropertyName")
class WebSocketWrapper(private val context: Context) {
    private val PING_INTERVAL: Long = 20_000
    private lateinit var onDataReceiveListener: OnDataReceiveListener
    private lateinit var clientSession: DefaultClientWebSocketSession

    interface OnDataReceiveListener {
        fun onConnect()
        fun onReceive(data: String)
    }

    fun setOnDataReceiveListener(listener: OnDataReceiveListener) {
        onDataReceiveListener = listener
    }

    fun postRequestPacket(requestPacket: RequestPacket.Builder) {
        val uid = Applications.getPrefs(context).getString(PrefsKeyConst.PREFS_KEY_UID, "")
        if (uid.isNullOrEmpty()) {
            throw IllegalStateException("UID is not set")
        }

        requestPacket.setUid(uid)
        postRequest(
            com.google.protobuf.util.JsonFormat.printer().print(requestPacket.build())
        )
    }

    private fun postRequest(data: String) {
        runBlocking {
            clientSession.send(data)
        }
    }

    fun disconnect() {
        runBlocking {
            clientSession.close()
        }
    }

    fun connect(serviceType: String) {
        runBlocking {
            val client = HttpClient(CIO) {
                install(WebSockets) {
                    pingIntervalMillis = PING_INTERVAL
                }

                headers {
                    val authKey = "Bearer ${getGoogleOAuthAccessToken(context)}"
                    append(HttpHeaders.Authorization, authKey)
                }
            }

            client.webSocket (
                method = HttpMethod.Get,
                host = PacketConst.API_HOST_ADDRESS_WS,
                port = PacketConst.API_HOST_PORT_WS,
                path = JsonRequest.buildPathUri(serviceType)
            ) {
                clientSession = this
                onDataReceiveListener.onConnect()

                for (received in clientSession.incoming) {
                    onDataReceiveListener.onReceive(String(received.data))
                }
            }
        }
    }
}