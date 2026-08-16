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
    const val BUNDLE_KEY_IS_EXACT_MATCH = "is_exact_match"
    const val BUNDLE_KEY_IS_IGNORE_CASE = "is_ignore_case"
    const val BUNDLE_KEY_IMAGE_SOURCE = "image_source"
    const val BUNDLE_KEY_IMAGE_PATH = "image_path"

    // Region restriction keys
    const val BUNDLE_KEY_RESTRICT_REGION = "restrict_region"
    const val BUNDLE_KEY_REGION_LEFT = "region_left"
    const val BUNDLE_KEY_REGION_TOP = "region_top"
    const val BUNDLE_KEY_REGION_RIGHT = "region_right"
    const val BUNDLE_KEY_REGION_BOTTOM = "region_bottom"

    // Image source options
    const val IMAGE_SOURCE_SCREEN_CAPTURE = "screen_capture"
    const val IMAGE_SOURCE_FILE_PATH = "file_path"
    const val IMAGE_SOURCE_ACCESSIBILITY = "accessibility"
}
