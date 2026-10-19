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
        // === Direct Messages / Header e pulsanti di azione sui messaggi ===
        "showHideChatButton",            // Pulsante nascondi/mostra chat nell'header dei DM
        "showDeletedMessagesButton",     // Pulsante cronologia messaggi eliminati/annullati nell'header dei DM
        "jumpToFirstMessage",            // Pulsante per scorrere al primo messaggio nell'header dei DM
        "enableVoiceDownload",           // Icona/pulsante di download sulle bolle dei messaggi vocali
        "enableStickerDownload",         // Icona/pulsante di download per sticker e GIF
        "enableDmMediaDownload",         // Pulsante di salvataggio personalizzato su foto/video all'interno della chat

        // === Quick Toggle nella Inbox dei DM ===
        "quickToggleSeen",               // Icona/pulsante quick toggle del Ghost Mode accanto all'header della inbox

        // === Pulsanti personalizzati nella schermata del Profilo ===
        "savedOnProfile",                // Scorciatoia per la raccolta 'Elementi salvati' nell'header del profilo
        "followersHistory",              // Scorciatoia per la cronologia/tracciamento dei follower sul profilo
        "followListExport",              // Pulsante per esportare la lista follower/seguiti in formato CSV

        // === Controlli overlay su Feed, Post e Reels ===
        "enablePostDownload",            // Icona/pulsante di download personalizzato sui post del feed
        "enableReelDownload",            // Pulsante di download nella vista Reels
        "forceReelScrubber",             // Barra di avanzamento (seek-bar/scrubber) iniettata nei Reels
        "offerPlaybackSpeed",            // Pulsante overlay per il controllo della velocità di riproduzione (0.5x - 3x)
        "holdLeftSlowMotion",            // Controllo gesture in overlay per il rallentatore nei Reels
        "enablePhotoZoom",               // Pulsante/trigger in overlay per lo zoom delle foto

        // === Menu e azioni contestuali sui media ===
        "copyMediaLink",                 // Pulsante/voce per copiare il link diretto
        "offerCopyImage",                // Pulsante/voce per copiare l'immagine negli appunti
        "offerMediaInfo",                // Pulsante per visualizzare i dettagli tecnici del media
        "offerAudioDownload",            // Pulsante per scaricare solo la traccia audio
        "uploadInstants",                // Pulsante picker della galleria iniettato per gli Instant
        "saveInstants",                  // Pulsante per salvare gli Instant

        // === Controlli iniettati nelle Storie ===
        "enableStoryDownload",           // Pulsante di download nel visualizzatore delle storie
        "storyInfo",                     // Pulsante/overlay con i dettagli della storia
        "storyLinks",                    // Pulsanti per estrarre i link presenti nella storia
        "enableStoryMentions",           // Pulsante per visualizzare tutte le menzioni della storia
        "storyViewerSearch",             // Barra di ricerca iniettata nella lista degli spettatori della storia

        // === Pulsanti iniettati per Sviluppatori e Diagnostica ===
        "isDevEnabled"                   // Scorciatoia/pulsanti per le Opzioni Sviluppatore iniettate
    };

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
