package ps.reso.instaeclipse.utils.core;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import ps.reso.instaeclipse.utils.feature.ExtrasFeatures;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;

/**
 * Every setting must survive a reload. saveAllFlags writes all FeatureFlags fields by reflection,
 * but loadAllFlags reads each one explicitly (with its default): a field missing there is reset on
 * the next load, like emptyGeneralFolder was ("Show 'General' folder as empty" switching itself off).
 */
public class SettingsPersistenceTest {

    private static String settingsManagerSource() throws Exception {
        String rel = "src/main/java/ps/reso/instaeclipse/utils/core/SettingsManager.java";
        File f = new File(rel);
        if (!f.exists()) f = new File("app/" + rel); // run from the project root instead of the module
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void everySettingIsLoaded() throws Exception {
        String src = settingsManagerSource();
        String load = src.substring(src.indexOf("public static void loadAllFlags("));
        List<String> extras = Arrays.asList(ExtrasFeatures.flags());
        List<String> missing = new ArrayList<>();
        for (Field f : FeatureFlags.class.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (!Modifier.isStatic(mod) || Modifier.isFinal(mod)) continue;
            if (extras.contains(f.getName())) continue; // loaded by the ExtrasFeatures loop
            Pattern read = Pattern.compile("FeatureFlags\\." + f.getName() + "\\s*=[^;]*\"" + f.getName() + "\"");
            if (!read.matcher(load).find()) missing.add(f.getName());
        }
        assertTrue("Not read back in loadAllFlags: " + missing, missing.isEmpty());
    }

    @Test
    public void saveWritesEveryFlagByReflection() throws Exception {
        String src = settingsManagerSource();
        String save = src.substring(src.indexOf("public static void saveAllFlags()"),
                src.indexOf("private static void stampChangedKeys("));
        assertTrue("saveAllFlags must keep its putEveryFlag safety net", save.contains("putEveryFlag(editor);"));
    }
}
