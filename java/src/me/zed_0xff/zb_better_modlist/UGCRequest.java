package me.zed_0xff.zb_better_modlist;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import me.zed_0xff.zombie_buddy.Exposer;
import se.krka.kahlua.vm.KahluaTable;

/**
 * Lua-visible object that encapsulates UGCQueryHandle_t. Create via {@link #Create(Number, Number, Number)},
 * configure with setSearchText / setReturn* / setMatchAnyTag / setRankedByTrendDays, then call {@link #send()} or {@link #sendAsync()}.
 */
@Exposer.LuaClass
public final class UGCRequest {

    private static final int PZ_APP_ID = 108600;
    private static final long SEND_TIMEOUT_MS = 15_000L;
    private static final long POLL_MS = 50;
    private static final boolean DEBUG = true;

    private static String dbgThread() {
        return Thread.currentThread().getName();
    }

    private long handle;

    private UGCRequest(long handle) {
        this.handle = handle;
    }

    public boolean setSearchText(String searchText) {
        if (handle == 0) return false;
        return SteamUGC.SetSearchText(handle, searchText != null ? searchText : "");
    }

    public boolean setReturnAdditionalPreviews(boolean value) {
        if (handle == 0) return false;
        return SteamUGC.SetReturnAdditionalPreviews(handle, value);
    }

    public boolean setReturnChildren(boolean value) {
        if (handle == 0) return false;
        return SteamUGC.SetReturnChildren(handle, value);
    }

    public boolean setReturnKeyValueTags(boolean value) {
        if (handle == 0) return false;
        return SteamUGC.SetReturnKeyValueTags(handle, value);
    }

    public boolean setReturnLongDescription(boolean value) {
        if (handle == 0) return false;
        return SteamUGC.SetReturnLongDescription(handle, value);
    }

    public boolean setReturnMetadata(boolean value) {
        if (handle == 0) return false;
        return SteamUGC.SetReturnMetadata(handle, value);
    }

    public boolean setReturnOnlyIDs(boolean value) {
        if (handle == 0) return false;
        return SteamUGC.SetReturnOnlyIDs(handle, value);
    }

    public boolean setReturnTotalOnly(boolean value) {
        if (handle == 0) return false;
        return SteamUGC.SetReturnTotalOnly(handle, value);
    }

    public boolean setReturnPlaytimeStats(int unDays) {
        if (handle == 0) return false;
        return SteamUGC.SetReturnPlaytimeStats(handle, unDays);
    }

    public boolean setMatchAnyTag(boolean bMatchAnyTag) {
        if (handle == 0) return false;
        return SteamUGC.SetMatchAnyTag(handle, bMatchAnyTag);
    }

    public boolean setRankedByTrendDays(int unDays) {
        if (handle == 0) return false;
        return SteamUGC.SetRankedByTrendDays(handle, unDays);
    }

    /** Shortcut: set all SetReturn* options so the response includes everything possible. Playtime stats use 30 days. */
    public void setReturnAll() {
        if (handle == 0) return;
        SteamUGC.SetReturnAdditionalPreviews(handle, true);
        SteamUGC.SetReturnChildren(handle, true);
        SteamUGC.SetReturnKeyValueTags(handle, true);
        SteamUGC.SetReturnLongDescription(handle, true);
        SteamUGC.SetReturnMetadata(handle, true);
        SteamUGC.SetReturnOnlyIDs(handle, false);
        SteamUGC.SetReturnTotalOnly(handle, false);
        SteamUGC.SetReturnPlaytimeStats(handle, 30);
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
        if (DEBUG) System.out.println("[ZB UGCRequest] Create() entry thread=" + dbgThread());
        int q = queryType != null ? queryType.intValue() : 19;
        int m = matchingType != null ? matchingType.intValue() : 0;
        int p = page != null ? page.intValue() : 1;
        long h = SteamUGC.CreateQueryAllUGCRequest(q, m, PZ_APP_ID, PZ_APP_ID, p);
        if (DEBUG) System.out.println("[ZB UGCRequest] Create() CreateQueryAllUGCRequest returned handle=" + h);
        if (h == 0 || h == -1) return null;
        return new UGCRequest(h);
    }

    /**
     * Sends the query and blocks until the response is ready (Java-side polling on a worker thread). Returns a list of item tables (modId, and optionally previewURL, metadata, tags) or null on failure/timeout.
     */
    public ArrayList<KahluaTable> send() {
        if (DEBUG) System.out.println("[ZB UGCRequest] send() entry handle=" + handle + " thread=" + dbgThread());
        if (handle == 0) return null;
        long hAPICall = SteamUGC.SendQueryUGCRequest(handle);
        if (DEBUG) System.out.println("[ZB UGCRequest] send() SendQueryUGCRequest returned hAPICall=" + hAPICall);
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
            if (DEBUG) System.out.println("[ZB UGCRequest] send() worker started");
            try {
                long deadline = System.currentTimeMillis() + SEND_TIMEOUT_MS;
                while (System.currentTimeMillis() < deadline) {
                    if (SteamUtils.IsAPICallCompleted(Long.valueOf(hAPICall))) {
                        if (DEBUG) System.out.println("[ZB UGCRequest] send() worker: API completed, setting handle");
                        completedHandle.set(Long.valueOf(h));
                        break;
                    }
                    Thread.sleep(POLL_MS);
                }
                if (completedHandle.get() == null) {
                    if (DEBUG) System.out.println("[ZB UGCRequest] send() worker: timeout, releasing handle");
                    SteamUGC.ReleaseQueryUGCRequest(Long.valueOf(h));
                }
            } catch (Throwable t) {
                System.out.println("[ZB UGCRequest] send() worker throw: " + t);
                t.printStackTrace();
                SteamUGC.ReleaseQueryUGCRequest(Long.valueOf(h));
            } finally {
                latch.countDown();
                if (DEBUG) System.out.println("[ZB UGCRequest] send() worker finished");
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
        if (DEBUG) System.out.println("[ZB UGCRequest] send() await done handleToBuild=" + handleToBuild);
        if (handleToBuild == null || handleToBuild.longValue() == 0) return null;
        if (DEBUG) System.out.println("[ZB UGCRequest] send() calling buildResponseTable thread=" + dbgThread());
        ArrayList<KahluaTable> result = UGCResponsePending.buildResponseTable(handleToBuild.longValue());
        if (DEBUG) System.out.println("[ZB UGCRequest] send() buildResponseTable done size=" + (result != null ? result.size() : -1));
        return result;
    }

    /**
     * Sends the query and returns a poller; call poll() each frame until it returns the response table.
     */
    public UGCResponsePending sendAsync() {
        if (DEBUG) System.out.println("[ZB UGCRequest] sendAsync() entry handle=" + handle);
        if (handle == 0) return null;
        long hAPICall = SteamUGC.SendQueryUGCRequest(handle);
        if (DEBUG) System.out.println("[ZB UGCRequest] sendAsync() SendQueryUGCRequest returned hAPICall=" + hAPICall);
        if (hAPICall == 0) {
            SteamUGC.ReleaseQueryUGCRequest(handle);
            handle = 0;
            return null;
        }
        long h = handle;
        handle = 0;
        if (DEBUG) System.out.println("[ZB UGCRequest] sendAsync() creating UGCResponsePending");
        return new UGCResponsePending(h, hAPICall);
    }
}
