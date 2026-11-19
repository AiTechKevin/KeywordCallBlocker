package com.example.keywordcallblocker

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class KeywordCallScreeningService : CallScreeningService() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            allowCall(callDetails)
            return
        }

        val incomingNumber = callDetails.handle?.schemeSpecificPart
        if (incomingNumber.isNullOrBlank()) {
            allowCall(callDetails)
            return
        }

        executor.execute {
            val config = RuleStorage.load(applicationContext)

            if (!config.enabled || config.keywords.isEmpty() || !RuleStorage.isWithinSchedule(config)) {
                allowCall(callDetails)
                return@execute
            }

            val contactName = lookupContactName(incomingNumber)
            if (contactName == null) {
                allowCall(callDetails)
                return@execute
            }

            val matchedKeyword = config.keywords.firstOrNull { keyword ->
                keyword.isNotBlank() && contactName.contains(keyword.trim(), ignoreCase = true)
            }

            if (matchedKeyword != null) {
                RuleStorage.addBlockedLog(applicationContext, contactName, incomingNumber, matchedKeyword)
                val response = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)
                    .setSkipCallLog(config.skipCallLog)
                    .build()
                respondToCall(callDetails, response)
            } else {
                allowCall(callDetails)
            }
        }
    }

    private fun allowCall(callDetails: Call.Details) {
        respondToCall(callDetails, CallResponse.Builder().build())
    }

    private fun lookupContactName(phoneNumber: String): String? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
