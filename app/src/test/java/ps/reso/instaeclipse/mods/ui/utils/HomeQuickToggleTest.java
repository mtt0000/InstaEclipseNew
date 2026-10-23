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
}
