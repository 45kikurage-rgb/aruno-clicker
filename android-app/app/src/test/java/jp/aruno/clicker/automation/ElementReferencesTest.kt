package jp.aruno.clicker.automation

import android.graphics.Rect
import org.robolectric.RuntimeEnvironment
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class ElementReferencesTest {
    private fun node(id: Int, b: Rect, text: String = "", visible: Boolean = true, layer: Int = 0) = JSONObject()
        .put("id", id).put("parent", if (id == 0) -1 else 0).put("windowLayer", layer)
        .put("bounds", JSONArray(listOf(b.left, b.top, b.right, b.bottom))).put("visible", visible)
        .put("text", text).put("description", "").put("viewId", "").put("textMissing", text.isEmpty())
        .put("descriptionMissing", true).put("viewIdMissing", true).put("enabled", true).put("childCount", 0)
    private fun snapshot() = JSONObject().put("recordId", UUID.randomUUID().toString()).put("screen", JSONArray(listOf(0, 0, 1000, 2000)))
        .put("density", 2.0).put("nodes", JSONArray(listOf(
            node(0, Rect(0, 0, 1000, 2000)),
            node(1, Rect(200, 1000, 800, 1100), "確認"),
            node(2, Rect(460, 1200, 540, 1280)),
            node(3, Rect(900, 20, 980, 100)),
            node(4, Rect(10, 2100, 90, 2180), visible = false),
        )))
    @Test fun blankSmallCloseCanBeSuggestedWithoutWhiteCardOrAttributes() {
        val ranked = ElementReferenceCandidates.analyse(snapshot())
        assertEquals(2, ranked.getJSONObject(0).getInt("nodeId"))
        assertTrue(ranked.getJSONObject(0).getJSONArray("reasons").toString().contains("below-wide-content"))
        assertEquals(40.0, ranked.getJSONObject(0).getDouble("widthDp"), 0.01)
        assertFalse(ranked.toString().contains("\"nodeId\":4"))
    }
    @Test fun labelledCloseOutranksGeometricGuessAndDisabledIsExcluded() {
        val s = snapshot(); s.getJSONArray("nodes").put(node(5, Rect(850, 500, 930, 580), "×"))
        assertEquals(5, ElementReferenceCandidates.analyse(s).getJSONObject(0).getInt("nodeId"))
        s.getJSONArray("nodes").getJSONObject(5).put("enabled", false)
        assertEquals(2, ElementReferenceCandidates.analyse(s).getJSONObject(0).getInt("nodeId"))
    }
    @Test fun selectionUsesSmallestBoundsAndAllowsCyclingAllContainingElements() {
        val s = snapshot(); val matches = ElementReferenceSelection.atPoint(s, 500, 1240)
        assertEquals(listOf(2, 0), matches.map { it.getInt("id") })
        assertNull(ElementReferenceSelection.pick(s, -1, -1))
    }
    @Test fun higherWindowTakesPriorityAndOffscreenIsNeverSelected() {
        val s = snapshot(); s.getJSONArray("nodes").put(node(5, Rect(450, 1190, 550, 1290), layer = 5))
        assertEquals(5, ElementReferenceSelection.pick(s, 500, 1240)?.getInt("id"))
        assertNull(ElementReferenceSelection.pick(s, 50, 2140))
    }
    @Test fun yesAndNoPreserveAllNodesAndMissingAttributesAndSourceIsUnmodified() {
        val s = snapshot()
        for (answer in listOf(true, false)) {
            val r = ElementReferenceSelection.labelled(s, 2, answer)
            assertEquals(answer, r.getBoolean("isClose")); assertEquals(5, r.getJSONArray("nodes").length())
            assertEquals(2, r.getInt("selectedNodeId")); assertTrue(r.getJSONArray("nodes").getJSONObject(2).getBoolean("viewIdMissing"))
        }
        assertFalse(s.has("isClose"))
    }
    @Test fun manualRegionIsDistinctFromAnExtractedElement() {
        val r = ElementReferenceSelection.labelledRegion(snapshot(), Rect(450, 1180, 550, 1300), true)
        assertTrue(r.isNull("selectedNodeId")); assertEquals("region", r.getString("selectionType"))
    }
    @Test(expected = IllegalArgumentException::class) fun outsideRegionRejected() {
        ElementReferenceSelection.labelledRegion(snapshot(), Rect(-10, 0, 50, 50), true)
    }
    @Test(expected = IllegalArgumentException::class) fun invisibleElementCannotBeConfirmed() {
        ElementReferenceSelection.labelled(snapshot(), 4, true)
    }
    @Test fun localRecordRemainsPendingUntilMatchingCentralReceiptAndCannotBeOverwritten() {
        val store = ElementReferenceStore(RuntimeEnvironment.getApplication())
        val r = ElementReferenceSelection.labelled(snapshot(), 2, true)
        val f = store.save(r)
        try {
            assertTrue(store.pending().contains(f))
            try { store.markSent(r, JSONObject().put("stored", true).put("recordId", UUID.randomUUID().toString())); fail() }
            catch (_: IllegalArgumentException) { }
            assertTrue(store.pending().contains(f))
            store.markSent(r, JSONObject().put("stored", true).put("recordId", r.getString("recordId")))
            assertTrue(f.exists()); assertFalse(store.pending().contains(f))
            try { store.save(r); fail() } catch (_: IllegalArgumentException) { }
        } finally { f.delete(); java.io.File(f.parentFile, f.nameWithoutExtension + ".sent").delete() }
    }
}
