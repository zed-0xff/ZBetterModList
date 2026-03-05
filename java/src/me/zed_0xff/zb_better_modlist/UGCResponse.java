package me.zed_0xff.zb_better_modlist;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.zed_0xff.zombie_buddy.Exposer;

/**
 * Lua-visible object returned from {@link UGCRequest#Send()} / {@link UGCResponsePending#Poll()}.
 * Encapsulates the query handle and result count; all binary/Steam data is decoded on the Java side.
 * Call {@link #Release()} when done.
 */
@Exposer.LuaClass
public final class UGCResponse {

    private static final int URL_BUFFER_SIZE = 512;
    private static final int STRING_BUFFER_SIZE = 256;
    private static final int MAX_CHILDREN = 64;
    private static final int MAX_CONTENT_DESCRIPTORS = 32;

    private long handle;
    private final int resultCount;

    UGCResponse(long handle, int resultCount) {
        this.handle = handle;
        this.resultCount = resultCount;
    }

    /**
     * Number of UGC results in this response. Indices for getters are 0-based and must be &lt; resultCount.
     */
    public int GetResultCount() {
        return resultCount;
    }

    /**
     * Preview image URL for the result at index (0-based). Empty string on failure or invalid index.
     */
    public String GetQueryUGCPreviewURL(int index) {
        if (handle == 0 || index < 0 || index >= resultCount) return "";
        byte[] buf = new byte[URL_BUFFER_SIZE];
        if (!SteamUGC.GetQueryUGCPreviewURL(Long.valueOf(handle), index, buf)) return "";
        return bytesToString(buf);
    }

    /**
     * Number of tags for the result at index (0-based). 0 on failure or invalid index.
     */
    public int GetQueryUGCNumTags(int index) {
        if (handle == 0 || index < 0 || index >= resultCount) return 0;
        return SteamUGC.GetQueryUGCNumTags(Long.valueOf(handle), index);
    }

    /**
     * Tag value string at (index, indexTag). Empty string on failure.
     */
    public String GetQueryUGCTag(int index, int indexTag) {
        if (handle == 0 || index < 0 || index >= resultCount) return "";
        byte[] buf = new byte[STRING_BUFFER_SIZE];
        if (!SteamUGC.GetQueryUGCTag(Long.valueOf(handle), index, indexTag, buf)) return "";
        return bytesToString(buf);
    }

    /**
     * Tag display name at (index, indexTag). Empty string on failure.
     */
    public String GetQueryUGCTagDisplayName(int index, int indexTag) {
        if (handle == 0 || index < 0 || index >= resultCount) return "";
        byte[] buf = new byte[STRING_BUFFER_SIZE];
        if (!SteamUGC.GetQueryUGCTagDisplayName(Long.valueOf(handle), index, indexTag, buf)) return "";
        return bytesToString(buf);
    }

    /**
     * Metadata string for the result at index. Empty string on failure.
     */
    public String GetQueryUGCMetadata(int index) {
        if (handle == 0 || index < 0 || index >= resultCount) return "";
        byte[] buf = new byte[URL_BUFFER_SIZE];
        if (!SteamUGC.GetQueryUGCMetadata(Long.valueOf(handle), index, buf)) return "";
        return bytesToString(buf);
    }

    /**
     * Number of additional previews for the result at index. 0 on failure.
     */
    public int GetQueryUGCNumAdditionalPreviews(int index) {
        if (handle == 0 || index < 0 || index >= resultCount) return 0;
        return SteamUGC.GetQueryUGCNumAdditionalPreviews(Long.valueOf(handle), index);
    }

    /**
     * Additional preview URL/video ID at (index, previewIndex). Empty string on failure.
     */
    public String GetQueryUGCAdditionalPreviewURL(int index, int previewIndex) {
        if (handle == 0 || index < 0 || index >= resultCount) return "";
        byte[] url = new byte[URL_BUFFER_SIZE];
        byte[] fileName = new byte[STRING_BUFFER_SIZE];
        int[] pType = new int[1];
        if (!SteamUGC.GetQueryUGCAdditionalPreview(Long.valueOf(handle), index, previewIndex, url, fileName, pType)) return "";
        return bytesToString(url);
    }

    /**
     * Additional preview original file name at (index, previewIndex). Empty string on failure.
     */
    public String GetQueryUGCAdditionalPreviewOriginalFileName(int index, int previewIndex) {
        if (handle == 0 || index < 0 || index >= resultCount) return "";
        byte[] url = new byte[URL_BUFFER_SIZE];
        byte[] fileName = new byte[STRING_BUFFER_SIZE];
        int[] pType = new int[1];
        if (!SteamUGC.GetQueryUGCAdditionalPreview(Long.valueOf(handle), index, previewIndex, url, fileName, pType)) return "";
        return bytesToString(fileName);
    }

    /**
     * Additional preview type (EItemPreviewType) at (index, previewIndex). 0 on failure.
     */
    public int GetQueryUGCAdditionalPreviewType(int index, int previewIndex) {
        if (handle == 0 || index < 0 || index >= resultCount) return 0;
        byte[] url = new byte[URL_BUFFER_SIZE];
        byte[] fileName = new byte[STRING_BUFFER_SIZE];
        int[] pType = new int[1];
        if (!SteamUGC.GetQueryUGCAdditionalPreview(Long.valueOf(handle), index, previewIndex, url, fileName, pType)) return 0;
        return pType[0];
    }

    /**
     * Child published file IDs for the result at index. Returns list of ID strings (may be empty).
     */
    public List<String> GetQueryUGCChildren(int index) {
        if (handle == 0 || index < 0 || index >= resultCount) return Collections.emptyList();
        long[] ids = new long[MAX_CHILDREN];
        if (!SteamUGC.GetQueryUGCChildren(Long.valueOf(handle), index, ids)) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (long id : ids) {
            if (id != 0) out.add(Long.toString(id));
        }
        return out;
    }

    /**
     * Number of key-value tags for the result at index. 0 on failure.
     */
    public int GetQueryUGCNumKeyValueTags(int index) {
        if (handle == 0 || index < 0 || index >= resultCount) return 0;
        return SteamUGC.GetQueryUGCNumKeyValueTags(Long.valueOf(handle), index);
    }

    /**
     * Key of the key-value tag at (index, keyValueTagIndex). Empty string on failure.
     */
    public String GetQueryUGCKeyValueTagKey(int index, int keyValueTagIndex) {
        if (handle == 0 || index < 0 || index >= resultCount) return "";
        byte[] key = new byte[STRING_BUFFER_SIZE];
        byte[] val = new byte[STRING_BUFFER_SIZE];
        if (!SteamUGC.GetQueryUGCKeyValueTag(Long.valueOf(handle), index, keyValueTagIndex, key, val)) return "";
        return bytesToString(key);
    }

    /**
     * Value of the key-value tag at (index, keyValueTagIndex). Empty string on failure.
     */
    public String GetQueryUGCKeyValueTagValue(int index, int keyValueTagIndex) {
        if (handle == 0 || index < 0 || index >= resultCount) return "";
        byte[] key = new byte[STRING_BUFFER_SIZE];
        byte[] val = new byte[STRING_BUFFER_SIZE];
        if (!SteamUGC.GetQueryUGCKeyValueTag(Long.valueOf(handle), index, keyValueTagIndex, key, val)) return "";
        return bytesToString(val);
    }

    /**
     * Content descriptor IDs (EUGCContentDescriptorID) for the result at index. Returns list of ints (may be empty).
     */
    public List<Integer> GetQueryUGCContentDescriptors(int index) {
        if (handle == 0 || index < 0 || index >= resultCount) return Collections.emptyList();
        int[] arr = new int[MAX_CONTENT_DESCRIPTORS];
        int n = SteamUGC.GetQueryUGCContentDescriptors(Long.valueOf(handle), index, arr);
        if (n <= 0) return Collections.emptyList();
        List<Integer> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(arr[i]);
        return out;
    }

    /**
     * Statistic value (eStatType: EItemStatistic) for the result at index. Returns value as string to avoid 64-bit precision loss in Lua. Empty string on failure.
     */
    public String GetQueryUGCStatistic(int index, int eStatType) {
        if (handle == 0 || index < 0 || index >= resultCount) return "";
        long[] val = new long[1];
        if (!SteamUGC.GetQueryUGCStatistic(Long.valueOf(handle), index, eStatType, val)) return "";
        return Long.toString(val[0]);
    }

    /**
     * Releases the query handle. Call once when done with this response. No-op if already released.
     */
    public void Release() {
        if (handle != 0) {
            SteamUGC.ReleaseQueryUGCRequest(Long.valueOf(handle));
            handle = 0;
        }
    }

    private static String bytesToString(byte[] b) {
        int len = nullTerminatedLength(b);
        return len <= 0 ? "" : new String(b, 0, len, StandardCharsets.UTF_8).trim();
    }

    private static int nullTerminatedLength(byte[] b) {
        for (int i = 0; i < b.length; i++) {
            if (b[i] == 0) return i;
        }
        return b.length;
    }
}
