package ps.reso.instaeclipse.mods.ui.utils;

import android.app.Activity;
import android.widget.Toast;

import java.util.Map;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.mods.extras.AirplaneModeHook;
import ps.reso.instaeclipse.mods.ui.theme.IgThemeEngine;
import ps.reso.instaeclipse.mods.ui.theme.IgThemeHook;
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.utils.core.SettingsProfiles;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureManager;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;
import ps.reso.instaeclipse.utils.toast.IeToast;

/**
 * Settings profiles inside Instagram (see {@link SettingsProfiles}): the Home shortcut and the
 * profile manager in the InstaEclipse menu. Every change is saved and synced like a menu change.
 */
public final class ProfileManager {

    private ProfileManager() {}

    /** The stored profiles (the first one is created when the settings are first loaded). */
    public static SettingsProfiles store() {
        SettingsProfiles p = SettingsProfiles.parse(FeatureFlags.settingsProfiles);
        if (p.isEmpty()) p = SettingsProfiles.create("1");
        return p;
    }

    /** Saves a change that only concerns the profile list (add, rename, delete...). */
    public static void save(SettingsProfiles p) {
        FeatureFlags.settingsProfiles = p.serialize();
        SettingsManager.saveAllFlags();
    }

    /** Home shortcut: the next profile, confirmed as chosen in the profile settings. */
    public static void switchNext(Activity activity) {
        SettingsProfiles p = store();
        String next = p.nextId();
        if (next == null) {
            IeToast.makeText(activity, I18n.t(activity, R.string.profiles_need_two), Toast.LENGTH_SHORT).show();
            return;
        }
        activate(activity, p, next);
        SettingsProfiles.Profile now = store().active();
        if (FeatureFlags.profileSwitchVibrate) VibrationUtil.vibrate(activity);
        if (FeatureFlags.profileSwitchShowName && now != null)
            IeToast.makeText(activity, I18n.t(activity, R.string.profiles_switched, now.name), Toast.LENGTH_SHORT).show();
    }

    /** Makes {@code id} the active profile: its settings replace the current ones. */
    public static void activate(Activity activity, SettingsProfiles p, String id) {
        Map<String, Object> changes = p.activate(id, SettingsManager.currentValues(), FeatureFlags.defaults());
        apply(activity, changes);
    }

    /** Turns every setting of {@code id} off (the current settings too, if it is the active one). */
    public static void reset(Activity activity, SettingsProfiles p, String id) {
        Map<String, Object> changes = p.reset(id, SettingsManager.currentValues(), FeatureFlags.defaults());
        apply(activity, changes);
    }

    /** Writes the settings an operation changed, then updates what depends on them right away. */
    private static void apply(Activity activity, Map<String, Object> changes) {
        if (changes.isEmpty()) return;
        try {
            SettingsManager.applyValues(changes);
            FeatureManager.refreshFeatureStatus();
            if (changes.containsKey("airplaneMode")) AirplaneModeHook.apply();
            boolean theme = false;
            for (String key : changes.keySet()) if (key.startsWith("theme") || key.startsWith("customTheme")) theme = true;
            if (theme) {
                IgThemeEngine.invalidate();
                IgThemeHook.refreshCurrentActivity();
            }
            InjectedUi.refreshScreen(activity);
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse|Profiles) switch failed: " + t);
        }
    }
}
