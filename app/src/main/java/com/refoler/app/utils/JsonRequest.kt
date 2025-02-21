package com.refoler.app.utils

import android.content.Context
import com.google.auth.oauth2.GoogleCredentials
import com.refoler.Refoler
import com.refoler.app.Applications
import com.refoler.app.backend.consts.PacketConst
import com.refoler.app.backend.ResponseWrapper
import com.refoler.app.ui.PrefsKeyConst
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import java.io.IOException

class JsonRequest {

    interface OnReceivedCompleteListener {
        fun onFinished(receivedPacket: ResponseWrapper)
    }

    companion object {
        @Suppress("HttpUrlsUsage")
        private fun buildApiUrl(serviceType: String): String {
            return "%s%s".format(
                PacketConst.API_HOST_ADDRESS,
                buildPathUri(serviceType)
            )
        }

        fun buildPathUri(serviceType: String): String {
            return PacketConst.API_ROUTE_SCHEMA.replace("{version}", "v1")
                .replace("{service_type}", serviceType)
        }

        @JvmStatic
        fun postRequestPacket(
            context: Context,
            serviceType: String,
            requestPacket: Refoler.RequestPacket.Builder,
            event: OnReceivedCompleteListener?
        ) {
            val uid = Applications.getPrefs(context).getString(PrefsKeyConst.PREFS_KEY_UID, "")
            if (uid.isNullOrEmpty()) {
                throw IllegalStateException("UID is not set")
            }

            requestPacket.setUid(uid)
            postRequest(
                context,
                serviceType,
                com.google.protobuf.util.JsonFormat.printer().print(requestPacket.build()),
                event
            )
        }

        @JvmStatic
        fun postRequest(context: Context, serviceType: String, body: String, event: OnReceivedCompleteListener?) {
            Thread {
                runBlocking {
                    val responsePacket =
                        ResponseWrapper()
                    val client = HttpClient().get(buildApiUrl(serviceType)) {
                        headers {
                            append(
                                HttpHeaders.Authorization,
                                "Bearer ${getGoogleOAuthAccessToken(context)}"
                            )
                        }
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }

                    responsePacket.refolerPacket = ResponseWrapper.parseResponsePacket(client.call.body())
                    responsePacket.statusCode = client.status
                    event?.onFinished(responsePacket)
                }
            }.start()
        }

        @JvmStatic
        fun getGoogleOAuthAccessToken(context: Context): String {
            try {
                val googleCredentials = GoogleCredentials
                    .fromStream(context.assets.open("service-account.json"))
                    .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))
                googleCredentials.refreshIfExpired()
                return googleCredentials.accessToken.tokenValue
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return ""
        }
    }
}