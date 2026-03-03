package me.zed_0xff.zb_better_modlist;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * JNA binding to libsteam_api (flat C API) for Workshop unsubscribe.
 * Uses symbols from the game's libsteam_api.dylib / steam_api.dll.
 */
public final class SteamUGCJNA {

    private static final String LIBRARY = "steam_api";

    public interface SteamAPI extends Library {
        SteamAPI INSTANCE = load();

        static SteamAPI load() {
            try {
                return Native.load(LIBRARY, SteamAPI.class);
            } catch (Throwable t) {
                return null;
            }
        }

        /** Returns HSteamUser (int). */
        int SteamAPI_GetHSteamUser();

        /** Returns HSteamPipe (int). */
        int SteamAPI_GetHSteamPipe();

        /** Returns ISteamUGC* for the given user/pipe. Version 21 = STEAMUGC_INTERFACE_VERSION021. */
        Pointer SteamAPI_SteamUGC_v021(int hSteamUser, int hSteamPipe);

        /**
         * Unsubscribe from a workshop item. Returns SteamAPICall_t (non-zero if request sent).
         * Async result is delivered via Steam callbacks (game already runs them).
         */
        long SteamAPI_ISteamUGC_UnsubscribeItem(Pointer self, long publishedFileId);
    }

    private static volatile Pointer steamUGC;

    /**
     * Returns the ISteamUGC* pointer, or null if Steam is not available or JNA failed.
     */
    public static Pointer getSteamUGC() {
        if (steamUGC != null) {
            return steamUGC;
        }
        SteamAPI api = SteamAPI.INSTANCE;
        if (api == null) {
            return null;
        }
        int user = api.SteamAPI_GetHSteamUser();
        int pipe = api.SteamAPI_GetHSteamPipe();
        if (user == 0 || pipe == 0) {
            return null;
        }
        steamUGC = api.SteamAPI_SteamUGC_v021(user, pipe);
        return steamUGC;
    }

    /**
     * Unsubscribe from a workshop item by its published file ID (64-bit).
     *
     * @param publishedFileId Workshop item ID (e.g. from SteamUtils.convertStringToSteamID).
     * @return true if the unsubscribe request was sent, false if Steam/JNA unavailable or invalid ID.
     */
    public static boolean unsubscribeItem(long publishedFileId) {
        if (publishedFileId == 0) {
            return false;
        }
        Pointer ugc = getSteamUGC();
        if (ugc == null) {
            return false;
        }
        SteamAPI api = SteamAPI.INSTANCE;
        long call = api.SteamAPI_ISteamUGC_UnsubscribeItem(ugc, publishedFileId);
        // k_uAPICallInvalid = 0
        return call != 0;
    }

    public static boolean isAvailable() {
        return getSteamUGC() != null;
    }

    private SteamUGCJNA() {}
}
