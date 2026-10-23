package ps.reso.instaeclipse.mods.ui.utils;

import android.app.Activity;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Home button shortcut (long press / 2 taps + long press, see NavGestureBinder): applies a set of
 * switches chosen by the user, then puts them back as they were on the next use.
 *
 * <p>The set ("homeToggleProfile") maps each included switch to the value it gets: on or off.
 * {@link #CLEAN_UI} stands for Clean UI (hide the module's buttons). Applying records the values
 * it replaces ("homeToggleSnapshot") and the next use restores exactly those. With no set chosen
 * the shortcut toggles Clean UI alone, as it did before. Both survive a restart.
 */
public final class HomeQuickToggle {

    /** Pseudo switch: Clean UI. */
    public static final String CLEAN_UI = "cleanUi";

    public static final class Item {
        public final String flag;
        public final int labelRes;

        Item(String flag, int labelRes) {
            this.flag = flag;
            this.labelRes = labelRes;
        }
    }

    public static final class Group {
        public final int titleRes;
        public final int iconRes;
        public final Item[] items;

        Group(int titleRes, int iconRes, Item... items) {
            this.titleRes = titleRes;
            this.iconRes = iconRes;
            this.items = items;
        }
    }

    private static Item i(String flag, int labelRes) {
        return new Item(flag, labelRes);
    }

    /** The switches the shortcut can include, grouped like the module menu. "Hide Home tab" is left
     *  out (it would remove the button the shortcut lives on), as are settings that aren't switches. */
    public static final Group[] GROUPS = {
            new Group(R.string.ig_home_toggle_group_ui, R.drawable.ic_eye_off,
                    i(CLEAN_UI, R.string.ig_home_toggle_clean_ui)),
            new Group(R.string.ig_dialog_menu_ghost_settings, R.drawable.ic_eye,
                    i("isGhostSeen", R.string.ig_dialog_ghost_hide_dm_seen),
                    i("isGhostTyping", R.string.ig_dialog_ghost_hide_typing),
                    i("isGhostScreenshot", R.string.ig_dialog_ghost_bypass_screenshot),
                    i("isGhostViewOnce", R.string.ig_dialog_ghost_hide_view_once),
                    i("isGhostStory", R.string.ig_dialog_ghost_hide_story_views),
                    i("isGhostLive", R.string.ig_dialog_ghost_hide_live_presence),
                    i("allowScreenshots", R.string.ig_dialog_ghost_allow_screenshots_dms),
                    i("keepEphemeralMessages", R.string.ig_dialog_ghost_keep_disappearing),
                    i("permanentViewMode", R.string.ig_dialog_ghost_permanent_view_once),
                    i("keepUnsentMessages", R.string.ig_dialog_ghost_keep_unsent),
                    i("keepUnsentInChat", R.string.ie_keep_unsent_in_chat),
                    i("readReceiptChats", R.string.ig_dialog_ghost_read_receipt_chats),
                    i("spoofLastSeen", R.string.ig_dialog_misc_spoof_last_seen),
                    i("airplaneMode", R.string.ig_dialog_extras_airplane_mode)),
            new Group(R.string.ig_hide_chats_title, R.drawable.ic_eye_off,
                    i("hideSpecificChats", R.string.ig_dialog_misc_hide_chats),
                    i("showHideChatButton", R.string.ie_show_hide_button),
                    i("enableBatchHideChats", R.string.ig_dialog_batch_hide_chats),
                    i("hideChatBackLongPress", R.string.ig_direct_hide_chat_back_long_press),
                    i("emptyGeneralFolder", R.string.ig_direct_empty_general_folder),
                    i("hideDirectFoldersBar", R.string.ig_direct_hide_folders_bar),
                    i("hideNotesTray", R.string.ig_dialog_distraction_hide_notes_tray),
                    i("hideDmSuggestions", R.string.ig_hide_dm_suggestions),
                    i("hideDmBlend", R.string.ig_dialog_hide_dm_blend)),
            new Group(R.string.ig_dialog_misc_lock_section, R.drawable.ic_shield,
                    i("lockDirectMessages", R.string.ig_dialog_misc_lock_dms),
                    i("lockDirectAlways", R.string.ig_dialog_misc_lock_dms_always),
                    i("lockWholeApp", R.string.ig_dialog_misc_lock_whole_app),
                    i("lockSavedCollections", R.string.ig_locked_coll_setting_short)),
            new Group(R.string.ig_dialog_menu_distraction_free, R.drawable.ic_notification,
                    i("disableStories", R.string.ig_dialog_distraction_disable_stories),
                    i("disableFeed", R.string.ig_dialog_distraction_disable_feed),
                    i("disableReels", R.string.ig_dialog_distraction_disable_reels),
                    i("disableReelsExceptDM", R.string.ig_dialog_distraction_disable_reels_except_dm),
                    i("disableExplore", R.string.ig_dialog_distraction_disable_explore),
                    i("disableComments", R.string.ig_dialog_distraction_disable_comments),
                    i("disableDiscoverPeople", R.string.ig_dialog_misc_disable_discover_people),
                    i("hideStoriesTray", R.string.ig_dialog_extras_hide_stories_tray),
                    i("hideInstants", R.string.ig_hide_instants),
                    i("hidePlusUpsells", R.string.ig_dialog_clean_feed_hide_plus)),
            new Group(R.string.menu_navigation, R.drawable.ic_tune,
                    i("hideTabReels", R.string.ig_nav_tab_reels),
                    i("hideTabCreate", R.string.ig_nav_tab_create),
                    i("hideTabMessages", R.string.ig_nav_tab_messages),
                    i("hideTabActivity", R.string.ig_nav_tab_activity),
                    i("hideTabProfile", R.string.ig_nav_tab_profile),
                    i("disableSwipeToCamera", R.string.ig_dialog_extras_no_swipe_camera),
                    i("backLeavesHome", R.string.ig_back_leaves_home)),
            new Group(R.string.menu_feed, R.drawable.ic_sparkle,
                    i("hideSuggestionsInFeed", R.string.ig_dialog_clean_feed_hide_suggested),
                    i("hideThreadsSuggestions", R.string.ig_dialog_clean_feed_hide_threads),
                    i("hideLikedPosts", R.string.ig_dialog_clean_feed_hide_liked),
                    i("followingOnlyFeed", R.string.ig_dialog_clean_feed_following_only),
                    i("expandCaptions", R.string.ig_dialog_clean_feed_expand_captions),
                    i("hideCommunityNotes", R.string.ig_dialog_clean_feed_hide_community_notes),
                    i("confirmFeedRefresh", R.string.ig_confirm_refresh),
                    i("disableVideoAutoPlay", R.string.ig_dialog_misc_disable_video_autoplay)),
            new Group(R.string.menu_reels, R.drawable.ic_movie,
                    i("reelsAutoScroll", R.string.ig_dialog_extras_reels_auto_scroll),
                    i("reelsLockScroll", R.string.ig_dialog_extras_reels_lock_scroll),
                    i("forceReelScrubber", R.string.ig_dialog_downloader_reel_scrubber),
                    i("holdLeftSlowMotion", R.string.ig_dialog_reels_hold_left_slow)),
            new Group(R.string.menu_stories, R.drawable.ic_story_ring,
                    i("disableStoryFlipping", R.string.ig_dialog_misc_disable_story_autoswipe),
                    i("storyInfo", R.string.ig_dialog_misc_story_info),
                    i("storyLinks", R.string.ig_dialog_misc_story_links),
                    i("enableStoryMentions", R.string.ig_dialog_misc_view_story_mentions),
                    i("storyViewerSearch", R.string.ig_dialog_misc_viewer_search),
                    i("cacheStories", R.string.ig_dialog_misc_cache_stories)),
            new Group(R.string.menu_messages, R.drawable.ic_chat,
                    i("chatExport", R.string.ig_dialog_ghost_chat_export),
                    i("jumpToFirstMessage", R.string.ig_dialog_ghost_jump_first),
                    i("translateMessages", R.string.ig_dialog_translate_messages),
                    i("bulkUnsend", R.string.ig_dialog_bulk_unsend),
                    i("keepEditHistory", R.string.ig_dialog_ghost_edit_history),
                    i("showDeletedMessagesButton", R.string.ie_show_deleted_button),
                    i("confirmCalls", R.string.ig_call_confirm),
                    i("hideShareCreateGroup", R.string.ig_dialog_misc_hide_share_create_group)),
            new Group(R.string.menu_likes_comments, R.drawable.ic_heart,
                    i("likeUndo", R.string.ig_dialog_misc_like_undo),
                    i("confirmLikes", R.string.ig_confirm_likes),
                    i("confirmFollows", R.string.ig_confirm_follows),
                    i("disableDoubleTapLike", R.string.ig_dialog_misc_disable_double_tap_like),
                    i("enableCopyComment", R.string.ig_dialog_misc_copy_comment),
                    i("commentSearch", R.string.ig_dialog_misc_comment_search),
                    i("enableCaptionCopy", R.string.ig_dialog_misc_copy_caption),
                    i("disableRepost", R.string.ig_dialog_misc_disable_repost)),
            new Group(R.string.menu_profiles, R.drawable.ic_eye,
                    i("profileFollowsYouLabel", R.string.ig_dialog_misc_profile_follows_label),
                    i("profileMenuTools", R.string.ig_profile_menu_tools),
                    i("profileAnalyzer", R.string.ig_profile_analyzer),
                    i("followListExport", R.string.ig_dialog_misc_follow_export),
                    i("followersHistory", R.string.ig_feat_followers_history),
                    i("savedOnProfile", R.string.ig_saved_on_profile),
                    i("fancyBioFont", R.string.ig_dialog_fancy_bio_font),
                    i("profileSpoofer", R.string.ig_profile_spoofer),
                    i("postCountSpoof", R.string.ig_post_counts_feature),
                    i("showFollowerToast", R.string.ig_dialog_misc_show_follower_toast)),
            new Group(R.string.ig_dialog_menu_downloader, R.drawable.ic_download,
                    i("enablePostDownload", R.string.ig_dialog_downloader_posts),
                    i("enableStoryDownload", R.string.ig_dialog_downloader_stories),
                    i("enableReelDownload", R.string.ig_dialog_downloader_reels),
                    i("enableProfileDownload", R.string.ig_dialog_downloader_profiles),
                    i("enableVoiceDownload", R.string.ig_dialog_downloader_voice),
                    i("enableStickerDownload", R.string.ig_dialog_downloader_stickers),
                    i("enableDmMediaDownload", R.string.ig_dialog_downloader_dm_media),
                    i("copyMediaLink", R.string.ig_dialog_downloader_copy_link),
                    i("offerCopyImage", R.string.ig_dialog_downloader_offer_copy_image),
                    i("offerMediaInfo", R.string.ig_dialog_downloader_offer_media_info),
                    i("offerAudioDownload", R.string.ig_dialog_downloader_offer_audio),
                    i("offerPlaybackSpeed", R.string.ig_dialog_downloader_offer_speed),
                    i("saveInstants", R.string.ig_dialog_misc_save_instants),
                    i("uploadInstants", R.string.ig_dialog_misc_upload_instants)),
            new Group(R.string.menu_ads_tracking, R.drawable.ic_block,
                    i("isAdBlockEnabled", R.string.ig_dialog_ad_block_ads),
                    i("isAnalyticsBlocked", R.string.ig_dialog_ad_block_analytics),
                    i("disableTrackingLinks", R.string.ig_dialog_ad_disable_tracking),
                    i("openLinksExternally", R.string.ig_dialog_open_links_externally),
                    i("disableSearchHistory", R.string.ig_disable_search_history),
                    i("removeMetaAI", R.string.ig_dialog_misc_remove_meta_ai)),
            new Group(R.string.menu_app, R.drawable.ic_settings_gear,
                    i("spoofLocation", R.string.ig_dialog_location_spoof_enable),
                    i("showFeatureToasts", R.string.ig_dialog_misc_show_feature_toasts),
                    i("autoClearCache", R.string.ig_dialog_auto_clear_cache),
                    i("highResolutionImages", R.string.ig_dialog_extras_high_res_images),
                    i("maxUploadQuality", R.string.ig_dialog_max_upload_quality),
                    i("isDevEnabled", R.string.ig_dialog_dev_enable)),
    };

    private HomeQuickToggle() {}

    // ── the set: "flag:1,flag:0" (on / off), CLEAN_UI included like a switch ──

    /** The chosen set; empty means "Clean UI only" (the shortcut's behaviour before sets existed). */
    public static Map<String, Boolean> profile() {
        return profileOf(FeatureFlags.homeToggleProfile);
    }

    /** A stored set ("homeToggleProfile" value; the companion app reads its own copy). */
    public static Map<String, Boolean> profileOf(String raw) {
        Map<String, Boolean> p = parse(raw);
        if (p.isEmpty() && (raw == null || raw.isEmpty())) p.put(CLEAN_UI, Boolean.TRUE);
        return p;
    }

    /** {@code current} with {@code flag} moved to the next state: Not included -> Turn on -> Turn off. */
    public static String cycled(String current, String flag) {
        Map<String, Boolean> p = profileOf(current);
        Boolean now = p.get(flag);
        if (now == null) p.put(flag, Boolean.TRUE);
        else if (now) p.put(flag, Boolean.FALSE);
        else p.remove(flag);
        // An emptied set is stored as "none" so it doesn't fall back to "Clean UI only".
        return p.isEmpty() ? "none" : format(p);
    }

    public static boolean isActive() {
        return FeatureFlags.homeToggleActive;
    }

    // ── the shortcut ──

    /**
     * Applies the set, or restores what it replaced. Vibration is done by the gesture binder. A
     * switch changed by hand while the set was applied is a conflict: a dialog asks, per switch,
     * whether it goes back to its previous value or keeps the user's choice.
     */
    public static synchronized void toggle(Activity activity) {
        if (!FeatureFlags.homeToggleActive) {
            apply(activity);
            return;
        }
        Map<String, Boolean> snapshot = parse(FeatureFlags.homeToggleSnapshot);
        java.util.List<String> conflicts = conflicts(snapshot, parse(FeatureFlags.homeToggleApplied));
        if (conflicts.isEmpty() || activity == null) restore(activity, snapshot);
        else askAboutConflicts(activity, snapshot, conflicts);
    }

    private static void apply(Activity activity) {
        boolean airplaneBefore = FeatureFlags.airplaneMode;
        Map<String, Boolean> p = profile();
        Map<String, Boolean> snapshot = applySwitches(p);
        Map<String, Boolean> applied = new LinkedHashMap<>();
        for (String k : snapshot.keySet()) applied.put(k, p.get(k));
        Boolean cleanUi = p.get(CLEAN_UI);
        // Clean UI last: it turns some of the module's buttons off and remembers them itself.
        if (cleanUi != null) {
            snapshot.put(CLEAN_UI, FeatureFlags.isCleanUiActive);
            applied.put(CLEAN_UI, cleanUi);
            if (cleanUi != FeatureFlags.isCleanUiActive) CleanUiManager.toggleCleanUi(activity, true);
        }
        FeatureFlags.homeToggleSnapshot = format(snapshot);
        FeatureFlags.homeToggleApplied = format(applied);
        FeatureFlags.homeToggleActive = true;
        SettingsManager.saveAllFlags();
        refresh(activity, airplaneBefore != FeatureFlags.airplaneMode);
    }

    /** Puts back the values in {@code snapshot} (a conflict the user chose to keep is left out). */
    private static synchronized void restore(Activity activity, Map<String, Boolean> snapshot) {
        boolean airplaneBefore = FeatureFlags.airplaneMode;
        Map<String, Boolean> values = new LinkedHashMap<>(snapshot);
        // Reverse order: Clean UI gives its buttons back first, then the switches go back.
        Boolean cleanUi = values.remove(CLEAN_UI);
        if (cleanUi != null && cleanUi != FeatureFlags.isCleanUiActive) CleanUiManager.toggleCleanUi(activity, true);
        applySwitches(values);
        FeatureFlags.homeToggleSnapshot = "";
        FeatureFlags.homeToggleApplied = "";
        FeatureFlags.homeToggleActive = false;
        SettingsManager.saveAllFlags();
        refresh(activity, airplaneBefore != FeatureFlags.airplaneMode);
    }

    /** Switches whose current value is no longer the one the shortcut applied (changed by hand). */
    static java.util.List<String> conflicts(Map<String, Boolean> snapshot, Map<String, Boolean> applied) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String key : snapshot.keySet()) {
            Boolean target = applied.get(key);
            Boolean now = current(key);
            if (target != null && now != null && !now.equals(target)) out.add(key);
        }
        return out;
    }

    static Boolean current(String key) {
        if (CLEAN_UI.equals(key)) return FeatureFlags.isCleanUiActive;
        Field f = booleanFlag(key);
        try {
            return f != null ? f.getBoolean(null) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** One check box per changed switch, ticked = back to the previous value. Cancel keeps the
     *  shortcut applied and changes nothing. */
    private static void askAboutConflicts(Activity activity, Map<String, Boolean> snapshot, java.util.List<String> conflicts) {
        try {
            ps.reso.instaeclipse.utils.dialog.IeDialog dialog = new ps.reso.instaeclipse.utils.dialog.IeDialog(activity)
                    .title(t(activity, R.string.ig_home_toggle_conflict_title))
                    .message(t(activity, R.string.ig_home_toggle_conflict_hint));
            Map<String, android.widget.CheckBox> boxes = new LinkedHashMap<>();
            for (String key : conflicts) {
                Boolean before = snapshot.get(key), now = current(key);
                String text = t(activity, R.string.ig_home_toggle_conflict_item, t(activity, labelOf(key)),
                        onOff(activity, Boolean.TRUE.equals(before)), onOff(activity, Boolean.TRUE.equals(now)));
                boxes.put(key, dialog.checkBox(text, true));
            }
            dialog.primary(t(activity, R.string.ig_home_toggle_conflict_confirm), () -> {
                Map<String, Boolean> values = new LinkedHashMap<>(snapshot);
                for (Map.Entry<String, android.widget.CheckBox> e : boxes.entrySet())
                    if (!e.getValue().isChecked()) values.remove(e.getKey()); // keep the user's choice
                restore(activity, values);
            });
            dialog.secondary(activity.getString(android.R.string.cancel), null);
            dialog.show();
        } catch (Throwable t) {
            ModuleLog.line("(IE|HomeToggle) conflict dialog: " + t);
        }
    }

    private static String onOff(Activity a, boolean on) {
        return t(a, on ? R.string.ig_home_toggle_value_on : R.string.ig_home_toggle_value_off);
    }

    private static String t(Activity a, int res, Object... args) {
        return ps.reso.instaeclipse.utils.i18n.I18n.t(a, res, args);
    }

    static int labelOf(String key) {
        for (Group g : GROUPS) for (Item item : g.items) if (item.flag.equals(key)) return item.labelRes;
        return R.string.ig_home_toggle_title;
    }

    /** Sets each switch (CLEAN_UI aside) and returns the values it replaced. Pure FeatureFlags work. */
    static Map<String, Boolean> applySwitches(Map<String, Boolean> targets) {
        Map<String, Boolean> before = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> e : targets.entrySet()) {
            if (CLEAN_UI.equals(e.getKey())) continue;
            Field f = booleanFlag(e.getKey());
            if (f == null) continue;
            try {
                before.put(e.getKey(), f.getBoolean(null));
                f.setBoolean(null, e.getValue());
            } catch (Throwable t) {
                ModuleLog.line("(IE|HomeToggle) " + e.getKey() + ": " + t);
            }
        }
        return before;
    }

    static Field booleanFlag(String name) {
        try {
            Field f = FeatureFlags.class.getField(name);
            return f.getType() == boolean.class ? f : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Things that only follow a switch when told: ghost tint, hidden chats, airplane mode, buttons. */
    private static void refresh(Activity activity, boolean airplaneChanged) {
        try {
            if (airplaneChanged) ps.reso.instaeclipse.mods.extras.AirplaneModeHook.apply();
            ps.reso.instaeclipse.mods.ghost.ui.GhostEmojiManager.addGhostEmojiNextToInbox(activity,
                    ps.reso.instaeclipse.utils.ghost.GhostModeUtils.isGhostModeActive());
            ps.reso.instaeclipse.mods.ghost.ConversationMenuHook.refreshAll();
            ps.reso.instaeclipse.mods.ghost.HideChatsHook.refreshKnownAccounts();
            ps.reso.instaeclipse.mods.ghost.HideChatsHook.refreshActiveInbox(activity);
            activity.getWindow().getDecorView().requestLayout();
        } catch (Throwable t) {
            ModuleLog.line("(IE|HomeToggle) refresh: " + t);
        }
    }

    // ── "flag:1,flag:0" ──

    public static Map<String, Boolean> parse(String s) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        if (s == null || s.isEmpty() || "none".equals(s)) return out;
        for (String part : s.split(",")) {
            int c = part.indexOf(':');
            if (c <= 0) continue;
            String key = part.substring(0, c).trim();
            if (CLEAN_UI.equals(key) || booleanFlag(key) != null)
                out.put(key, "1".equals(part.substring(c + 1).trim()));
        }
        return out;
    }

    public static String format(Map<String, Boolean> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Boolean> e : m.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append(':').append(e.getValue() ? '1' : '0');
        }
        return sb.toString();
    }
}
