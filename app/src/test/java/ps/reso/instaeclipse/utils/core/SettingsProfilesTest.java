package ps.reso.instaeclipse.utils.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import ps.reso.instaeclipse.utils.feature.FeatureFlags;

public class SettingsProfilesTest {

    private static Map<String, Object> defaults() {
        Map<String, Object> d = new TreeMap<>();
        d.put("isGhostSeen", false);
        d.put("enablePostDownload", false);
        d.put("storyTrayScale", 100);
        d.put("spoofLat", "0.0");
        d.put("cleanUiToggleGesture", 0); // global
        d.put(SettingsProfiles.KEY, "");  // global
        return d;
    }

    /** Applies what an operation returned, the way SettingsManager / the companion do. */
    private static void write(Map<String, Object> live, Map<String, Object> changes) {
        live.putAll(changes);
    }

    @Test
    public void everySettingStartsOff() {
        for (Map.Entry<String, Object> e : FeatureFlags.defaults().entrySet()) {
            if (e.getValue() instanceof Boolean) assertFalse(e.getKey() + " must default to off", (Boolean) e.getValue());
        }
        assertEquals(Boolean.FALSE, FeatureFlags.defaults().get("lockUseFingerprint"));
        assertEquals(Boolean.FALSE, FeatureFlags.defaults().get("downloaderCustomQuality"));
        assertEquals("0.0", FeatureFlags.defaults().get("spoofLat"));
    }

    @Test
    public void switchKeepsEachProfilesValues() {
        Map<String, Object> d = defaults();
        Map<String, Object> live = new HashMap<>();
        live.put("isGhostSeen", true);
        live.put("cleanUiToggleGesture", 2);
        SettingsProfiles p = SettingsProfiles.create("A");
        String b = p.add("B").id;

        write(live, p.activate(b, live, d));
        assertEquals(false, live.get("isGhostSeen"));       // B starts with everything off
        assertEquals(2, live.get("cleanUiToggleGesture"));   // shortcuts are global

        live.put("enablePostDownload", true);                // edited while B is active
        write(live, p.activate("p1", live, d));
        assertEquals(true, live.get("isGhostSeen"));
        assertEquals(false, live.get("enablePostDownload"));

        write(live, p.activate(b, live, d));
        assertEquals(true, live.get("enablePostDownload"));
        assertEquals(false, live.get("isGhostSeen"));
    }

    @Test
    public void storeSurvivesSerialization() {
        Map<String, Object> d = defaults();
        Map<String, Object> live = new HashMap<>();
        live.put("storyTrayScale", 60);
        SettingsProfiles p = SettingsProfiles.create("A");
        String b = p.add("B").id;
        write(live, p.activate(b, live, d));
        assertEquals(100, live.get("storyTrayScale"));

        SettingsProfiles again = SettingsProfiles.parse((String) live.get(SettingsProfiles.KEY));
        assertEquals(2, again.list().size());
        assertEquals(b, again.active().id);
        write(live, again.activate("p1", live, d));
        assertEquals(60, live.get("storyTrayScale"));
    }

    @Test
    public void nextCyclesThroughProfiles() {
        SettingsProfiles p = SettingsProfiles.create("A");
        assertNull(p.nextId());
        p.add("B");
        p.add("C");
        assertEquals("p2", p.nextId());
        Map<String, Object> live = new HashMap<>();
        p.activate("p3", live, defaults());
        assertEquals("p1", p.nextId());
    }

    @Test
    public void duplicateOfActiveCopiesCurrentSettings() {
        Map<String, Object> d = defaults();
        Map<String, Object> live = new HashMap<>();
        live.put("isGhostSeen", true);
        SettingsProfiles p = SettingsProfiles.create("A");
        SettingsProfiles.Profile copy = p.duplicate("p1", "A copy", live, d);
        assertNotNull(copy);
        write(live, p.activate(copy.id, live, d));
        assertEquals(true, live.get("isGhostSeen"));
        assertEquals("A copy", p.active().name);
    }

    @Test
    public void deleteKeepsActiveAndLastProfile() {
        SettingsProfiles p = SettingsProfiles.create("A");
        assertFalse(p.delete("p1"));
        String b = p.add("B").id;
        assertFalse(p.delete("p1"));
        assertTrue(p.delete(b));
        assertEquals(1, p.list().size());
    }

    @Test
    public void resetTurnsEverythingOff() {
        Map<String, Object> d = defaults();
        Map<String, Object> live = new HashMap<>();
        live.put("isGhostSeen", true);
        live.put("storyTrayScale", 50);
        live.put("cleanUiToggleGesture", 1);
        SettingsProfiles p = SettingsProfiles.create("A");
        write(live, p.reset("p1", live, d));
        assertEquals(false, live.get("isGhostSeen"));
        assertEquals(100, live.get("storyTrayScale"));
        assertEquals(1, live.get("cleanUiToggleGesture"));

        // An inactive profile: its stored values go, the current settings stay.
        live.put("isGhostSeen", true);
        String b = p.duplicate("p1", "B", live, d).id;
        Map<String, Object> changes = p.reset(b, live, d);
        assertEquals(1, changes.size());
        write(live, p.activate(b, live, d));
        assertEquals(false, live.get("isGhostSeen"));
    }

    @Test
    public void reorderChangesOrderAndNextProfile() {
        SettingsProfiles p = SettingsProfiles.create("A");
        p.add("B");
        p.add("C");
        assertTrue(p.reorder(java.util.Arrays.asList("p3", "p1")));
        assertEquals("p3", p.list().get(0).id);
        assertEquals("p1", p.list().get(1).id);
        assertEquals("p2", p.list().get(2).id); // not listed: kept, after the others
        assertEquals("p2", p.nextId());          // after the active one (p1) in the new order
        assertFalse(p.reorder(java.util.Arrays.asList("p3", "p1", "p2")));
        SettingsProfiles again = SettingsProfiles.parse(p.serialize());
        assertEquals("p3", again.list().get(0).id);
    }

    @Test
    public void unreadableStoreIsEmpty() {
        assertTrue(SettingsProfiles.parse("").isEmpty());
        assertTrue(SettingsProfiles.parse("{not json").isEmpty());
        SettingsProfiles p = SettingsProfiles.create("Profile 1");
        assertEquals("Profile 1 2", p.uniqueName("Profile 1"));
        assertTrue(p.rename("p1", "  Work "));
        assertEquals("Work", p.active().name);
        assertFalse(p.rename("p1", " "));
    }
}
