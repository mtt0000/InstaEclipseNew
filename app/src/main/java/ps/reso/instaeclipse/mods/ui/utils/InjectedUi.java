package ps.reso.instaeclipse.mods.ui.utils;

import android.app.Activity;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;

import ps.reso.instaeclipse.mods.ghost.ConversationMenuHook;
import ps.reso.instaeclipse.mods.ghost.HideChatsHook;
import ps.reso.instaeclipse.mods.ghost.ui.GhostEmojiManager;
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.ghost.GhostModeUtils;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * What the module adds to Instagram's screens: the settings that only show a button, menu row,
 * label or pill ({@link #FLAGS}, turned off together by "Turn off all injections"), and the views
 * already on screen, hidden as soon as the setting behind them goes off.
 */
public final class InjectedUi {

    /** Settings whose only effect is something added to Instagram's UI. The features behind the
     *  last five (Ghost seen, locked collections, edited counts, the chat tools) keep
     *  working when they are off. */
    public static final String[] FLAGS = {
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
        "isDevEnabled",
        // Buttons of features that work without them
        "ghostSeenButton",
        "ghostInboxTint",
        "lockCollectionsButton",
        "postCountsMenu",
        "chatHeaderMenu"
    };

    private InjectedUi() {}

    private static final class Tracked {
        final WeakReference<View> view;
        final BooleanSupplier shown;

        Tracked(View view, BooleanSupplier shown) {
            this.view = new WeakReference<>(view);
            this.shown = shown;
        }
    }

    private static final List<Tracked> TRACKED = new ArrayList<>();

    /** Registers an injected view, hidden by {@link #refresh} once {@code shown} turns false. */
    public static synchronized void track(View view, BooleanSupplier shown) {
        if (view == null || shown == null) return;
        // Drop views that are gone, so screens re-injecting on every layout don't grow the list.
        if (TRACKED.size() > 64) TRACKED.removeIf(t -> t.view.get() == null);
        TRACKED.add(new Tracked(view, shown));
    }

    /** Hides the injected views on screen whose setting is now off (new ones aren't injected). */
    public static synchronized void refresh() {
        for (Iterator<Tracked> it = TRACKED.iterator(); it.hasNext(); ) {
            Tracked t = it.next();
            View v = t.view.get();
            if (v == null) {
                it.remove();
                continue;
            }
            boolean on;
            try {
                on = t.shown.getAsBoolean();
            } catch (Throwable e) {
                on = true;
            }
            if (!on) v.setVisibility(View.GONE);
        }
    }

    /** True if any of {@link #FLAGS} is on. */
    public static boolean anyOn() {
        for (String flag : FLAGS) {
            try {
                if (FeatureFlags.class.getField(flag).getBoolean(null)) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    /** Turns every setting in {@link #FLAGS} off, saves, and updates the screen. */
    public static void disableAll(Activity activity) {
        for (String flag : FLAGS) {
            try {
                FeatureFlags.class.getField(flag).setBoolean(null, false);
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse|InjectedUi) can't turn off " + flag + ": " + t);
            }
        }
        SettingsManager.saveAllFlags();
        refreshScreen(activity);
    }

    /** After settings changed at once (profile switch, turn off all): updates what is on screen. */
    public static void refreshScreen(Activity activity) {
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            try {
                refresh();
                activity.getWindow().getDecorView().requestLayout();
                activity.getWindow().getDecorView().invalidate();
                HideChatsHook.refreshActiveInbox(activity);
                ConversationMenuHook.refreshAll();
                GhostEmojiManager.addGhostEmojiNextToInbox(activity, GhostModeUtils.isGhostModeActive());
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse|InjectedUi) refresh failed: " + t);
            }
        });
    }
}
