package com.example.wheelmic

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MicTapService : AccessibilityService() {
    private var pendingTap = false
    private var attempts = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != "com.offline.assistant") return
        if (!pendingTap) { pendingTap = true; attempts = 0 }
        tapMic(rootInActiveWindow)
    }

    private fun tapMic(root: AccessibilityNodeInfo?): Boolean {
        if (root == null || attempts++ > 30) { if (attempts > 30) pendingTap = false; return false }
        val nodes = root.findAccessibilityNodeInfosByViewId("com.offline.assistant:id/chatMic")
        for (node in nodes) if (node.isVisibleToUser && node.isEnabled && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            pendingTap = false
            return true
        }
        for (i in 0 until root.childCount) if (tapMic(root.getChild(i))) return true
        return false
    }
    override fun onInterrupt() {}
}
