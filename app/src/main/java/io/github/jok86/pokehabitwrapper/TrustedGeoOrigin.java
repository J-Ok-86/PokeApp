package io.github.jok86.pokehabitwrapper;

import java.net.URI;

/** Origin and visible-main-frame guard for WebView geolocation and native messaging.
 * Pure Java, so we can test its exact security behavior without an Android device.
 */
final class TrustedGeoOrigin {
    private static final String HOST = "j-ok-86.github.io";
    private static final String APP_PATH = "/PokeHabit";

    static boolean accepts(String origin, String visibleUrl) {
        try {
            URI o = new URI(origin);
            URI page = new URI(visibleUrl);
            if (!secureTrustedHost(o) || !secureTrustedHost(page)) return false;
            // The supplied value must be an ORIGIN (no request path, query or fragment).
            String path = o.getRawPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) return false;
            if (o.getRawQuery() != null || o.getRawFragment() != null) return false;
            String mainPath = page.normalize().getRawPath();
            return APP_PATH.equals(mainPath) ||
                (mainPath != null && mainPath.startsWith(APP_PATH + "/"));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean secureTrustedHost(URI u) {
        return u != null && "https".equalsIgnoreCase(u.getScheme()) &&
            HOST.equalsIgnoreCase(u.getHost()) &&
            (u.getPort() == -1 || u.getPort() == 443) &&
            u.getRawUserInfo() == null;
    }
    private TrustedGeoOrigin() { }
}
