package com.repknot.app

/** Reports only transitions into a validated default network. */
internal class NetworkValidation<T> {
    private var network: T? = null
    private var validated = false

    @Synchronized fun changed(current: T, isValidated: Boolean): Boolean {
        if (network != current) { network = current; validated = false }
        val shouldSync = isValidated && !validated
        validated = isValidated
        return shouldSync
    }

    @Synchronized fun lost(current: T) {
        if (network == current) { network = null; validated = false }
    }
}
