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

    // ============================================================
    // Reference-passing pattern extras (Termux:Tasker compatible)
    // ============================================================
    /** PendingIntent extra key, placed into downstream intent */
    const val EXTRA_PENDING_INTENT = "pendingIntent"

    // Our custom bundle keys
    const val BUNDLE_KEY_TARGET_TEXT = "target_text"
    const val BUNDLE_KEY_IS_REGEX = "is_regex"
    const val BUNDLE_KEY_IS_EXACT_MATCH = "is_exact_match"
    const val BUNDLE_KEY_IS_IGNORE_CASE = "is_ignore_case"
    const val BUNDLE_KEY_CAPTURE_MODE = "capture_mode"
    const val BUNDLE_KEY_FILE_PATH = "file_path"
    
    // Region Restriction
    const val BUNDLE_KEY_RESTRICT_REGION = "restrict_region"
    const val BUNDLE_KEY_REGION_LEFT = "region_left"
    const val BUNDLE_KEY_REGION_TOP = "region_top"
    const val BUNDLE_KEY_REGION_RIGHT = "region_right"
    const val BUNDLE_KEY_REGION_BOTTOM = "region_bottom"
    
    // Capture Modes
    const val MODE_MEDIA_PROJECTION = 0
    const val MODE_ACCESSIBILITY = 1
    const val MODE_FILE_PATH = 2
}
