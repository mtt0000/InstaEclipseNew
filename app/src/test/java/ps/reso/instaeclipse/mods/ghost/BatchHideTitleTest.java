package ps.reso.instaeclipse.mods.ghost;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Which row-model strings may name a chat in the batch hide list. */
public class BatchHideTitleTest {

    @Test public void namesAndUsernamesAreAccepted() {
        assertTrue(BatchHideChatsHook.looksLikeName("Mario Rossi", "340282366841710300949128"));
        assertTrue(BatchHideChatsHook.looksLikeName("mario_rossi", "340282366841710300949128"));
        assertTrue(BatchHideChatsHook.looksLikeName("Famiglia 🏠", "1"));
    }

    @Test public void idsKeysAndUrlsAreRejected() {
        String id = "340282366841710300949128";
        assertFalse(BatchHideChatsHook.looksLikeName(id, id));
        assertFalse(BatchHideChatsHook.looksLikeName("12345678", id));
        assertFalse(BatchHideChatsHook.looksLikeName("https://instagram.com/x", id));
        assertFalse(BatchHideChatsHook.looksLikeName("THREAD_TYPE_GROUP", id));
        assertFalse(BatchHideChatsHook.looksLikeName("   ", id));
        assertFalse(BatchHideChatsHook.looksLikeName(null, id));
    }

    @Test public void statusWordsAreNotNames() {
        String id = "340282366841710300949128";
        assertFalse(BatchHideChatsHook.looksLikeName("unknown", id));
        assertFalse(BatchHideChatsHook.looksLikeName("UNKNOWN", id));
        assertFalse(BatchHideChatsHook.looksLikeName("Unknown", id));
        assertFalse(BatchHideChatsHook.looksLikeName("general", id));
        assertFalse(BatchHideChatsHook.looksLikeName("pending", id));
    }
}
