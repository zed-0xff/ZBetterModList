package me.zed_0xff.zb_better_modlist;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import me.zed_0xff.zombie_buddy.Exposer;

/**
 * Lua-facing helpers for native Steam operations. PZ's own {@code zombie.core.znet.SteamWorkshop}
 * has {@code SubscribeItem} but no unsubscribe, and its subscribe path requires a Java callback
 * parameter that's awkward to drive from Lua — so we bind both via JNA directly.
 */
@Exposer.LuaClass
public final class SteamLuaHelper {

    private SteamLuaHelper() {}

    private interface SteamAPI extends Library {
        SteamAPI INSTANCE = load();
        static SteamAPI load() {
            try { return Native.load("steam_api", SteamAPI.class); } catch (Throwable t) { return null; }
        }
        int SteamAPI_GetHSteamUser();
        int SteamAPI_GetHSteamPipe();
        Pointer SteamAPI_SteamUGC_v021(int hSteamUser, int hSteamPipe);
        long SteamAPI_ISteamUGC_SubscribeItem(Pointer self, long publishedFileId);
        long SteamAPI_ISteamUGC_UnsubscribeItem(Pointer self, long publishedFileId);
    }

    private static volatile Pointer steamUGC;

    private static Pointer getUGC() {
        if (steamUGC != null) return steamUGC;
        SteamAPI api = SteamAPI.INSTANCE;
        if (api == null) return null;
        int user = api.SteamAPI_GetHSteamUser();
        int pipe = api.SteamAPI_GetHSteamPipe();
        if (user == 0 || pipe == 0) return null;
        steamUGC = api.SteamAPI_SteamUGC_v021(user, pipe);
        return steamUGC;
    }

    @FunctionalInterface
    private interface UGCCall { long invoke(SteamAPI api, Pointer ugc, long id); }

    private static boolean dispatch(String workshopId, UGCCall call) {
        if (workshopId == null || workshopId.isEmpty()) return false;
        if (!zombie.core.znet.SteamUtils.isValidSteamID(workshopId)) return false;
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long id = zombie.core.znet.SteamUtils.convertStringToSteamID(workshopId);
        if (id == 0) return false;
        return call.invoke(SteamAPI.INSTANCE, ugc, id) != 0;
    }

    /**
     * Subscribe to a Steam Workshop item by its workshop ID. Non-blocking; the download happens
     * asynchronously on Steam's side. Safe to call on IDs the user is already subscribed to
     * (Steam will simply no-op).
     *
     * @param workshopId published file ID as a decimal string.
     * @return true if the request was sent, false on bad input or Steam unavailable.
     */
    public static boolean subscribeToWorkshopItem(String workshopId) {
        return dispatch(workshopId, (api, ugc, id) -> api.SteamAPI_ISteamUGC_SubscribeItem(ugc, id));
    }

    /**
     * Unsubscribe from a Steam Workshop mod by its workshop ID. Sends the request to Steam;
     * the item is removed from disk after the game quits.
     *
     * @param workshopId published file ID as a decimal string.
     * @return true if the request was sent, false on bad input or Steam unavailable.
     */
    public static boolean unsubscribeFromWorkshopItem(String workshopId) {
        return dispatch(workshopId, (api, ugc, id) -> api.SteamAPI_ISteamUGC_UnsubscribeItem(ugc, id));
    }
}
