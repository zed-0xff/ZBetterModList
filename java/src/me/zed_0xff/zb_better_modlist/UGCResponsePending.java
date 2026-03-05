package me.zed_0xff.zb_better_modlist;

import me.zed_0xff.zombie_buddy.Exposer;

/**
 * Lua-visible object returned from {@link UGCRequest#Send()} while the query is in flight.
 * Call {@link #Poll()} each frame; when the worker sees completion it returns an
 * {@link UGCResponse}. GetAPICallResult and GetQueryUGCResult hang/crash in this process,
 * so we only use IsAPICallCompleted and assume {@value #RESULTS_PER_PAGE} results per page.
 * Iterate 0..GetResultCount()-1; indices beyond the real count may return empty from getters.
 */
@Exposer.LuaClass
public final class UGCResponsePending {

    private static final long POLL_MS = 50;
    private static final long TIMEOUT_MS = 15_000L;
    /** Max results per page (kNumUGCResultsPerPage). We cannot call GetAPICallResult or GetQueryUGCResult (they hang/crash), so assume full page when completed. */
    private static final int RESULTS_PER_PAGE = 50;

    private volatile long queryHandle;
    private volatile long apiCallHandle;
    private static final boolean DEBUG = true;

    private volatile long resultHandle = 0;
    private volatile int resultCount = -1;
    private volatile UGCResponse cachedResponse;

    UGCResponsePending(long queryHandle, long apiCallHandle) {
        this.queryHandle = queryHandle;
        this.apiCallHandle = apiCallHandle;
        if (DEBUG) System.out.println("[ZB UGCResponsePending] created queryHandle=" + queryHandle + " apiCallHandle=" + apiCallHandle);
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
                    if (DEBUG) System.out.println("[ZB UGCResponsePending] completed, assuming " + RESULTS_PER_PAGE + " results (GetAPICallResult/GetQueryUGCResult hang)");
                    resultHandle = queryHandle;
                    resultCount = RESULTS_PER_PAGE;
                    queryHandle = 0;
                    apiCallHandle = 0;
                    break;
                }
                Thread.sleep(POLL_MS);
            }
            if (queryHandle != 0) {
                if (DEBUG) System.out.println("[ZB UGCResponsePending] timeout or exit, releasing handle (polls=" + polls + ")");
                SteamUGC.ReleaseQueryUGCRequest(Long.valueOf(queryHandle));
                resultHandle = 0;
                resultCount = 0;
                queryHandle = 0;
                apiCallHandle = 0;
            }
        } catch (Throwable t) {
            System.out.println("[ZB UGCResponsePending] worker throw: " + t);
            t.printStackTrace();
            if (queryHandle != 0) SteamUGC.ReleaseQueryUGCRequest(Long.valueOf(queryHandle));
            resultHandle = 0;
            resultCount = 0;
            queryHandle = 0;
            apiCallHandle = 0;
        }
        if (DEBUG) System.out.println("[ZB UGCResponsePending] worker finished resultCount=" + resultCount);
    }

    /**
     * Check if the worker has produced a result. No Steam API is called on the main thread.
     * Returns the same UGCResponse instance every time (not consumed); call {@link UGCResponse#Release()} when done.
     *
     * @return UGCResponse when ready, or null if still pending.
     */
    public UGCResponse Poll() {
        if (resultCount < 0) return null;
        if (cachedResponse == null) cachedResponse = new UGCResponse(resultHandle, resultCount);
        return cachedResponse;
    }
}
