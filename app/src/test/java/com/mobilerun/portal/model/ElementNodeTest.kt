package com.mobilerun.portal.model

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementNodeTest {

    @Test
    fun addChild_rejectsSelfReference() {
        val node = element("node")

        node.addChild(node)

        assertTrue(node.children.isEmpty())
        assertNull(node.parent)
    }

    @Test
    fun addChild_rejectsAncestorCycle() {
        val root = element("root")
        val child = element("child")

        root.addChild(child)
        child.addChild(root)

        assertEquals(listOf(child), root.children)
        assertTrue(child.children.isEmpty())
        assertNull(root.parent)
        assertSame(root, child.parent)
    }

    @Test
    fun graphHelpersTerminateWhenCycleAlreadyExists() {
        val root = element("root")
        val child = element("child")

        root.children.add(child)
        child.parent = root
        child.children.add(root)
        root.parent = child

        assertEquals(2, root.calculateNestingLevel())
        assertSame(root, root.getRootAncestor())
        assertEquals(listOf(child), root.getAllDescendants())
        assertEquals(listOf(child, root), root.getPathFromRoot())
    }

    @Test
    fun redactedLogIdentifierOmitsElementIdTextAndContentDescription() {
        val sensitiveMarker = "dro2052-secret"
        val rect = rect(1, 2, 30, 40)
        val element = element(
            id = "generated-$sensitiveMarker",
            text = "visible $sensitiveMarker text",
            className = "EditText",
            rect = rect,
            windowLayer = 3,
            contentDescription = "private $sensitiveMarker description",
        ).apply {
            clickableIndex = 7
            overlayIndex = 11
        }

        val diagnostic = element.redactedLogIdentifier()

        assertTrue(diagnostic.contains("class=EditText"))
        assertTrue(diagnostic.contains("bounds=1,2,30,40"))
        assertTrue(diagnostic.contains("windowLayer=3"))
        assertTrue(diagnostic.contains("clickableIndex=7"))
        assertTrue(diagnostic.contains("overlayIndex=11"))
        assertTrue(diagnostic.contains("identity="))
        assertFalse(diagnostic.contains(sensitiveMarker))
        assertFalse(diagnostic.contains(element.id))
        assertFalse(diagnostic.contains(element.text))
        assertFalse(diagnostic.contains("private"))
    }

    private fun element(
        id: String,
        text: String = id,
        className: String = "TextView",
        rect: Rect = Rect(0, 0, 10, 10),
        windowLayer: Int = 0,
        contentDescription: String? = null,
    ): ElementNode {
        val nodeInfo = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { nodeInfo.contentDescription } returns contentDescription
        return ElementNode(
            nodeInfo = nodeInfo,
            rect = rect,
            text = text,
            className = className,
            windowLayer = windowLayer,
            creationTime = 0L,
            id = id,
        )
    }

    private fun rect(left: Int, top: Int, right: Int, bottom: Int): Rect {
        return Rect().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
    }
}
