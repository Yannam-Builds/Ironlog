package com.ironlog.app.config

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.ironlog.app.BuildConfig

class IronlogNativeConfigModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = "IronlogNativeConfig"

  override fun getConstants(): MutableMap<String, Any?> {
    return mutableMapOf(
      "googleDriveAndroidClientId" to BuildConfig.GOOGLE_DRIVE_ANDROID_CLIENT_ID
    )
  }
}

