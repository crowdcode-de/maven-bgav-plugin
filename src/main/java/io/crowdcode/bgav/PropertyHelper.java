package io.crowdcode.bgav;

import org.apache.maven.model.Model;

import java.util.regex.Pattern;

public class PropertyHelper {

    public static boolean isPlaceholder(String version) {
        return version != null && !version.trim().isEmpty() && Pattern.matches("\\$\\{.*}", version);
    }

    public static String resolveProperty(Model model, String version) {
        String replace = unkey(version);
        return model.getProperties()
                .getProperty(replace);
    }


    public static Object setProperty(Model model, String key, String value) {
        String replace = unkey(key);
        return model.getProperties()
                .setProperty(replace, value);
    }

    public static String unkey(String key) {
        return key
                .replace("${", "")
                .replace("}", "");
    }

}
