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
import ps.reso.instaeclipse.mods.ghost.HideChatsHook;
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.toast.IeToast;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class CleanUiManager {

    private static final String[] UI_BUTTON_FLAGS = {
        "showHideChatButton",
        "showDeletedMessagesButton",
        "jumpToFirstMessage",
        "chatExport",
        "enableVoiceDownload",
        "enableStickerDownload",
        "enableDmMediaDownload",
        "bulkUnsend",
        "translateMessages",
        "permanentViewMode",
        "quickToggleSeen",
        "savedOnProfile",
        "followersHistory",
        "followListExport",
        "fancyBioFont",
        "profileFollowsYouLabel",
        "enableProfileDownload",
        "enablePostDownload",
        "likeUndo",
        "enableCopyComment",
        "commentSearch",
        "enableCaptionCopy",
        "enablePhotoZoom",
        "enableReelDownload",
        "forceReelScrubber",
        "offerPlaybackSpeed",
        "holdLeftSlowMotion",
        "copyMediaLink",
        "offerCopyImage",
        "offerMediaInfo",
        "offerAudioDownload",
        "uploadInstants",
        "saveInstants",
        "enableStoryDownload",
        "storyInfo",
        "storyLinks",
        "enableStoryMentions",
        "storyViewerSearch",
        "isDevEnabled"
    };

    private static final List<WeakReference<View>> sRegisteredInjectedViews = new ArrayList<>();
    private static final Map<View, Integer> sRegisteredInjectedViewsState = new WeakHashMap<>();

    public static void trackInjectedView(View view) {
        if (view == null) return;
        sRegisteredInjectedViews.add(new WeakReference<>(view));
        if (FeatureFlags.isCleanUiActive) {
            view.setVisibility(View.GONE);
        }
    }

    public static synchronized void toggleCleanUi(Activity activity) {
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

                IeToast.makeText(activity, I18n.t(activity, R.string.clean_ui_toast_on), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                ModuleLog.line("(InstaEclipse|CleanUI) Failed to create snapshot: " + e.getMessage());
            }
        } else {
            // Restore snapshot
            try {
                JSONObject json = new JSONObject(FeatureFlags.cleanUiSavedSnapshot);

                for (String flag : UI_BUTTON_FLAGS) {
                    if (json.has(flag)) {
                        try {
                            java.lang.reflect.Field field = FeatureFlags.class.getField(flag);
                            field.setBoolean(null, json.getBoolean(flag));
                        } catch (Exception e) {
                            ModuleLog.line("(InstaEclipse|CleanUI) Failed to restore flag " + flag + ": " + e.getMessage());
                        }
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

                IeToast.makeText(activity, I18n.t(activity, R.string.clean_ui_toast_off), Toast.LENGTH_SHORT).show();
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
            } catch (Exception e) {
                ModuleLog.line("(InstaEclipse|CleanUI) Error refreshing UI: " + e.getMessage());
            }
        });
    }
}
