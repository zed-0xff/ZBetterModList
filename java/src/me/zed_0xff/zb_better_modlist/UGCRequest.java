package me.zed_0xff.zb_better_modlist;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import me.zed_0xff.zombie_buddy.Exposer;
import se.krka.kahlua.vm.KahluaTable;

/**
 * Lua-visible object that encapsulates UGCQueryHandle_t. Create via {@link #Create(Number, Number, Number)},
 * configure with setSearchText / addRequiredTag / setReturn* / setMatchAnyTag / setRankedByTrendDays / setAllowCachedResponse, then call {@link #send()} or {@link #sendAsync()}.
 */
@Exposer.LuaClass
public final class UGCRequest {

    private static final int PZ_APP_ID         = 108600;
    private static final long SEND_TIMEOUT_MS  = 15_000L;
    private static final long POLL_MS          = 50;
    private static final int MAX_CACHE_SECONDS = 600;

    private long handle;

    private UGCRequest(long handle) {
        this.handle = handle;
    }

    public UGCRequest setSearchText(String searchText) {
        if (handle != 0) SteamUGC.SetSearchText(handle, searchText != null ? searchText : "");
        return this;
    }

    public UGCRequest addRequiredTag(String tagName) {
        if (handle != 0) SteamUGC.AddRequiredTag(handle, tagName);
        return this;
    }

    public UGCRequest setReturnAdditionalPreviews(boolean value) {
        if (handle != 0) SteamUGC.SetReturnAdditionalPreviews(handle, value);
        return this;
    }

    public UGCRequest setReturnChildren(boolean value) {
        if (handle != 0) SteamUGC.SetReturnChildren(handle, value);
        return this;
    }

    public UGCRequest setReturnKeyValueTags(boolean value) {
        if (handle != 0) SteamUGC.SetReturnKeyValueTags(handle, value);
        return this;
    }

    public UGCRequest setReturnLongDescription(boolean value) {
        if (handle != 0) SteamUGC.SetReturnLongDescription(handle, value);
        return this;
    }

    public UGCRequest setReturnMetadata(boolean value) {
        if (handle != 0) SteamUGC.SetReturnMetadata(handle, value);
        return this;
    }

    public UGCRequest setReturnOnlyIDs(boolean value) {
        if (handle != 0) SteamUGC.SetReturnOnlyIDs(handle, value);
        return this;
    }

    public UGCRequest setReturnTotalOnly(boolean value) {
        if (handle != 0) SteamUGC.SetReturnTotalOnly(handle, value);
        return this;
    }

    public UGCRequest setReturnPlaytimeStats(int unDays) {
        if (handle != 0) SteamUGC.SetReturnPlaytimeStats(handle, unDays);
        return this;
    }

    public UGCRequest setMatchAnyTag(boolean bMatchAnyTag) {
        if (handle != 0) SteamUGC.SetMatchAnyTag(handle, bMatchAnyTag);
        return this;
    }

    public UGCRequest setRankedByTrendDays(int unDays) {
        if (handle != 0) SteamUGC.SetRankedByTrendDays(handle, unDays);
        return this;
    }

    /** Allow cached query results. maxAgeSeconds > 0 enables cache (e.g. 600); 0 disables. */
    public UGCRequest setAllowCachedResponse(int maxAgeSeconds) {
        if (handle != 0) SteamUGC.SetAllowCachedResponse(handle, maxAgeSeconds);
        return this;
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
     * Sends the query and blocks until the response is ready (Java-side polling on a worker thread). Returns a list of item tables (modId, and optionally previewURL, metadata, tags) or null on failure/timeout.
     */
    public ArrayList<KahluaTable> send() {
        if (handle == 0) return null;

        SteamUGC.SetAllowCachedResponse(handle, MAX_CACHE_SECONDS);
        long hAPICall = SteamUGC.SendQueryUGCRequest(handle);

        if (hAPICall == 0) {
            SteamUGC.ReleaseQueryUGCRequest(handle);
            handle = 0;
            return null;
        }
        long h = handle;
        handle = 0;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Long> completedHandle = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                long deadline = System.currentTimeMillis() + SEND_TIMEOUT_MS;
                while (System.currentTimeMillis() < deadline) {
                    if (SteamUtils.IsAPICallCompleted(Long.valueOf(hAPICall))) {
                        completedHandle.set(Long.valueOf(h));
                        break;
                    }
                    Thread.sleep(POLL_MS);
                }
                if (completedHandle.get() == null) {
                    SteamUGC.ReleaseQueryUGCRequest(Long.valueOf(h));
                }
            } catch (Throwable t) {
                SteamUGC.ReleaseQueryUGCRequest(Long.valueOf(h));
            } finally {
                latch.countDown();
            }
        }, "UGCRequest-send-worker");
        worker.setDaemon(true);
        worker.start();
        try {
            latch.await(SEND_TIMEOUT_MS + 1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        Long handleToBuild = completedHandle.get();
        if (handleToBuild == null || handleToBuild.longValue() == 0) return null;
        return UGCResponsePending.buildResponseTable(handleToBuild.longValue());
    }

    /**
     * Sends the query and returns a poller; call poll() each frame until it returns the response table.
     */
    public UGCResponsePending sendAsync() {
        if (handle == 0) return null;

        SteamUGC.SetAllowCachedResponse(handle, MAX_CACHE_SECONDS);
        long hAPICall = SteamUGC.SendQueryUGCRequest(handle);

        if (hAPICall == 0) {
            SteamUGC.ReleaseQueryUGCRequest(handle);
            handle = 0;
            return null;
        }
        long h = handle;
        handle = 0;
        return new UGCResponsePending(h, hAPICall);
    }
}
