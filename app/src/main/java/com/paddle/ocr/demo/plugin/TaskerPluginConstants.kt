package com.paddle.ocr.demo.plugin

object TaskerPluginConstants {
    // Locale plugin standard actions
    const val ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
    const val ACTION_EDIT_SETTING = "com.twofortyfouram.locale.intent.action.EDIT_SETTING"

    // Locale plugin standard extras
    const val EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"

    // Tasker completion intent (private in TaskerPlugin.java, needed for debug logging)
    const val EXTRA_PLUGIN_COMPLETION_INTENT = "net.dinglisch.android.tasker.extras.COMPLETION_INTENT"

    // Our custom bundle keys
    const val BUNDLE_KEY_TARGET_TEXT = "target_text"
    const val BUNDLE_KEY_IS_REGEX = "is_regex"
}
