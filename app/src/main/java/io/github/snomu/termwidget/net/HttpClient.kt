package io.github.snomu.termwidget.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClient {
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
}
