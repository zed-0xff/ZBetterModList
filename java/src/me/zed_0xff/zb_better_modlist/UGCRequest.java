package me.zed_0xff.zb_better_modlist;

import me.zed_0xff.zombie_buddy.Exposer;

/**
 * Lua-visible object that encapsulates UGCQueryHandle_t. Create via {@link #Create(Number, Number, Number)},
 * configure with {@link #SetSearchText(String)}, then call {@link #Send()}. Send() does not block—it
 * returns a {@link UGCResponsePending}. Call {@link UGCResponsePending#Poll()} each frame (e.g. in
 * a timer or update loop) until it returns the {@link UGCResponse}, then call
 * {@link UGCResponse#Release()} when done. Blocking the main thread in Send() crashes the app.
 */
@Exposer.LuaClass
public final class UGCRequest {

    private static final int PZ_APP_ID = 108600;

    private long handle;

    private UGCRequest(long handle) {
        this.handle = handle;
    }

    /**
     * Creates a new UGC query request for the given page. Uses Project Zomboid app ID for creator/consumer.
     *
     * @param queryType   e.g. 19 = RankedByLastUpdatedDate (see k_EUGCQuery_* in Lua).
     * @param matchingType e.g. 0 = Items (see k_EUGCMatchingUGCType_*).
     * @param page        page number (1-based).
     * @return new UGCRequest, or null if creation failed (Steam unavailable / invalid params).
     */
    public static UGCRequest Create(Number queryType, Number matchingType, Number page) {
        int q = queryType != null ? queryType.intValue() : 19;
        int m = matchingType != null ? matchingType.intValue() : 0;
        int p = page != null ? page.intValue() : 1;
        long h = SteamUGC.CreateQueryAllUGCRequest(q, m, PZ_APP_ID, PZ_APP_ID, p);
        if (h == 0 || h == -1) return null;
        return new UGCRequest(h);
    }

    /**
     * Sets the search text for this query (optional). Call before {@link #Send()}.
     *
     * @param searchText search string; null or empty is ignored.
     * @return true if set successfully.
     */
    public boolean SetSearchText(String searchText) {
        if (handle == 0) return false;

        return SteamUGC.SetSearchText(handle, searchText != null ? searchText : "");
    }

    /**
     * Starts the query and returns immediately. Do not block the main thread—Steam needs it for callbacks.
     * Poll the returned pending object each frame until {@link UGCResponsePending#Poll()} returns the response.
     *
     * @return UGCResponsePending to poll, or null if send failed.
     */
    public UGCResponsePending Send() {
        if (handle == 0) return null;
        long hAPICall = SteamUGC.SendQueryUGCRequest(Long.valueOf(handle));
        if (hAPICall == 0) {
            SteamUGC.ReleaseQueryUGCRequest(Long.valueOf(handle));
            handle = 0;
            return null;
        }
        long h = handle;
        handle = 0;
        return new UGCResponsePending(h, hAPICall);
    }
}
