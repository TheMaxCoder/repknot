package com.repknot.app

import android.app.Application
import com.repknot.data.RoomSetRepository
import com.repknot.domain.SetRepository

class RepknotApplication : Application() {
    val repository: SetRepository by lazy { RoomSetRepository.open(this) }
}
