package me.zed_0xff.zb_better_modlist;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import me.zed_0xff.zombie_buddy.Exposer;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;

/**
 * Lua-visible object returned from {@link UGCRequest#sendAsync()} while the query is in flight.
 * Call {@link #poll()} each frame; when the worker sees completion it returns an ArrayList of
 * item tables (each with modId, and optionally previewURL, metadata, tags). No release needed.
 * Response tables are built on the calling (main/Lua) thread to avoid cross-thread Kahlua access.
 */
@Exposer.LuaClass
public final class UGCResponsePending {

    private static final long POLL_MS = 50;
    private static final long TIMEOUT_MS = 15_000L;
    private static final int RESULTS_PER_PAGE = 50;
    /** Must be >= sizeof(SteamUGCDetails_t) in the Steam SDK; 1024 was too small and caused native crash at index 3+. */
    private static final int DETAILS_BUFFER_SIZE = 65536;
    private static final boolean DEBUG = true;

    private static String dbgThread() {
        return Thread.currentThread().getName();
    }

    private volatile long queryHandle;
    private volatile long apiCallHandle;
    /** When worker sees completion, sets this to the handle; poll() builds on main thread. */
    private final AtomicLong handleReadyToBuild = new AtomicLong(0);
    private volatile ArrayList<KahluaTable> cachedList;

    UGCResponsePending(long queryHandle, long apiCallHandle) {
        this.queryHandle = queryHandle;
        this.apiCallHandle = apiCallHandle;
        if (DEBUG) System.out.println("[ZB UGCResponsePending] created queryHandle=" + queryHandle + " apiCallHandle=" + apiCallHandle + " thread=" + dbgThread());
        Thread t = new Thread(this::runWorker, "UGCResponsePending-worker");
        t.setDaemon(true);
        t.start();
    }

    private void runWorker() {
        if (DEBUG) System.out.println("[ZB UGCResponsePending] worker started");
        try {
            long deadline = System.currentTimeMillis() + TIMEOUT_MS;
            int polls = 0;
            while (queryHandle != 0 && apiCallHandle != 0 && System.currentTimeMillis() < deadline) {
                boolean completed = SteamUtils.IsAPICallCompleted(Long.valueOf(apiCallHandle));
                polls++;
                if (DEBUG && (polls <= 3 || polls % 100 == 0)) System.out.println("[ZB UGCResponsePending] poll #" + polls + " IsAPICallCompleted=" + completed);
                if (completed) {
                    if (DEBUG) System.out.println("[ZB UGCResponsePending] completed, signaling main thread to build");
                    handleReadyToBuild.set(queryHandle);
                    queryHandle = 0;
                    apiCallHandle = 0;
                    break;
                }
                Thread.sleep(POLL_MS);
            }
            if (queryHandle != 0) {
                if (DEBUG) System.out.println("[ZB UGCResponsePending] timeout or exit, releasing handle (polls=" + polls + ")");
                SteamUGC.ReleaseQueryUGCRequest(Long.valueOf(queryHandle));
                queryHandle = 0;
                apiCallHandle = 0;
            }
        } catch (Throwable t) {
            System.out.println("[ZB UGCResponsePending] worker throw: " + t);
            t.printStackTrace();
            if (queryHandle != 0) SteamUGC.ReleaseQueryUGCRequest(Long.valueOf(queryHandle));
            queryHandle = 0;
            apiCallHandle = 0;
        }
        if (DEBUG) System.out.println("[ZB UGCResponsePending] worker finished");
    }

    /**
     * Returns the list of item tables when ready. Same list every time. No release needed.
     * Building is done on this (main/Lua) thread to avoid cross-thread Kahlua access.
     *
     * @return ArrayList of KahluaTable item entries when ready, or null if still pending.
     */
    public ArrayList<KahluaTable> poll() {
        long h = handleReadyToBuild.getAndSet(0);
        if (h != 0) {
            if (DEBUG) System.out.println("[ZB UGCResponsePending] poll() handle ready, building h=" + h + " thread=" + dbgThread());
            cachedList = buildResponseTable(h);
            if (DEBUG) System.out.println("[ZB UGCResponsePending] poll() buildResponseTable done");
        }
        return cachedList;
    }

    /** Builds the response list for the given query handle; call after IsAPICallCompleted. Caller must not release handle. */
    static ArrayList<KahluaTable> buildResponseTable(long queryHandle) {
        if (DEBUG) System.out.println("[ZB UGCResponsePending] buildResponseTable() entry queryHandle=" + queryHandle + " thread=" + dbgThread());
        ArrayList<KahluaTable> list = new ArrayList<>();
        if (DEBUG) System.out.println("[ZB UGCResponsePending] buildResponseTable() creating UGCResponse");
        UGCResponse resp = new UGCResponse(queryHandle, RESULTS_PER_PAGE);
        byte[] detailsBuf = new byte[DETAILS_BUFFER_SIZE];
        Long handleObj = Long.valueOf(queryHandle);
        for (int i = 0; i < RESULTS_PER_PAGE; i++) {
            if (DEBUG && i <= 4) System.out.println("[ZB UGCResponsePending] buildResponseTable() loop start i=" + i);
            if (DEBUG && i <= 4) System.out.println("[ZB UGCResponsePending] buildResponseTable() GetQueryUGCResult call i=" + i);
            boolean gotResult = SteamUGC.GetQueryUGCResult(handleObj, i, detailsBuf);
            if (DEBUG && i <= 4) System.out.println("[ZB UGCResponsePending] buildResponseTable() GetQueryUGCResult returned i=" + i + " ok=" + gotResult);
            if (!gotResult) break;
            long id = readU64LE(detailsBuf, 0);
            String modId = id != 0 ? Long.toString(id) : "";
            if (DEBUG && i <= 4) System.out.println("[ZB UGCResponsePending] buildResponseTable() GetQueryUGCPreviewURL i=" + i);
            String previewURL = resp.GetQueryUGCPreviewURL(i);
            if (DEBUG && i <= 4) System.out.println("[ZB UGCResponsePending] buildResponseTable() GetQueryUGCMetadata i=" + i);
            String metadata = resp.GetQueryUGCMetadata(i);
            int numTags = resp.GetQueryUGCNumTags(i);

            if (DEBUG && i <= 4) System.out.println("[ZB UGCResponsePending] buildResponseTable() newTable item i=" + i + " numTags=" + numTags);
            KahluaTable item = LuaManager.platform.newTable();
            item.rawset("modId", modId);
            if (previewURL != null && !previewURL.isEmpty()) item.rawset("previewURL", previewURL);
            if (metadata != null && !metadata.isEmpty()) item.rawset("metadata", metadata);
            if (numTags > 0) {
                if (DEBUG && i <= 4) System.out.println("[ZB UGCResponsePending] buildResponseTable() creating tags table i=" + i);
                KahluaTable tags = LuaManager.platform.newTable();
                int tagIdx = 0;
                for (int j = 0; j < numTags; j++) {
                    String tag = resp.GetQueryUGCTag(i, j);
                    if (tag != null && !tag.isEmpty()) tags.rawset(Double.valueOf(++tagIdx), tag);
                }
                if (tagIdx > 0) item.rawset("tags", tags);
                if (DEBUG && i <= 4) System.out.println("[ZB UGCResponsePending] buildResponseTable() tags done i=" + i);
            }
            list.add(item);
            if (DEBUG && i <= 4) System.out.println("[ZB UGCResponsePending] buildResponseTable() list.add done i=" + i);
        }
        if (DEBUG) System.out.println("[ZB UGCResponsePending] buildResponseTable() Release resp, list.size=" + list.size());
        resp.Release();
        if (DEBUG) System.out.println("[ZB UGCResponsePending] buildResponseTable() done");
        return list;
    }

    private static long readU64LE(byte[] buf, int offset) {
        if (buf == null || offset + 8 > buf.length) return 0;
        long lo = (buf[offset] & 0xFF) | ((buf[offset + 1] & 0xFF) << 8) | ((buf[offset + 2] & 0xFF) << 16) | ((buf[offset + 3] & 0xFF) << 24);
        long hi = (buf[offset + 4] & 0xFF) | ((buf[offset + 5] & 0xFF) << 8) | ((buf[offset + 6] & 0xFF) << 16) | ((buf[offset + 7] & 0xFF) << 24);
        return (hi << 32) | (lo & 0xFFFFFFFFL);
    }
}
