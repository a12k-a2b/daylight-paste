#!/usr/bin/env bash
# Daylight Paste: SolOS Privileged Subsystem Deployment & Diagnostic Tool
set -euo pipefail

TARGET_DEVICE="${1:-JP5R02371}"
echo "========================================================"
echo "Daylight Paste: SolOS Deployment & Diagnostic Tool"
echo "Target Device: $TARGET_DEVICE"
echo "========================================================"

# 1. Check ADB connection
if ! adb -s "$TARGET_DEVICE" get-state >/dev/null 2>&1; then
    echo "❌ Error: Device $TARGET_DEVICE not detected via ADB."
    exit 1
fi

echo "🟢 Device detected."

# 2. Check current capture health state
echo "📋 Inspecting package permission states..."
adb -s "$TARGET_DEVICE" shell dumpsys package com.daylightcomputer.paste | grep -i "READ_CLIPBOARD_IN_BACKGROUND" || true

# 3. Check registered Input Methods
echo "⌨ Checking registered Input Method Services..."
adb -s "$TARGET_DEVICE" shell ime list -a | grep -A 2 "com.daylightcomputer.paste" || true

# 4. Display instructions
cat << 'EOF'

--------------------------------------------------------
SOLOS DEPLOYMENT ARCHITECTURE:
--------------------------------------------------------
1. System Image Integration (Permanent OS Subsystem):
   - Place APK in: /system/priv-app/DaylightPaste/DaylightPaste.apk
   - Place permissions in: /system/etc/permissions/privapp-permissions-com.daylightcomputer.paste.xml
   - This permanently grants android.permission.READ_CLIPBOARD_IN_BACKGROUND across all apps.

2. Portable / User-Space Deployment:
   - Tooltip Menu: Register PROCESS_TEXT actions 'Daylight Copy' and 'Daylight Paste'.
   - Daylight IME: Enable via Settings -> System -> Languages & Input -> On-screen keyboard.
     As an active IME, AOSP natively grants background clipboard reads.
--------------------------------------------------------
EOF
