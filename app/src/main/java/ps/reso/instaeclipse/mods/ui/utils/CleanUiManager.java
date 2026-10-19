package ps.reso.instaeclipse.mods.ui.utils;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

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
        "savedOnProfile",
        "enableVoiceDownload",
        "enableStickerDownload",
        "forceReelScrubber",
        "offerPlaybackSpeed",
        "offerCopyImage",
        "offerMediaInfo"
    };

    public static synchronized void toggleCleanUi(Activity activity) {
        if (!FeatureFlags.isCleanUiActive) {
            // Take snapshot and set all to false
            try {
                JSONObject json = new JSONObject();

                json.put("showHideChatButton", FeatureFlags.showHideChatButton);
                FeatureFlags.showHideChatButton = false;

                json.put("showDeletedMessagesButton", FeatureFlags.showDeletedMessagesButton);
                FeatureFlags.showDeletedMessagesButton = false;

                json.put("jumpToFirstMessage", FeatureFlags.jumpToFirstMessage);
                FeatureFlags.jumpToFirstMessage = false;

                json.put("savedOnProfile", FeatureFlags.savedOnProfile);
                FeatureFlags.savedOnProfile = false;

                json.put("enableVoiceDownload", FeatureFlags.enableVoiceDownload);
                FeatureFlags.enableVoiceDownload = false;

                json.put("enableStickerDownload", FeatureFlags.enableStickerDownload);
                FeatureFlags.enableStickerDownload = false;

                json.put("forceReelScrubber", FeatureFlags.forceReelScrubber);
                FeatureFlags.forceReelScrubber = false;

                json.put("offerPlaybackSpeed", FeatureFlags.offerPlaybackSpeed);
                FeatureFlags.offerPlaybackSpeed = false;

                json.put("offerCopyImage", FeatureFlags.offerCopyImage);
                FeatureFlags.offerCopyImage = false;

                json.put("offerMediaInfo", FeatureFlags.offerMediaInfo);
                FeatureFlags.offerMediaInfo = false;

                FeatureFlags.cleanUiSavedSnapshot = json.toString();
                FeatureFlags.isCleanUiActive = true;

                IeToast.makeText(activity, I18n.t(activity, R.string.clean_ui_toast_on), Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                ModuleLog.line("(InstaEclipse|CleanUI) Failed to create snapshot: " + e.getMessage());
            }
        } else {
            // Restore snapshot
            try {
                JSONObject json = new JSONObject(FeatureFlags.cleanUiSavedSnapshot);

                if (json.has("showHideChatButton")) FeatureFlags.showHideChatButton = json.getBoolean("showHideChatButton");
                if (json.has("showDeletedMessagesButton")) FeatureFlags.showDeletedMessagesButton = json.getBoolean("showDeletedMessagesButton");
                if (json.has("jumpToFirstMessage")) FeatureFlags.jumpToFirstMessage = json.getBoolean("jumpToFirstMessage");
                if (json.has("savedOnProfile")) FeatureFlags.savedOnProfile = json.getBoolean("savedOnProfile");
                if (json.has("enableVoiceDownload")) FeatureFlags.enableVoiceDownload = json.getBoolean("enableVoiceDownload");
                if (json.has("enableStickerDownload")) FeatureFlags.enableStickerDownload = json.getBoolean("enableStickerDownload");
                if (json.has("forceReelScrubber")) FeatureFlags.forceReelScrubber = json.getBoolean("forceReelScrubber");
                if (json.has("offerPlaybackSpeed")) FeatureFlags.offerPlaybackSpeed = json.getBoolean("offerPlaybackSpeed");
                if (json.has("offerCopyImage")) FeatureFlags.offerCopyImage = json.getBoolean("offerCopyImage");
                if (json.has("offerMediaInfo")) FeatureFlags.offerMediaInfo = json.getBoolean("offerMediaInfo");

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
