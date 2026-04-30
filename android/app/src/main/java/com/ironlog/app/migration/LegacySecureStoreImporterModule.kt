package com.ironlog.app.migration

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableMap

class LegacySecureStoreImporterModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = "LegacySecureStoreImporter"

  @ReactMethod
  fun importKeys(keys: ReadableArray, promise: Promise) {
    try {
      val out: WritableMap = Arguments.createMap()
      for (i in 0 until keys.size()) {
        val key = keys.getString(i) ?: continue
        val value = readLegacyValue(key)
        if (!value.isNullOrEmpty()) {
          out.putString(key, value)
        }
      }
      promise.resolve(out)
    } catch (error: Throwable) {
      promise.reject("legacy_secure_import_failed", error)
    }
  }

  private fun readLegacyValue(key: String): String? {
    val prefNames = listOf(
      "SecureStore",
      "expo.modules.securestore.sharedpreferences",
      "SecureStorePreferences"
    )
    for (name in prefNames) {
      readEncryptedPreference(name, key)?.let { return it }
      readPlainPreference(name, key)?.let { return it }
    }
    return null
  }

  private fun readPlainPreference(prefName: String, key: String): String? {
    val prefs = reactContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    return if (prefs.contains(key)) prefs.getString(key, null) else null
  }

  private fun readEncryptedPreference(prefName: String, key: String): String? {
    return try {
      val masterKey = MasterKey.Builder(reactContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
      val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        reactContext,
        prefName,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
      )
      if (prefs.contains(key)) prefs.getString(key, null) else null
    } catch (_: Throwable) {
      null
    }
  }
}

