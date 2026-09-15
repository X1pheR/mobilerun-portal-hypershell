package com.mobilerun.portal.core

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityTraversalGuardTest {

    @Test
    fun activePathRejectsSameNodeUntilLeave() {
        val activePath = mutableSetOf<AccessibilityNodeInfo>()
        val node = node(viewId = "same")

        assertTrue(AccessibilityTraversalGuard.enterActivePath(node, activePath))
        assertFalse(AccessibilityTraversalGuard.enterActivePath(node, activePath))

        AccessibilityTraversalGuard.leaveActivePath(node, activePath)
        assertTrue(AccessibilityTraversalGuard.enterActivePath(node, activePath))
    }

    @Test
    fun activePathRejectsEqualNodeUntilLeave() {
        val activePath = mutableSetOf<AccessibilityNodeInfo>()
        val first = node(viewId = "same-source")
        val equivalent = node(viewId = "same-source-copy")

        every { first.hashCode() } returns 42
        every { equivalent.hashCode() } returns 42
        every { first.equals(any()) } answers {
            val other = firstArg<Any?>()
            other === first || other === equivalent
        }
        every { equivalent.equals(any()) } answers {
            val other = firstArg<Any?>()
            other === equivalent || other === first
        }

        assertTrue(AccessibilityTraversalGuard.enterActivePath(first, activePath))
        assertFalse(AccessibilityTraversalGuard.enterActivePath(equivalent, activePath))

        AccessibilityTraversalGuard.leaveActivePath(first, activePath)
        assertTrue(AccessibilityTraversalGuard.enterActivePath(equivalent, activePath))
    }

    @Test
    fun activeReferenceCheckUsesIdentityNotAccessibilityEquality() {
        val activePath = mutableSetOf<AccessibilityNodeInfo>()
        val first = node(viewId = "same-source")
        val equivalent = node(viewId = "same-source-copy")

        every { first.hashCode() } returns 42
        every { equivalent.hashCode() } returns 42
        every { first.equals(any()) } answers {
            val other = firstArg<Any?>()
            other === first || other === equivalent
        }
        every { equivalent.equals(any()) } answers {
            val other = firstArg<Any?>()
            other === equivalent || other === first
        }

        assertTrue(AccessibilityTraversalGuard.enterActivePath(first, activePath))

        assertTrue(AccessibilityTraversalGuard.isActiveNodeReference(first, activePath))
        assertFalse(AccessibilityTraversalGuard.isActiveNodeReference(equivalent, activePath))
    }

    @Test
    fun traversalKeyIncludesStableNodeFields() {
        val rect = Rect(0, 0, 10, 10)
        val first = node(viewId = "first")
        val second = node(viewId = "second")

        val firstKey = AccessibilityTraversalGuard.createTraversalKey(first, rect)
        val secondKey = AccessibilityTraversalGuard.createTraversalKey(second, rect)

        assertNotEquals(firstKey, secondKey)
    }

    @Test
    fun traversalKeyOmitsSensitiveTextAndContentDescription() {
        val rect = Rect(0, 0, 10, 10)
        val key = AccessibilityTraversalGuard.createTraversalKey(
            node(
                viewId = "safe_view_id",
                text = "secret typed password",
                contentDescription = "private account description",
            ),
            rect,
        )

        assertTrue(key.contains("safe_view_id"))
        assertFalse(key.contains("secret typed password"))
        assertFalse(key.contains("private account description"))
    }

    private fun node(
        viewId: String,
        text: String = "Text",
        contentDescription: String? = null,
    ): AccessibilityNodeInfo {
        val node = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { node.windowId } returns 1
        every { node.className } returns "android.widget.TextView"
        every { node.viewIdResourceName } returns viewId
        every { node.packageName } returns "com.example"
        every { node.text } returns text
        every { node.contentDescription } returns contentDescription
        return node
    }
}
