package me.zed_0xff.zb_better_modlist;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;
import java.nio.charset.StandardCharsets;
import me.zed_0xff.zombie_buddy.Exposer;

/**
 * Pure mirror of Steamworks ISteamUGC API. JNA bindings only; method names and behaviour
 * match the C#/C API. For Lua-facing helpers (e.g. decoded UGC query result), use
 * {@link SteamLuaHelper}.
 */
@Exposer.LuaClass
public final class SteamUGC {

    private static final String LIBRARY = "steam_api";
    private static final int TITLE_LEN = 129;
    private static final int DESC_LEN = 8000;
    private static final int TAGS_LEN = 1025;
    private static final int FILENAME_LEN = 260;
    private static final int URL_LEN = 256;

    public interface SteamAPI extends Library {
        SteamAPI INSTANCE = load();
        static SteamAPI load() {
            try { return Native.load(LIBRARY, SteamAPI.class); } catch (Throwable t) { return null; }
        }
        int SteamAPI_GetHSteamUser();
        int SteamAPI_GetHSteamPipe();
        Pointer SteamAPI_SteamUGC_v021(int hSteamUser, int hSteamPipe);
        long SteamAPI_ISteamUGC_UnsubscribeItem(Pointer self, long publishedFileId);
        long SteamAPI_ISteamUGC_CreateQueryAllUGCRequestPage(Pointer self, int eQueryType, int eMatchingUGCType, int nCreatorAppID, int nConsumerAppID, int unPage);
        int SteamAPI_ISteamUGC_GetNumSubscribedItems(Pointer self, boolean bIncludeLocallyDisabled);
        int SteamAPI_ISteamUGC_GetSubscribedItems(Pointer self, long[] pvecPublishedFileID, int cMaxEntries, boolean bIncludeLocallyDisabled);
        boolean SteamAPI_ISteamUGC_SetSearchText(Pointer self, long handle, String searchText);
        boolean SteamAPI_ISteamUGC_SetReturnAdditionalPreviews(Pointer self, long handle, boolean value);
        boolean SteamAPI_ISteamUGC_SetReturnChildren(Pointer self, long handle, boolean value);
        boolean SteamAPI_ISteamUGC_SetReturnKeyValueTags(Pointer self, long handle, boolean value);
        boolean SteamAPI_ISteamUGC_SetReturnLongDescription(Pointer self, long handle, boolean value);
        boolean SteamAPI_ISteamUGC_SetReturnMetadata(Pointer self, long handle, boolean value);
        boolean SteamAPI_ISteamUGC_SetReturnOnlyIDs(Pointer self, long handle, boolean value);
        boolean SteamAPI_ISteamUGC_SetReturnTotalOnly(Pointer self, long handle, boolean value);
        long SteamAPI_ISteamUGC_SendQueryUGCRequest(Pointer self, long handle);
        boolean SteamAPI_ISteamUGC_GetQueryUGCResult(Pointer self, long handle, int index, Pointer pDetails);
        boolean SteamAPI_ISteamUGC_ReleaseQueryUGCRequest(Pointer self, long handle);
        int SteamAPI_ISteamUGC_GetQueryUGCNumTags(Pointer self, long handle, int index);
        boolean SteamAPI_ISteamUGC_GetQueryUGCPreviewURL(Pointer self, long handle, int index, byte[] pchURL, int cchURLSize);
        // Additional ISteamUGC methods
        long SteamAPI_ISteamUGC_GetAppDependencies(Pointer self, long nPublishedFileID);
        boolean SteamAPI_ISteamUGC_GetItemDownloadInfo(Pointer self, long nPublishedFileID, long[] punBytesDownloaded, long[] punBytesTotal);
        boolean SteamAPI_ISteamUGC_GetItemInstallInfo(Pointer self, long nPublishedFileID, long[] punSizeOnDisk, byte[] pchFolder, int cchFolderSize, int[] punTimeStamp);
        int SteamAPI_ISteamUGC_GetItemState(Pointer self, long nPublishedFileID);
        int SteamAPI_ISteamUGC_GetItemUpdateProgress(Pointer self, long handle, long[] punBytesProcessed, long[] punBytesTotal);
        long SteamAPI_ISteamUGC_GetUserItemVote(Pointer self, long nPublishedFileID);
        boolean SteamAPI_ISteamUGC_GetQueryUGCAdditionalPreview(Pointer self, long handle, int index, int previewIndex, byte[] pchURLOrVideoID, int cchURLSize, byte[] pchOriginalFileName, int cchOriginalFileNameSize, int[] pPreviewType);
        boolean SteamAPI_ISteamUGC_GetQueryUGCChildren(Pointer self, long handle, int index, long[] pvecPublishedFileID, int cMaxEntries);
        boolean SteamAPI_ISteamUGC_GetQueryUGCTag(Pointer self, long handle, int index, int indexTag, byte[] pchValue, int cchValueSize);
        boolean SteamAPI_ISteamUGC_GetQueryUGCTagDisplayName(Pointer self, long handle, int index, int indexTag, byte[] pchValue, int cchValueSize);
        boolean SteamAPI_ISteamUGC_GetQueryUGCKeyValueTag(Pointer self, long handle, int index, int keyValueTagIndex, byte[] pchKey, int cchKeySize, byte[] pchValue, int cchValueSize);
        int SteamAPI_ISteamUGC_GetQueryUGCContentDescriptors(Pointer self, long handle, int index, int[] pvecDescriptors, int cMaxEntries);
        boolean SteamAPI_ISteamUGC_GetQueryUGCMetadata(Pointer self, long handle, int index, byte[] pchMetadata, int cchMetadatasize);
        int SteamAPI_ISteamUGC_GetQueryUGCNumAdditionalPreviews(Pointer self, long handle, int index);
        int SteamAPI_ISteamUGC_GetQueryUGCNumKeyValueTags(Pointer self, long handle, int index);
        boolean SteamAPI_ISteamUGC_GetQueryUGCStatistic(Pointer self, long handle, int index, int eStatType, long[] pStatValue);
        boolean SteamAPI_ISteamUGC_SetMatchAnyTag(Pointer self, long handle, boolean bMatchAnyTag);
        boolean SteamAPI_ISteamUGC_SetRankedByTrendDays(Pointer self, long handle, int unDays);
        boolean SteamAPI_ISteamUGC_SetReturnPlaytimeStats(Pointer self, long handle, int unDays);
        boolean SteamAPI_ISteamUGC_SetAllowCachedResponse(Pointer self, long handle, int unMaxAgeSeconds);
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

    private static long toHandle(Number n) { return n != null ? n.longValue() : 0; }

    public static final class DecodedUGCDetails {
        public long publishedFileId;
        public int eResult;
        public int eFileType;
        public int creatorAppId;
        public int consumerAppId;
        public String title;
        public String description;
        public long steamIdOwner;
        public long timeCreated;
        public long timeUpdated;
        public long timeAddedToUserList;
        public int visibility;
        public boolean banned;
        public boolean acceptedForUse;
        public boolean tagsTruncated;
        public String tagsCsv;
        public long fileHandle;
        public long previewFileHandle;
        public String fileName;
        public int fileSize;
        public int previewFileSize;
        public String url;
        public long votesUp;
        public long votesDown;
        public float score;
        public long numChildren;
        public int assumedPack;
    }

    /**
     * Decode a raw SteamUGCDetails_t byte buffer. Steamworks pack differs across builds; we try
     * both 4 and 8 and pick the one that yields a sane URL/score/votes.
     */
    public static DecodedUGCDetails DecodeUGCDetails(byte[] raw) {
        DecodedUGCDetails d4 = decodeUGCDetails(raw, 4);
        DecodedUGCDetails d8 = decodeUGCDetails(raw, 8);
        return pickBetter(d4, d8);
    }

    private static DecodedUGCDetails pickBetter(DecodedUGCDetails a, DecodedUGCDetails b) {
        if (a == null) return b;
        if (b == null) return a;

        boolean aUrl = looksLikeUrl(a.url);
        boolean bUrl = looksLikeUrl(b.url);
        if (aUrl && !bUrl) return a;
        if (bUrl && !aUrl) return b;

        boolean aScore = looksReasonableScore(a.score);
        boolean bScore = looksReasonableScore(b.score);
        if (aScore && !bScore) return a;
        if (bScore && !aScore) return b;

        boolean aVotes = looksReasonableVotes(a.votesUp, a.votesDown);
        boolean bVotes = looksReasonableVotes(b.votesUp, b.votesDown);
        if (aVotes && !bVotes) return a;
        if (bVotes && !aVotes) return b;

        // Prefer pack=4 by default (common in Steamworks structs).
        return a.assumedPack == 4 ? a : b;
    }

    private static boolean looksLikeUrl(String s) {
        if (s == null) return false;
        return s.startsWith("https://") || s.startsWith("http://");
    }

    private static boolean looksReasonableScore(float f) {
        return !Float.isNaN(f) && f >= 0.0f && f <= 1.0f;
    }

    private static boolean looksReasonableVotes(long up, long down) {
        if (up < 0 || down < 0) return false;
        // Workshop items rarely have > 10M votes; reject absurd values.
        return up <= 10_000_000L && down <= 10_000_000L;
    }

    private static int align(int off, int pack) {
        int m = pack - 1;
        return (off + m) & ~m;
    }

    private static long readU32LE(byte[] buf, int offset) {
        if (buf == null || offset + 4 > buf.length) return 0;
        int v = (buf[offset] & 0xFF)
                | ((buf[offset + 1] & 0xFF) << 8)
                | ((buf[offset + 2] & 0xFF) << 16)
                | ((buf[offset + 3] & 0xFF) << 24);
        return Integer.toUnsignedLong(v);
    }

    private static int readI32LE(byte[] buf, int offset) {
        if (buf == null || offset + 4 > buf.length) return 0;
        return (buf[offset] & 0xFF)
                | ((buf[offset + 1] & 0xFF) << 8)
                | ((buf[offset + 2] & 0xFF) << 16)
                | ((buf[offset + 3] & 0xFF) << 24);
    }

    private static long readU64LE(byte[] buf, int offset) {
        if (buf == null || offset + 8 > buf.length) return 0;
        long lo = (buf[offset] & 0xFFL)
                | ((buf[offset + 1] & 0xFFL) << 8)
                | ((buf[offset + 2] & 0xFFL) << 16)
                | ((buf[offset + 3] & 0xFFL) << 24);
        long hi = (buf[offset + 4] & 0xFFL)
                | ((buf[offset + 5] & 0xFFL) << 8)
                | ((buf[offset + 6] & 0xFFL) << 16)
                | ((buf[offset + 7] & 0xFFL) << 24);
        return (hi << 32) | (lo & 0xFFFFFFFFL);
    }

    private static float readF32LE(byte[] buf, int offset) {
        return Float.intBitsToFloat(readI32LE(buf, offset));
    }

    private static String readCString(byte[] buf, int offset, int maxLen) {
        if (buf == null || maxLen <= 0 || offset < 0 || offset >= buf.length) return "";
        int end = Math.min(buf.length, offset + maxLen);
        int len = 0;
        while (offset + len < end && buf[offset + len] != 0) len++;
        if (len <= 0) return "";
        return new String(buf, offset, len, StandardCharsets.UTF_8).trim();
    }

    private static DecodedUGCDetails decodeUGCDetails(byte[] raw, int pack) {
        if (raw == null) return null;

        DecodedUGCDetails d = new DecodedUGCDetails();
        d.assumedPack = pack;

        d.publishedFileId = readU64LE(raw, 0);
        d.eResult = readI32LE(raw, 8);
        d.eFileType = readI32LE(raw, 12);
        d.creatorAppId = (int) readU32LE(raw, 16);
        d.consumerAppId = (int) readU32LE(raw, 20);

        int offTitle = 24;
        int offDesc = offTitle + TITLE_LEN;
        d.title = readCString(raw, offTitle, TITLE_LEN);
        d.description = readCString(raw, offDesc, DESC_LEN);

        int offOwner = align(offDesc + DESC_LEN, pack);
        d.steamIdOwner = readU64LE(raw, offOwner);

        int offTimes = offOwner + 8;
        d.timeCreated = readU32LE(raw, offTimes);
        d.timeUpdated = readU32LE(raw, offTimes + 4);
        d.timeAddedToUserList = readU32LE(raw, offTimes + 8);
        d.visibility = readI32LE(raw, offTimes + 12);

        int offBools = offTimes + 16;
        d.banned = offBools < raw.length && raw[offBools] != 0;
        d.acceptedForUse = offBools + 1 < raw.length && raw[offBools + 1] != 0;
        d.tagsTruncated = offBools + 2 < raw.length && raw[offBools + 2] != 0;

        int offTags = offBools + 3;
        d.tagsCsv = readCString(raw, offTags, TAGS_LEN);

        int offFile = align(offTags + TAGS_LEN, pack);
        d.fileHandle = readU64LE(raw, offFile);
        d.previewFileHandle = readU64LE(raw, offFile + 8);

        int offFileName = offFile + 16;
        d.fileName = readCString(raw, offFileName, FILENAME_LEN);

        int offSizes = offFileName + FILENAME_LEN;
        d.fileSize = readI32LE(raw, offSizes);
        d.previewFileSize = readI32LE(raw, offSizes + 4);

        int offUrl = offSizes + 8;
        d.url = readCString(raw, offUrl, URL_LEN);

        int offVotes = offUrl + URL_LEN;
        d.votesUp = readU32LE(raw, offVotes);
        d.votesDown = readU32LE(raw, offVotes + 4);
        d.score = readF32LE(raw, offVotes + 8);
        d.numChildren = readU32LE(raw, offVotes + 12);

        return d;
    }

    @FieldOrder({
            "m_nPublishedFileId",
            "m_eResult",
            "m_eFileType",
            "m_nCreatorAppID",
            "m_nConsumerAppID",
            "m_rgchTitle",
            "m_rgchDescription",
            "m_ulSteamIDOwner",
            "m_rtimeCreated",
            "m_rtimeUpdated",
            "m_rtimeAddedToUserList",
            "m_eVisibility",
            "m_bBanned",
            "m_bAcceptedForUse",
            "m_bTagsTruncated",
            "m_rgchTags",
            "m_hFile",
            "m_hPreviewFile",
            "m_pchFileName",
            "m_nFileSize",
            "m_nPreviewFileSize",
            "m_rgchURL",
            "m_unVotesUp",
            "m_unVotesDown",
            "m_flScore",
            "m_unNumChildren"
    })
    public static final class SteamUGCDetails extends Structure {
        public long m_nPublishedFileId;            // PublishedFileId_t (uint64)
        public int m_eResult;                      // EResult (int32)
        public int m_eFileType;                    // EWorkshopFileType (int32)
        public int m_nCreatorAppID;                // AppId_t (uint32)
        public int m_nConsumerAppID;               // AppId_t (uint32)
        public byte[] m_rgchTitle = new byte[129]; // char[129]
        public byte[] m_rgchDescription = new byte[8000]; // char[8000]
        public long m_ulSteamIDOwner;              // uint64
        public int m_rtimeCreated;                 // uint32
        public int m_rtimeUpdated;                 // uint32
        public int m_rtimeAddedToUserList;         // uint32
        public int m_eVisibility;                  // ERemoteStoragePublishedFileVisibility (int32)
        public byte m_bBanned;                     // bool (1 byte)
        public byte m_bAcceptedForUse;             // bool (1 byte)
        public byte m_bTagsTruncated;              // bool (1 byte)
        public byte[] m_rgchTags = new byte[1025]; // char[1025]
        public long m_hFile;                       // UGCHandle_t (uint64)
        public long m_hPreviewFile;                // UGCHandle_t (uint64)
        public byte[] m_pchFileName = new byte[260]; // char[260]
        public int m_nFileSize;                    // int32
        public int m_nPreviewFileSize;             // int32
        public byte[] m_rgchURL = new byte[256];   // char[256]
        public int m_unVotesUp;                    // uint32
        public int m_unVotesDown;                  // uint32
        public float m_flScore;                    // float
        public int m_unNumChildren;                // uint32

        public String title() { return bytesToString(m_rgchTitle); }
        public String description() { return bytesToString(m_rgchDescription); }
        public String tags() { return bytesToString(m_rgchTags); }
        public String url() { return bytesToString(m_rgchURL); }
        public String fileName() { return bytesToString(m_pchFileName); }
        public boolean tagsTruncated() { return m_bTagsTruncated != 0; }
    }

    private static String bytesToString(byte[] b) {
        if (b == null || b.length == 0) return "";
        int len = 0;
        for (; len < b.length; len++) {
            if (b[len] == 0) break;
        }
        return len <= 0 ? "" : new String(b, 0, len, StandardCharsets.UTF_8).trim();
    }

    public static boolean UnsubscribeItem(long publishedFileId) {
        if (publishedFileId == 0) return false;
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        return SteamAPI.INSTANCE.SteamAPI_ISteamUGC_UnsubscribeItem(ugc, publishedFileId) != 0;
    }

    public static long CreateQueryAllUGCRequest(int queryType, int matchingType, int creatorAppId, int consumerAppId, int page) {
        Pointer ugc = getUGC();
        if (ugc == null) return 0;
        return SteamAPI.INSTANCE.SteamAPI_ISteamUGC_CreateQueryAllUGCRequestPage(ugc, queryType, matchingType, creatorAppId, consumerAppId, page);
    }

    public static boolean SetSearchText(Number handle, String searchText) {
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_SetSearchText(ugc, h, searchText != null ? searchText : "");
    }

    public static boolean SetReturnAdditionalPreviews(Number handle, boolean value) {
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_SetReturnAdditionalPreviews(ugc, h, value);
    }

    public static boolean SetReturnChildren(Number handle, boolean value) {
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_SetReturnChildren(ugc, h, value);
    }

    public static boolean SetReturnKeyValueTags(Number handle, boolean value) {
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_SetReturnKeyValueTags(ugc, h, value);
    }

    public static boolean SetReturnLongDescription(Number handle, boolean value) {
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_SetReturnLongDescription(ugc, h, value);
    }

    public static boolean SetReturnMetadata(Number handle, boolean value) {
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_SetReturnMetadata(ugc, h, value);
    }

    public static boolean SetReturnOnlyIDs(Number handle, boolean value) {
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_SetReturnOnlyIDs(ugc, h, value);
    }

    public static boolean SetReturnTotalOnly(Number handle, boolean value) {
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_SetReturnTotalOnly(ugc, h, value);
    }

    /**
     * Mirror of ISteamUGC::GetNumSubscribedItems.
     *
     * C++: uint32 GetNumSubscribedItems( bool bIncludeLocallyDisabled = false );
     */
    public static int GetNumSubscribedItems(boolean includeLocallyDisabled) {
        Pointer ugc = getUGC();
        if (ugc == null) return 0;
        return SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetNumSubscribedItems(ugc, includeLocallyDisabled);
    }

    /**
     * Mirror of ISteamUGC::GetSubscribedItems.
     *
     * C++: uint32 GetSubscribedItems( PublishedFileId_t *pvecPublishedFileID,
     *                                 uint32 cMaxEntries,
     *                                 bool bIncludeLocallyDisabled = false );
     *
     * Here {@code pvecPublishedFileID} is represented as a {@code long[]} of PublishedFileId_t
     * (uint64). The array length is used as {@code cMaxEntries}. Returns the number of entries
     * written to the array.
     */
    public static int GetSubscribedItems(long[] pvecPublishedFileID, boolean includeLocallyDisabled) {
        if (pvecPublishedFileID == null || pvecPublishedFileID.length == 0) return 0;
        Pointer ugc = getUGC();
        if (ugc == null) return 0;
        return SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetSubscribedItems(
                ugc,
                pvecPublishedFileID,
                pvecPublishedFileID.length,
                includeLocallyDisabled
        );
    }

    public static long SendQueryUGCRequest(Number handle) {
        Pointer ugc = getUGC();
        if (ugc == null) return 0;
        long h = toHandle(handle);
        return h != 0 ? SteamAPI.INSTANCE.SteamAPI_ISteamUGC_SendQueryUGCRequest(ugc, h) : 0;
    }

    public static boolean ReleaseQueryUGCRequest(Number handle) {
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_ReleaseQueryUGCRequest(ugc, h);
    }

    /**
     * Mirror of ISteamUGC::GetQueryUGCResult.
     *
     * C++: bool GetQueryUGCResult(UGCQueryHandle_t handle, uint32 index, SteamUGCDetails_t *pDetails);
     *
     * Here {@code pDetails} is represented as a raw byte buffer which receives the native
     * {@code SteamUGCDetails_t} struct contents. The caller is responsible for providing a buffer
     * large enough for the current SDK's struct size.
     */
    public static boolean GetQueryUGCResult(Number handle, int index, byte[] pDetails) {
        if (pDetails == null || pDetails.length == 0) return false;
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        if (h == 0) return false;
        Pointer mem = new Memory(pDetails.length);
        boolean ok = SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetQueryUGCResult(ugc, h, index, mem);
        if (ok) mem.read(0, pDetails, 0, pDetails.length);
        return ok;
    }

    /** Safer overload that fills a correctly-sized SteamUGCDetails struct. */
    public static boolean GetQueryUGCResult(Number handle, int index, SteamUGCDetails outDetails) {
        if (outDetails == null) return false;
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        if (h == 0) return false;
        boolean ok = SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetQueryUGCResult(ugc, h, index, outDetails.getPointer());
        if (ok) outDetails.read();
        return ok;
    }

    /**
     * Mirror of ISteamUGC::GetQueryUGCNumTags.
     * C++: uint32 GetQueryUGCNumTags( UGCQueryHandle_t handle, uint32 index );
     */
    public static int GetQueryUGCNumTags(Number handle, int index) {
        Pointer ugc = getUGC();
        if (ugc == null) return 0;
        long h = toHandle(handle);
        return h != 0 ? SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetQueryUGCNumTags(ugc, h, index) : 0;
    }

    /**
     * Mirror of ISteamUGC::GetQueryUGCPreviewURL.
     * C++: bool GetQueryUGCPreviewURL( UGCQueryHandle_t handle, uint32 index, char *pchURL, uint32 cchURLSize );
     * Writes null-terminated URL into pchURL. Returns false on failure.
     */
    public static boolean GetQueryUGCPreviewURL(Number handle, int index, byte[] pchURL) {
        if (pchURL == null || pchURL.length == 0) return false;
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetQueryUGCPreviewURL(ugc, h, index, pchURL, pchURL.length);
    }

    // --- Additional Get* / Set* (mirror of ISteamUGC) ---

    /** Returns SteamAPICall_t; use SteamUtils to poll for GetAppDependenciesResult_t. */
    public static long GetAppDependencies(long publishedFileId) {
        Pointer ugc = getUGC();
        return ugc == null || publishedFileId == 0 ? 0 : SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetAppDependencies(ugc, publishedFileId);
    }

    /** Fills punBytesDownloaded[0] and punBytesTotal[0]. Returns false on failure. */
    public static boolean GetItemDownloadInfo(long publishedFileId, long[] punBytesDownloaded, long[] punBytesTotal) {
        if (publishedFileId == 0 || punBytesDownloaded == null || punBytesDownloaded.length < 1 || punBytesTotal == null || punBytesTotal.length < 1) return false;
        Pointer ugc = getUGC();
        return ugc != null && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetItemDownloadInfo(ugc, publishedFileId, punBytesDownloaded, punBytesTotal);
    }

    /** Fills punSizeOnDisk[0], pchFolder, punTimeStamp[0]. Returns false on failure. */
    public static boolean GetItemInstallInfo(long publishedFileId, long[] punSizeOnDisk, byte[] pchFolder, int[] punTimeStamp) {
        if (publishedFileId == 0 || punSizeOnDisk == null || punSizeOnDisk.length < 1 || pchFolder == null || punTimeStamp == null || punTimeStamp.length < 1) return false;
        Pointer ugc = getUGC();
        return ugc != null && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetItemInstallInfo(ugc, publishedFileId, punSizeOnDisk, pchFolder, pchFolder.length, punTimeStamp);
    }

    /** Returns EItemState flags (uint32). */
    public static int GetItemState(long publishedFileId) {
        Pointer ugc = getUGC();
        return ugc == null || publishedFileId == 0 ? 0 : SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetItemState(ugc, publishedFileId);
    }

    /** handle is UGCUpdateHandle_t. Returns EItemUpdateStatus; fills punBytesProcessed[0], punBytesTotal[0]. */
    public static int GetItemUpdateProgress(Number handle, long[] punBytesProcessed, long[] punBytesTotal) {
        if (handle == null || punBytesProcessed == null || punBytesProcessed.length < 1 || punBytesTotal == null || punBytesTotal.length < 1) return 0;
        Pointer ugc = getUGC();
        long h = toHandle(handle);
        return (ugc == null || h == 0) ? 0 : SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetItemUpdateProgress(ugc, h, punBytesProcessed, punBytesTotal);
    }

    /** Returns SteamAPICall_t; poll for GetUserItemVoteResult_t. */
    public static long GetUserItemVote(long publishedFileId) {
        Pointer ugc = getUGC();
        return ugc == null || publishedFileId == 0 ? 0 : SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetUserItemVote(ugc, publishedFileId);
    }

    public static int GetQueryUGCNumAdditionalPreviews(Number handle, int index) {
        Pointer ugc = getUGC();
        if (ugc == null) return 0;
        long h = toHandle(handle);
        return h != 0 ? SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetQueryUGCNumAdditionalPreviews(ugc, h, index) : 0;
    }

    /** Fills pchURLOrVideoID, pchOriginalFileName, pPreviewType[0] (EItemPreviewType). */
    public static boolean GetQueryUGCAdditionalPreview(Number handle, int index, int previewIndex, byte[] pchURLOrVideoID, byte[] pchOriginalFileName, int[] pPreviewType) {
        if (handle == null || pchURLOrVideoID == null || pchOriginalFileName == null || pPreviewType == null || pPreviewType.length < 1) return false;
        Pointer ugc = getUGC();
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetQueryUGCAdditionalPreview(ugc, h, index, previewIndex, pchURLOrVideoID, pchURLOrVideoID.length, pchOriginalFileName, pchOriginalFileName.length, pPreviewType);
    }

    /** Fills pvecPublishedFileID with child IDs. Returns false on failure. */
    public static boolean GetQueryUGCChildren(Number handle, int index, long[] pvecPublishedFileID) {
        if (handle == null || pvecPublishedFileID == null) return false;
        Pointer ugc = getUGC();
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetQueryUGCChildren(ugc, h, index, pvecPublishedFileID, pvecPublishedFileID.length);
    }

    /** Writes null-terminated tag value into pchValue. */
    public static boolean GetQueryUGCTag(Number handle, int index, int indexTag, byte[] pchValue) {
        if (handle == null || pchValue == null || pchValue.length == 0) return false;
        Pointer ugc = getUGC();
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetQueryUGCTag(ugc, h, index, indexTag, pchValue, pchValue.length);
    }

    /** Writes null-terminated tag display name into pchValue. */
    public static boolean GetQueryUGCTagDisplayName(Number handle, int index, int indexTag, byte[] pchValue) {
        if (handle == null || pchValue == null || pchValue.length == 0) return false;
        Pointer ugc = getUGC();
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetQueryUGCTagDisplayName(ugc, h, index, indexTag, pchValue, pchValue.length);
    }

    /** Fills pchKey and pchValue (null-terminated). keyValueTagIndex is 0-based. */
    public static boolean GetQueryUGCKeyValueTag(Number handle, int index, int keyValueTagIndex, byte[] pchKey, byte[] pchValue) {
        if (handle == null || pchKey == null || pchKey.length == 0 || pchValue == null || pchValue.length == 0) return false;
        Pointer ugc = getUGC();
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetQueryUGCKeyValueTag(ugc, h, index, keyValueTagIndex, pchKey, pchKey.length, pchValue, pchValue.length);
    }

    /** Fills pvecDescriptors with EUGCContentDescriptorID values. Returns number written. */
    public static int GetQueryUGCContentDescriptors(Number handle, int index, int[] pvecDescriptors) {
        if (handle == null || pvecDescriptors == null) return 0;
        Pointer ugc = getUGC();
        long h = toHandle(handle);
        return h != 0 ? SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetQueryUGCContentDescriptors(ugc, h, index, pvecDescriptors, pvecDescriptors.length) : 0;
    }

    /** Writes null-terminated metadata into pchMetadata. */
    public static boolean GetQueryUGCMetadata(Number handle, int index, byte[] pchMetadata) {
        if (handle == null || pchMetadata == null || pchMetadata.length == 0) return false;
        Pointer ugc = getUGC();
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetQueryUGCMetadata(ugc, h, index, pchMetadata, pchMetadata.length);
    }

    public static int GetQueryUGCNumKeyValueTags(Number handle, int index) {
        Pointer ugc = getUGC();
        if (ugc == null) return 0;
        long h = toHandle(handle);
        return h != 0 ? SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetQueryUGCNumKeyValueTags(ugc, h, index) : 0;
    }

    /** eStatType: EItemStatistic. Fills pStatValue[0] with uint64. */
    public static boolean GetQueryUGCStatistic(Number handle, int index, int eStatType, long[] pStatValue) {
        if (handle == null || pStatValue == null || pStatValue.length < 1) return false;
        Pointer ugc = getUGC();
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetQueryUGCStatistic(ugc, h, index, eStatType, pStatValue);
    }

    public static boolean SetMatchAnyTag(Number handle, boolean bMatchAnyTag) {
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_SetMatchAnyTag(ugc, h, bMatchAnyTag);
    }

    public static boolean SetRankedByTrendDays(Number handle, int unDays) {
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_SetRankedByTrendDays(ugc, h, unDays);
    }

    public static boolean SetReturnPlaytimeStats(Number handle, int unDays) {
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_SetReturnPlaytimeStats(ugc, h, unDays);
    }

    /**
     * Allow the query to return cached results. unMaxAgeSeconds &gt; 0 enables cache; 0 disables.
     * C++: bool SetAllowCachedResponse( UGCQueryHandle_t handle, uint32 unMaxAgeSeconds );
     */
    public static boolean SetAllowCachedResponse(Number handle, int unMaxAgeSeconds) {
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        return h != 0 && SteamAPI.INSTANCE.SteamAPI_ISteamUGC_SetAllowCachedResponse(ugc, h, unMaxAgeSeconds);
    }

    /** GetNumSupportedGameVersions / GetSupportedGameVersionData not present in this SDK. */

    private SteamUGC() {}
}
