package me.zed_0xff.zb_better_modlist;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import me.zed_0xff.zombie_buddy.Exposer;

/**
 * Pure mirror of Steamworks ISteamUGC API. JNA bindings only; method names and behaviour
 * match the C#/C API. For Lua-facing helpers (e.g. decoded UGC query result), use
 * {@link SteamLuaHelper}.
 */
@Exposer.LuaClass
public final class SteamUGC {

    private static final String LIBRARY = "steam_api";

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
    private static final boolean DEBUG_GET_QUERY_UGC = true;

    public static boolean GetQueryUGCResult(Number handle, int index, byte[] pDetails) {
        if (pDetails == null || pDetails.length == 0) return false;
        Pointer ugc = getUGC();
        if (ugc == null) return false;
        long h = toHandle(handle);
        if (h == 0) return false;
        if (DEBUG_GET_QUERY_UGC && index <= 4) System.out.println("[ZB SteamUGC] GetQueryUGCResult native call index=" + index + " bufLen=" + pDetails.length);
        Pointer mem = new Memory(pDetails.length);
        boolean ok = SteamAPI.INSTANCE.SteamAPI_ISteamUGC_GetQueryUGCResult(ugc, h, index, mem);
        if (DEBUG_GET_QUERY_UGC && index <= 4) System.out.println("[ZB SteamUGC] GetQueryUGCResult native returned index=" + index + " ok=" + ok);
        if (ok) mem.read(0, pDetails, 0, pDetails.length);
        if (DEBUG_GET_QUERY_UGC && index <= 4) System.out.println("[ZB SteamUGC] GetQueryUGCResult mem.read done index=" + index);
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

    /** GetNumSupportedGameVersions / GetSupportedGameVersionData not present in this SDK. */

    private SteamUGC() {}
}
