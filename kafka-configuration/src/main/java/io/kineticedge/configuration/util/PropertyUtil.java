package io.kineticedge.configuration.util;

import java.util.Map;
import java.util.Properties;

public class PropertyUtil {

    public static Properties toProperties(final Map<String, Object> map) {
        final Properties properties = new Properties();
        properties.putAll(map);
        return properties;
    }

}
