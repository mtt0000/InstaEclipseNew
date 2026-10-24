package ps.reso.instaeclipse.mods.ui.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import ps.reso.instaeclipse.utils.feature.FeatureFlags;

/** The Home shortcut set: every listed switch exists, the stored format, apply and restore. */
public class HomeQuickToggleTest {

    @After public void reset() {
        FeatureFlags.homeToggleProfile = "";
        FeatureFlags.isGhostSeen = false;
        FeatureFlags.disableReels = false;
        FeatureFlags.enablePostDownload = false;
        FeatureFlags.isCleanUiActive = false;
    }

    @Test public void everyItemIsABooleanSwitchListedOnce() {
        Set<String> seen = new HashSet<>();
        for (HomeQuickToggle.Group g : HomeQuickToggle.GROUPS)
            for (HomeQuickToggle.Item item : g.items) {
                assertTrue("listed twice: " + item.flag, seen.add(item.flag));
                if (!HomeQuickToggle.CLEAN_UI.equals(item.flag))
                    assertNotNull("not a boolean FeatureFlags field: " + item.flag, HomeQuickToggle.booleanFlag(item.flag));
            }
        assertFalse("Hiding the Home tab would remove the shortcut's own button", seen.contains("hideTabHome"));
    }

    @Test public void formatRoundTripsAndDropsUnknownKeys() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        m.put(HomeQuickToggle.CLEAN_UI, true);
        m.put("isGhostSeen", true);
        m.put("disableReels", false);
        String s = HomeQuickToggle.format(m);
        assertEquals("cleanUi:1,isGhostSeen:1,disableReels:0", s);
        assertEquals(m, HomeQuickToggle.parse(s));
        assertTrue(HomeQuickToggle.parse("notAFlag:1,homeToggleProfile:1").isEmpty());
    }

    @Test public void noSetMeansCleanUiOnlyAndAnEmptiedSetStaysEmpty() {
        FeatureFlags.homeToggleProfile = "";
        assertEquals(Map.of(HomeQuickToggle.CLEAN_UI, true), HomeQuickToggle.profile());
        FeatureFlags.homeToggleProfile = "none";
        assertTrue(HomeQuickToggle.profile().isEmpty());
    }

    @Test public void applyReturnsWhatItReplacedAndRestoresIt() {
        FeatureFlags.isGhostSeen = false;
        FeatureFlags.disableReels = false;
        FeatureFlags.enablePostDownload = true;
        Map<String, Boolean> set = HomeQuickToggle.parse("cleanUi:1,isGhostSeen:1,disableReels:1,enablePostDownload:0");

        Map<String, Boolean> before = HomeQuickToggle.applySwitches(set);
        assertTrue(FeatureFlags.isGhostSeen);
        assertTrue(FeatureFlags.disableReels);
        assertFalse(FeatureFlags.enablePostDownload);
        assertFalse("Clean UI is handled by CleanUiManager, not as a field", before.containsKey(HomeQuickToggle.CLEAN_UI));

        HomeQuickToggle.applySwitches(before);
        assertFalse(FeatureFlags.isGhostSeen);
        assertFalse(FeatureFlags.disableReels);
        assertTrue(FeatureFlags.enablePostDownload);
    }

    @Test public void aSwitchChangedByHandWhileAppliedIsAConflict() {
        // applied: Ghost seen ON, Reels off ON, Clean UI ON; before they were all OFF
        Map<String, Boolean> before = HomeQuickToggle.parse("isGhostSeen:0,disableReels:0,cleanUi:0");
        Map<String, Boolean> applied = HomeQuickToggle.parse("isGhostSeen:1,disableReels:1,cleanUi:1");
        FeatureFlags.isGhostSeen = true;   // untouched since
        FeatureFlags.disableReels = false; // turned back off by hand
        FeatureFlags.isCleanUiActive = true;
        assertEquals(java.util.List.of("disableReels"), HomeQuickToggle.conflicts(before, applied));

        FeatureFlags.isCleanUiActive = false; // Clean UI turned off meanwhile
        assertEquals(java.util.List.of("disableReels", HomeQuickToggle.CLEAN_UI), HomeQuickToggle.conflicts(before, applied));
    }

    @Test public void nothingAppliedRecordedMeansNoConflict() {
        // a set applied by the previous build has no "applied" record: restore as before, no dialog
        FeatureFlags.isGhostSeen = true;
        assertTrue(HomeQuickToggle.conflicts(HomeQuickToggle.parse("isGhostSeen:0"), HomeQuickToggle.parse("")).isEmpty());
    }

    @Test public void everyItemHasItsOwnLabel() {
        for (HomeQuickToggle.Group g : HomeQuickToggle.GROUPS)
            for (HomeQuickToggle.Item item : g.items)
                assertEquals(item.labelRes, HomeQuickToggle.labelOf(item.flag));
    }
}
