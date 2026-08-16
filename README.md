# PaddleOCR_Plugin
# PaddleOCR for Tasker

**PaddleOCR for Tasker** is a fast, 100% offline Android OCR plugin built on PP-OCRv6. Designed for **Tasker**, **MacroDroid**, and **AutoInput**, it allows you to capture screen text, match keywords/regex, and return exact target coordinates `(x, y)` for instant tap automation.

---

## 🌟 Key Features

* **100% Offline & Private:** Runs inference on-device. No internet connection or API keys required.
* **High-Accuracy Chinese & English OCR:** Unlike built-in automation tools (e.g., MacroDroid's default OCR which struggles with Chinese characters), this plugin uses PP-OCRv6 to deliver high-accuracy recognition for Chinese (Simplified/Traditional) and English text.
* **Silent Screen Capture:** Eliminates constant system confirmation popups via ADB AppOps authorization or Android 11+ Accessibility Service.
* **Target Matching:** Search text using **Contains**, **Exact**, or **Regex** rules (with case-insensitive support).
* **Region Restriction (Crop Box):** Draw and restrict recognition to a specific screen area to boost speed and accuracy.
* **Structured Output:** Instantly outputs target center coordinates `(x, y)` for auto-clicking, alongside full recognized text and a detailed JSON array.

---

## 📸 Capture Modes

| Mode | Platform | Popup-Free | Requirements & Notes |
| :--- | :--- | :--- | :--- |
| **Screen Record** *(Recommended)* | Android 8.0+ | **Yes** (via ADB) | Fastest & most reliable. Run the ADB command once to bypass popups permanently. |
| **Accessibility Service** | Android 11+ | **Yes** | 100% silent without Root or ADB. Enable Accessibility Service in Android Settings. |
| **Local File Path** | All Versions | **Yes** | Directly process an image file stored on local storage. |

### 💡 One-Line ADB Setup for Silent Screen Record
If you have Root, Shizuku, or PC ADB access, run this command once to grant permanent silent screen capture permission:

```bash
adb shell appops set com.paddle.ocr.plugin PROJECT_MEDIA allow
```

---

## 📊 Output Variables

When triggered in Tasker / MacroDroid, the plugin populates the following variables:

| Variable | Description | Example / Output |
| :--- | :--- | :--- |
| `%match_found` | Whether the target text was matched | `true` or `false` |
| `%match_center_x` | Target center X coordinate (px) | `540` |
| `%match_center_y` | Target center Y coordinate (px) | `1280` |
| `%ocr_full_text` | All recognized text combined | `Skip\nConfirm\nCancel` |
| `%ocr_json` | Complete JSON array with text blocks & bounds | `[{"text":"Skip","bounds":[...],"confidence":0.98}]` |
| `%ocr_error` | Error message if execution fails | Empty on success |

---

## 🛠️ Quick Setup Tutorial (Auto-Click "Skip" Button)

1. **Add Action in Tasker / MacroDroid:**
   Select **Plugin** -> **PaddleOCR (OCR Screen Capture)**.
2. **Configure Plugin:**
   * **Image Source:** `Screen Record` or `Accessibility`
   * **Target Text:** `Skip` (or `跳过` for Chinese apps)
   * **Match Rule:** `Contains`
3. **Add Tap Action:**
   * **Condition:** If `%match_found ~ true`
   * **Shell Action:** `input tap %match_center_x %match_center_y` (or use AutoInput / MacroDroid UI Interaction by coordinates)
   * **End If**
