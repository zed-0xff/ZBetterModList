package me.zed_0xff.zb_better_modlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.zed_0xff.zombie_buddy.Exposer;

/**
 * Lua-facing helpers for Steam API. Use this from Lua when you want decoded results or
 * convenience wrappers. For raw API behaviour, call {@link SteamUGC} and {@link SteamUtils}
 * directly (they mirror the Steam API).
 */
@Exposer.LuaClass
public final class SteamLuaHelper {

    /** Callback type for SteamUGCQueryCompleted_t (match SDK; 3411 or 3401). */
    private static final int STEAM_UGC_QUERY_COMPLETED_CALLBACK = 3411;
    /** Struct size with padding; use 32 to avoid native overrun if layout differs. */
    private static final int STEAM_UGC_QUERY_COMPLETED_STRUCT_SIZE = 32;

    private SteamLuaHelper() {}

    /**
     * Unsubscribe from a Steam Workshop mod by its workshop ID (e.g. "1234567890").
     * Sends the request to Steam; the item is removed after the game quits.
     *
     * @param workshopId Workshop published file ID as string (from mod info / getWorkshopID).
     * @return true if the unsubscribe request was sent, false if Steam unavailable or invalid ID.
     */
    public static boolean unsubscribeFromWorkshopItem(String workshopId) {
        if (workshopId == null || workshopId.isEmpty()) return false;
        if (!zombie.core.znet.SteamUtils.isValidSteamID(workshopId)) return false;
        long id = zombie.core.znet.SteamUtils.convertStringToSteamID(workshopId);
        return SteamUGC.UnsubscribeItem(id);
    }

    private static final int PZ_APP_ID = 108600;

    /** Read uint64 at offset 0, little-endian (SteamUGCDetails_t first field is published file ID). */
    private static long readPublishedFileId(byte[] b) {
        if (b == null || b.length < 8) return 0;
        long lo = (b[0] & 0xFF) | ((b[1] & 0xFF) << 8) | ((b[2] & 0xFF) << 16) | ((b[3] & 0xFF) << 24);
        long hi = (b[4] & 0xFF) | ((b[5] & 0xFF) << 8) | ((b[6] & 0xFF) << 16) | ((b[7] & 0xFF) << 24);
        return (lo & 0xFFFFFFFFL) | (hi << 32);
    }

    /**
     * Create a QueryAllUGC request, block until complete, return list of published file IDs as strings.
     * Params from Lua: queryType, matchingType, page, searchText (all optional; nil = use default).
     * Defaults: queryType=19, matchingType=0, page=1, searchText="". Returns nil on failure; otherwise a table of ID strings (1-based in Lua).
     * Blocking.
     */
    public static List<String> testQueryAllUGCRequests(Number queryType, Number matchingType, Number page, String searchText) {
        int q = queryType != null ? queryType.intValue() : 19;
        int m = matchingType != null ? matchingType.intValue() : 0;
        int p = page != null ? page.intValue() : 1;
        String search = searchText != null ? searchText : "";

        long handle = SteamUGC.CreateQueryAllUGCRequest(q, m, PZ_APP_ID, PZ_APP_ID, p);
        if (handle == 0 || handle == -1) return null;
        if (!search.isEmpty()) SteamUGC.SetSearchText(handle, search);
        long hAPICall = SteamUGC.SendQueryUGCRequest(handle);
        if (hAPICall == 0) {
            SteamUGC.ReleaseQueryUGCRequest(handle);
            return null;
        }
        try {
            while (!SteamUtils.IsAPICallCompleted(Long.valueOf(hAPICall))) {
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return Collections.emptyList(); }
            }
            List<String> ids = new ArrayList<>();
            byte[] buffer = new byte[8192];
            for (int i = 0; i < 10000; i++) {
                if (!SteamUGC.GetQueryUGCResult(handle, i, buffer)) break;
                long id = readPublishedFileId(buffer);
                if (id != 0) ids.add(Long.toString(id));
            }
            return ids;
        } finally {
            SteamUGC.ReleaseQueryUGCRequest(handle);
        }
    }

}
