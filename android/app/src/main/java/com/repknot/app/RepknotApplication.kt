package com.repknot.app

import android.app.Application
import com.repknot.data.CloudAccount
import com.repknot.data.RoomSetRepository
import com.repknot.domain.SetRepository

class RepknotApplication : Application() {
    val repository: SetRepository by lazy { RoomSetRepository.open(this) }
    val cloud: CloudAccount by lazy { CloudAccount(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) }
}
