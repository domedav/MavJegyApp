package com.domedav.mavjegy

import android.app.Application
import com.domedav.mavjegy.data.MavApi
import com.domedav.mavjegy.data.TokenStore

class MavJegyApp : Application() {
    lateinit var tokenStore: TokenStore
        private set
    lateinit var api: MavApi
        private set

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(this)
        api = MavApi(tokenStore)
    }
}
