package com.mobilerun.portal.api

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.mobilerun.portal.core.StateRepository
import com.mobilerun.portal.model.ElementNode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiHandlerFlattenElementsTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun flattenElementsTerminatesWhenGraphAlreadyContainsCycle() {
        val root = element("root")
        val child = element("child")
        root.children.add(child)
        child.parent = root
        child.children.add(root)
        root.parent = child

        val result = flattenElements(listOf(root))

        assertEquals(listOf(root, child), result)
    }

    @Test
    fun flattenElementsLogsRedactedIdentifierWhenGraphAlreadyContainsCycle() {
        val sensitiveMarker = "dro2052-secret"
        val root = element(
            id = "root-$sensitiveMarker",
            text = "visible $sensitiveMarker text",
            className = "EditText",
            rect = rect(1, 2, 30, 40),
        )
        val child = element("child")
        root.children.add(child)
        child.parent = root
        child.children.add(root)
        root.parent = child
        val warning = slot<String>()
        every { Log.w(any(), capture(warning)) } returns 0

        val result = flattenElements(listOf(root))

        assertEquals(listOf(root, child), result)
        verify(exactly = 1) { Log.w(any(), any<String>()) }
        assertTrue(warning.captured.contains("class=EditText"))
        assertTrue(warning.captured.contains("bounds=1,2,30,40"))
        assertFalse(warning.captured.contains(sensitiveMarker))
        assertFalse(warning.captured.contains(root.id))
        assertFalse(warning.captured.contains(root.text))
    }

    @Suppress("UNCHECKED_CAST")
    private fun flattenElements(elements: List<ElementNode>): List<ElementNode> {
        val handler = ApiHandler(
            stateRepo = StateRepository(service = null),
            getKeyboardIME = { null },
            getPackageManager = { mockk<PackageManager>(relaxed = true) },
            appVersionProvider = { "test" },
            context = mockk<Context>(relaxed = true),
        )
        val method = ApiHandler::class.java.getDeclaredMethod("flattenElements", List::class.java)
        method.isAccessible = true
        return method.invoke(handler, elements) as List<ElementNode>
    }

    private fun element(
        id: String,
        text: String = id,
        className: String = "TextView",
        rect: Rect = Rect(0, 0, 10, 10),
    ): ElementNode {
        return ElementNode(
            nodeInfo = mockk<AccessibilityNodeInfo>(relaxed = true),
            rect = rect,
            text = text,
            className = className,
            windowLayer = 0,
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
