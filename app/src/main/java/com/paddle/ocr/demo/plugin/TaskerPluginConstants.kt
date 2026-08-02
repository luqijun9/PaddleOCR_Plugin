package com.paddle.ocr.demo.plugin

object TaskerPluginConstants {
    const val ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
    const val ACTION_EDIT_SETTING = "com.twofortyfouram.locale.intent.action.EDIT_SETTING"
    
    const val EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"
    const val EXTRA_VARIABLES = "net.dinglisch.android.tasker.extras.VARIABLES"
    const val EXTRA_RELEVANT_VARIABLES = "net.dinglisch.android.tasker.RELEVANT_VARIABLES"
    const val EXTRA_REQUESTED_TIMEOUT = "net.dinglisch.android.tasker.extras.REQUESTED_TIMEOUT"
    
    // Our custom bundle keys
    const val BUNDLE_KEY_TARGET_TEXT = "target_text"
    const val BUNDLE_KEY_IS_REGEX = "is_regex"
}
