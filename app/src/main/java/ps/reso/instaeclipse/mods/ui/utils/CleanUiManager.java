package ps.reso.instaeclipse.mods.ui.utils;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.mods.ghost.ConversationMenuHook;
import ps.reso.instaeclipse.mods.ghost.HideChatsHook;
import ps.reso.instaeclipse.mods.ghost.ui.GhostEmojiManager;
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.ghost.GhostModeUtils;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.toast.IeToast;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class CleanUiManager {

    /**
     * Flags whose only effect is a button, menu row, label or pill the module adds to Instagram:
     * Clean UI turns them off and restores them afterwards. Flags that also drive behaviour
     * (Ghost Mode "Mark as read" buttons, the DM thread header menu, read receipts per chat,
     * edit counts, locked collections, the ghost tint on the inbox icon) are not listed here:
     * their injection points check {@link #isActive()} instead, so the behaviour keeps working.
     */
    private static final String[] UI_BUTTON_FLAGS = {
        // DMs: thread header shortcuts, message long-press rows, inbox multi-select
        "showHideChatButton",
        "showDeletedMessagesButton",
        "jumpToFirstMessage",
        "chatExport",
        "enableVoiceDownload",
        "enableStickerDownload",
        "enableDmMediaDownload",
        "bulkUnsend",
        "translateMessages",
        "enableBatchHideChats",
        // Profiles and follow lists
        "savedOnProfile",
        "followersHistory",
        "followListExport",
        "profileAnalyzer",
        "profileMenuTools",
        "fancyBioFont",
        "profileFollowsYouLabel",
        "enableProfileDownload",
        "showFollowerToast",
        // Feed, posts, comments
        "enablePostDownload",
        "likeUndo",
        "enableCopyComment",
        "commentSearch",
        "enableCaptionCopy",
        "enablePhotoZoom",
        // Reels and post ⋮ menu
        "enableReelDownload",
        "forceReelScrubber",
        "offerPlaybackSpeed",
        "holdLeftSlowMotion",
        "copyMediaLink",
        "offerCopyImage",
        "offerMediaInfo",
        "offerAudioDownload",
        // Instants
        "uploadInstants",
        "saveInstants",
        // Stories
        "enableStoryDownload",
        "storyInfo",
        "storyLinks",
        "enableStoryMentions",
        "storyViewerSearch",
        // Instagram's internal developer options entry
        "isDevEnabled"
    };

    /** True while Clean UI hides the module's injected buttons. */
    public static boolean isActive() {
        return FeatureFlags.isCleanUiActive;
    }

    // Injected views already on screen: hidden / shown at once when Clean UI is toggled, so the
    // user doesn't have to leave and re-open the screen (the flags only stop new injections).
    private static final List<WeakReference<View>> sRegisteredInjectedViews = new ArrayList<>();
    private static final Map<View, Integer> sRegisteredInjectedViewsState = new WeakHashMap<>();

    public static void trackInjectedView(View view) {
        if (view == null) return;
        // Drop views that are gone, so screens re-injecting on every layout don't grow the list.
        if (sRegisteredInjectedViews.size() > 64) sRegisteredInjectedViews.removeIf(r -> r.get() == null);
        sRegisteredInjectedViews.add(new WeakReference<>(view));
        if (FeatureFlags.isCleanUiActive) {
            view.setVisibility(View.GONE);
        }
    }

    public static synchronized void toggleCleanUi(Activity activity) {
        toggleCleanUi(activity, false);
    }

    /** {@code silent}: no toast (the Home shortcut confirms with a vibration only). */
    public static synchronized void toggleCleanUi(Activity activity, boolean silent) {
        if (!FeatureFlags.isCleanUiActive) {
            // Take snapshot and set all to false
            try {
                JSONObject json = new JSONObject();

                for (String flag : UI_BUTTON_FLAGS) {
                    try {
                        java.lang.reflect.Field field = FeatureFlags.class.getField(flag);
                        json.put(flag, field.getBoolean(null));
                        field.setBoolean(null, false);
                    } catch (Exception e) {
                        ModuleLog.line("(InstaEclipse|CleanUI) Failed to snapshot flag " + flag + ": " + e.getMessage());
                    }
                }

                FeatureFlags.cleanUiSavedSnapshot = json.toString();

                Iterator<WeakReference<View>> iterator = sRegisteredInjectedViews.iterator();
                while (iterator.hasNext()) {
                    View view = iterator.next().get();
                    if (view != null) {
                        sRegisteredInjectedViewsState.put(view, view.getVisibility());
                        view.setVisibility(View.GONE);
                    } else {
                        iterator.remove();
                    }
                }

                FeatureFlags.isCleanUiActive = true;

                if (!silent) IeToast.makeText(activity, I18n.t(activity, R.string.clean_ui_toast_on), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                ModuleLog.line("(InstaEclipse|CleanUI) Failed to create snapshot: " + e.getMessage());
            }
        } else {
            // Restore snapshot
            try {
                JSONObject json = new JSONObject(FeatureFlags.cleanUiSavedSnapshot);

                // Every saved key, not just today's list: a snapshot taken by an older build may hold
                // flags that are no longer hidden this way (permanentViewMode, quickToggleSeen).
                for (java.util.Iterator<String> it = json.keys(); it.hasNext(); ) {
                    String flag = it.next();
                    try {
                        java.lang.reflect.Field field = FeatureFlags.class.getField(flag);
                        // Keep anything switched on while Clean UI was active.
                        field.setBoolean(null, json.getBoolean(flag) || field.getBoolean(null));
                    } catch (Exception e) {
                        ModuleLog.line("(InstaEclipse|CleanUI) Failed to restore flag " + flag + ": " + e.getMessage());
                    }
                }

                Iterator<WeakReference<View>> iterator = sRegisteredInjectedViews.iterator();
                while (iterator.hasNext()) {
                    View view = iterator.next().get();
                    if (view != null) {
                        Integer state = sRegisteredInjectedViewsState.get(view);
                        if (state != null) {
                            view.setVisibility(state);
                        } else {
                            view.setVisibility(View.VISIBLE);
                        }
                    } else {
                        iterator.remove();
                    }
                }
                sRegisteredInjectedViewsState.clear();

                FeatureFlags.cleanUiSavedSnapshot = "";
                FeatureFlags.isCleanUiActive = false;

                if (!silent) IeToast.makeText(activity, I18n.t(activity, R.string.clean_ui_toast_off), Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                ModuleLog.line("(InstaEclipse|CleanUI) Failed to parse snapshot: " + e.getMessage());
                FeatureFlags.isCleanUiActive = false;
                FeatureFlags.cleanUiSavedSnapshot = "";
            }
        }

        SettingsManager.saveAllFlags();

        activity.runOnUiThread(() -> {
            try {
                activity.getWindow().getDecorView().requestLayout();
                activity.getWindow().getDecorView().invalidate();
                HideChatsHook.refreshActiveInbox(activity);
                ConversationMenuHook.refreshAll();
                GhostEmojiManager.addGhostEmojiNextToInbox(activity, GhostModeUtils.isGhostModeActive());
            } catch (Exception e) {
                ModuleLog.line("(InstaEclipse|CleanUI) Error refreshing UI: " + e.getMessage());
            }
        });
    }
}
