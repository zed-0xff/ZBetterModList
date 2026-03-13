package me.zed_0xff.zb_better_modlist;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import me.zed_0xff.zombie_buddy.Exposer;
import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.LuaCallFrame;
import zombie.Lua.LuaManager;

/**
 * Lua-visible object returned from {@link UGCRequest#sendAsync()} while the query is in flight.
 * Call {@link #poll()} each frame; when the worker sees completion it returns an ArrayList of
 * item tables (each with id, subscribed, subscribe(), and optionally previewURL, metadata, tags). No release needed.
 * Response tables are built on the calling (main/Lua) thread to avoid cross-thread Kahlua access.
 */
@Exposer.LuaClass
public final class UGCResponsePending {

    private static final long POLL_MS         = 50;
    private static final long TIMEOUT_MS      = 15_000L;
    private static final int RESULTS_PER_PAGE = 50;
    private static final int DETAILS_BUF_SIZE = 256*1024;

    private volatile long queryHandle;
    private volatile long apiCallHandle;
    /** When worker sees completion, sets this to the handle; poll() builds on main thread. */
    private final AtomicLong handleReadyToBuild = new AtomicLong(0);
    private volatile ArrayList<KahluaTable> cachedList;

    UGCResponsePending(long queryHandle, long apiCallHandle) {
        this.queryHandle = queryHandle;
        this.apiCallHandle = apiCallHandle;
        Thread t = new Thread(this::runWorker, "UGCResponsePending-worker");
        t.setDaemon(true);
        t.start();
    }

    private void runWorker() {
        try {
            long deadline = System.currentTimeMillis() + TIMEOUT_MS;
            while (queryHandle != 0 && apiCallHandle != 0 && System.currentTimeMillis() < deadline) {
                boolean completed = SteamUtils.IsAPICallCompleted(Long.valueOf(apiCallHandle));
                if (completed) {
                    handleReadyToBuild.set(queryHandle);
                    queryHandle = 0;
                    apiCallHandle = 0;
                    break;
                }
                Thread.sleep(POLL_MS);
            }
            if (queryHandle != 0) {
                SteamUGC.ReleaseQueryUGCRequest(Long.valueOf(queryHandle));
                queryHandle = 0;
                apiCallHandle = 0;
            }
        } catch (Throwable t) {
            if (queryHandle != 0) SteamUGC.ReleaseQueryUGCRequest(Long.valueOf(queryHandle));
            queryHandle = 0;
            apiCallHandle = 0;
        }
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
            cachedList = buildResponseTable(h);
        }
        return cachedList;
    }

    /** Builds the response list for the given query handle; call after IsAPICallCompleted. Caller must not release handle. */
    static ArrayList<KahluaTable> buildResponseTable(long queryHandle) {
        ArrayList<KahluaTable> list = new ArrayList<>();
        UGCResponse resp = new UGCResponse(queryHandle, RESULTS_PER_PAGE);
        Long handleObj = Long.valueOf(queryHandle);
        for (int i = 0; i < RESULTS_PER_PAGE; i++) {
            byte[] detailsBuf = new byte[DETAILS_BUF_SIZE];
            boolean gotResult = SteamUGC.GetQueryUGCResult(handleObj, i, detailsBuf);
            if (!gotResult) break;
            SteamUGC.DecodedUGCDetails details = SteamUGC.DecodeUGCDetails(detailsBuf);
            long id = details != null ? details.publishedFileId : 0;
            String modId = id != 0 ? Long.toString(id) : "";
            String previewURL = resp.GetQueryUGCPreviewURL(i);
            String metadata = resp.GetQueryUGCMetadata(i);
            String tagsStr = details != null ? details.tagsCsv : "";

            KahluaTable item = LuaManager.platform.newTable();
            item.rawset("id", modId);
            String title = details != null ? details.title : "";
            if (title != null && !title.isEmpty()) item.rawset("title", title);
            String description = details != null ? details.description : "";
            if (description != null && !description.isEmpty()) item.rawset("description", description);
            String url = details != null ? details.url : "";
            if (url != null && !url.isEmpty()) item.rawset("url", url);
            String fileName = details != null ? details.fileName : "";
            if (fileName != null && !fileName.isEmpty()) item.rawset("fileName", fileName);
            if (details != null && details.fileSize != 0) item.rawset("fileSize", Double.valueOf(details.fileSize));
            if (details != null && details.votesUp != 0) item.rawset("votesUp", Double.valueOf(details.votesUp));
            if (details != null && details.votesDown != 0) item.rawset("votesDown", Double.valueOf(details.votesDown));
            if (details != null && details.score != 0) item.rawset("score", Double.valueOf(details.score));
            if (details != null && details.numChildren != 0) item.rawset("numChildren", Double.valueOf(details.numChildren));
            if (previewURL != null && !previewURL.isEmpty()) item.rawset("previewURL", previewURL);
            if (metadata != null && !metadata.isEmpty()) item.rawset("metadata", metadata);
            if (tagsStr != null && !tagsStr.isEmpty()) {
                String[] parts = tagsStr.split(",");
                KahluaTable tags = LuaManager.platform.newTable();
                int tagIdx = 0;
                for (String p : parts) {
                    if (p == null) continue;
                    String tag = p.trim();
                    if (!tag.isEmpty()) tags.rawset(Double.valueOf(++tagIdx), tag);
                }
                if (tagIdx > 0) item.rawset("tags", tags);
            }
            if (id != 0) {
                int itemState = SteamUGC.GetItemState(id);
                item.rawset("subscribed", Boolean.valueOf((itemState & 1) != 0));
                final String modIdForSubscribe = modId;
                item.rawset("subscribe", (JavaFunction) (LuaCallFrame frame, int nArgs) -> {
                    try {
                        SteamUGC.SubscribeItem(Long.parseLong(modIdForSubscribe));
                    } catch (NumberFormatException ignored) {}
                    return 0;
                });
            }
            list.add(item);
        }
        resp.Release();
        return list;
    }
}
