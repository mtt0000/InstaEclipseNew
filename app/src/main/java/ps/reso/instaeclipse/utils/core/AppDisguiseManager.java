package ps.reso.instaeclipse.utils.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import ps.reso.instaeclipse.BuildConfig;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * App disguise: which launcher entry (activity-alias in the manifest) the companion app shows,
 * each with its own icon and name, like Lawnchair's private space disguises.
 */
public class AppDisguiseManager {

    public static final String DEFAULT = "default";
    public static final String CALCULATOR = "calculator";
    public static final String NOTES = "notes";
    public static final String FILE_MANAGER = "file_manager";
    public static final String CALENDAR = "calendar";
    public static final String WEATHER = "weather";

    /** The disguises, in picker order: setting key, launcher alias, name and icon. */
    public enum Option {
        DEFAULT_OPTION(DEFAULT, ".MainActivityDefault", R.string.app_name, R.mipmap.ic_launcher),
        CALCULATOR_OPTION(CALCULATOR, ".MainActivityCalculator", R.string.app_name_calculator, R.drawable.ic_disguise_calculator),
        NOTES_OPTION(NOTES, ".MainActivityNotes", R.string.app_name_notes, R.drawable.ic_disguise_notes),
        FILE_MANAGER_OPTION(FILE_MANAGER, ".MainActivityFileManager", R.string.app_name_file_manager, R.drawable.ic_disguise_files),
        CALENDAR_OPTION(CALENDAR, ".MainActivityCalendar", R.string.app_name_calendar, R.drawable.ic_disguise_calendar),
        WEATHER_OPTION(WEATHER, ".MainActivityWeather", R.string.app_name_weather, R.drawable.ic_disguise_weather);

        public final String key;
        final String alias;
        public final int labelRes;
        public final int iconRes;

        Option(String key, String alias, int labelRes, int iconRes) {
            this.key = key;
            this.alias = alias;
            this.labelRes = labelRes;
            this.iconRes = iconRes;
        }

        public static Option of(String key) {
            for (Option o : values()) if (o.key.equals(key)) return o;
            return DEFAULT_OPTION;
        }
    }

    /**
     * Applies a disguise picked in a menu. In the companion app: switches the launcher entry (the
     * caller stores the choice in the companion's own settings and sends it to Instagram);
     * SettingsManager is Instagram's settings store and isn't set up in this process, so calling
     * it here crashed the app before the icon changed. In Instagram's process: saves the setting,
     * which reaches the companion, the only app allowed to switch it (see PrefsSyncProvider).
     */
    public static void applyDisguise(Context context, String disguiseType) {
        if (disguiseType == null) disguiseType = DEFAULT;
        FeatureFlags.appDisguise = disguiseType;
        if (BuildConfig.APPLICATION_ID.equals(context.getPackageName())) applyAliases(context, disguiseType);
        else SettingsManager.saveAllFlags();
    }

    /** Companion app start: brings the launcher entries back in line with the saved disguise, so
     *  exactly one is enabled even after an interrupted switch. Cheap when nothing changed. */
    public static void reconcile(Context context) {
        try {
            String saved = CommonUtils.companionCache(context).getString("appDisguise", DEFAULT);
            applyAliases(context, saved);
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse) App disguise: launcher check failed: " + t);
        }
    }

    /** Enables the chosen launcher alias and disables the others. Companion app process only:
     *  another app may not change this package's components. */
    public static void applyAliases(Context context, String disguiseType) {
        Option chosen = Option.of(disguiseType);
        PackageManager pm = context.getPackageManager();
        String pkg = BuildConfig.APPLICATION_ID;
        // Only the entries whose state changes, chosen one first: the app always keeps one entry.
        java.util.List<ComponentName> components = new java.util.ArrayList<>();
        java.util.List<Integer> states = new java.util.ArrayList<>();
        for (Option o : orderChosenFirst(chosen)) {
            ComponentName c = new ComponentName(pkg, pkg + o.alias);
            int state = o == chosen ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            if (isEnabled(pm, c, o) == (o == chosen)) continue;
            components.add(c);
            states.add(state);
        }
        if (components.isEmpty()) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                // One change for the launcher: switched one by one, several launchers dropped the
                // app from the drawer until they were restarted.
                java.util.List<PackageManager.ComponentEnabledSetting> batch = new java.util.ArrayList<>();
                for (int i = 0; i < components.size(); i++)
                    batch.add(new PackageManager.ComponentEnabledSetting(components.get(i), states.get(i),
                            PackageManager.DONT_KILL_APP));
                pm.setComponentEnabledSettings(batch);
            } else {
                for (int i = 0; i < components.size(); i++)
                    pm.setComponentEnabledSetting(components.get(i), states.get(i), PackageManager.DONT_KILL_APP);
            }
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse) App disguise: launcher entry not switched: " + t);
            // Never leave the app without a launcher entry.
            try {
                pm.setComponentEnabledSetting(new ComponentName(pkg, pkg + chosen.alias),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            } catch (Throwable ignored) {}
        }
    }

    /** Effective state: a never-changed entry keeps the manifest's (only Default is enabled there). */
    private static boolean isEnabled(PackageManager pm, ComponentName c, Option o) {
        int state = pm.getComponentEnabledSetting(c);
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) return o == Option.DEFAULT_OPTION;
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    private static java.util.List<Option> orderChosenFirst(Option chosen) {
        java.util.List<Option> out = new java.util.ArrayList<>();
        out.add(chosen);
        for (Option o : Option.values()) if (o != chosen) out.add(o);
        return out;
    }

    /**
     * Rows for the disguise picker: each option's icon beside its name, in a single-choice list.
     * {@code res} holds the module's drawables (the module's own Resources inside Instagram).
     */
    public static android.widget.ListAdapter pickerAdapter(Context context, android.content.res.Resources res, String[] labels) {
        float density = context.getResources().getDisplayMetrics().density;
        int size = Math.round(32 * density);
        int pad = Math.round(16 * density);
        Option[] options = Option.values();
        return new android.widget.ArrayAdapter<String>(context, android.R.layout.select_dialog_singlechoice,
                android.R.id.text1, labels) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View row = super.getView(position, convertView, parent);
                android.widget.TextView text = row.findViewById(android.R.id.text1);
                if (text == null) return row;
                android.graphics.drawable.Drawable icon = null;
                try {
                    icon = res.getDrawable(options[position].iconRes, null).mutate();
                    icon.setBounds(0, 0, size, size);
                } catch (Throwable ignored) {}
                text.setCompoundDrawablesRelative(icon, null, null, null);
                text.setCompoundDrawablePadding(pad);
                return row;
            }
        };
    }
}
