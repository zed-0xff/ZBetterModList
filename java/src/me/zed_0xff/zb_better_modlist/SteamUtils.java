package me.zed_0xff.zb_better_modlist;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import me.zed_0xff.zombie_buddy.Exposer;

/**
 * Pure mirror of Steamworks ISteamUtils API. JNA bindings only; method names and behaviour
 * match the C#/C API. For Lua-facing helpers (e.g. decoded callback results), use
 * {@link SteamLuaHelper}.
 */
@Exposer.LuaClass
public final class SteamUtils {

    private static final String LIBRARY = "steam_api";

    public interface SteamAPI extends Library {
        SteamAPI INSTANCE = load();
        static SteamAPI load() {
            try { return Native.load(LIBRARY, SteamAPI.class); } catch (Throwable t) { return null; }
        }
        int SteamAPI_GetHSteamUser();
        int SteamAPI_GetHSteamPipe();
        Pointer SteamAPI_SteamUtils_v010(int hSteamUser, int hSteamPipe);
        int SteamAPI_ISteamUtils_GetAppID(Pointer self);
        boolean SteamAPI_ISteamUtils_IsAPICallCompleted(Pointer self, long hSteamAPICall, boolean[] pbFailed);
        int SteamAPI_ISteamUtils_GetAPICallFailureReason(Pointer self, long hSteamAPICall);
        boolean SteamAPI_ISteamUtils_GetAPICallResult(Pointer self, int hSteamPipe, long hSteamAPICall, Pointer pCallback, int cubCallback, int iCallbackExpected, boolean[] pbFailed);
    }

    private static volatile Pointer steamUtils;
    private static volatile int hSteamPipe;

    private static Pointer getUtils() {
        if (steamUtils != null) return steamUtils;
        SteamAPI api = SteamAPI.INSTANCE;
        if (api == null) return null;
        int user = api.SteamAPI_GetHSteamUser();
        int pipe = api.SteamAPI_GetHSteamPipe();
        if (user == 0 || pipe == 0) return null;
        hSteamPipe = pipe;
        steamUtils = api.SteamAPI_SteamUtils_v010(user, pipe);
        return steamUtils;
    }

    private static long toLong(Number n) { return n != null ? n.longValue() : 0; }

    /**
     * Returns the App ID of the current process (e.g. 108600 for PZ).
     */
    public static int GetAppID() {
        Pointer utils = getUtils();
        if (utils == null) return 0;
        return SteamAPI.INSTANCE.SteamAPI_ISteamUtils_GetAppID(utils);
    }

    /**
     * Returns true when the async call has completed (success or failure).
     * pbFailed is set to true if the call failed.
     */
    public static boolean IsAPICallCompleted(Number hSteamAPICall) {
        Pointer utils = getUtils();
        if (utils == null) return false;
        long h = toLong(hSteamAPICall);
        if (h == 0) return false;
        boolean[] failed = new boolean[1];
        return SteamAPI.INSTANCE.SteamAPI_ISteamUtils_IsAPICallCompleted(utils, h, failed);
    }

    /**
     * ESteamAPICallFailure value when the call failed.
     */
    public static int GetAPICallFailureReason(Number hSteamAPICall) {
        Pointer utils = getUtils();
        if (utils == null) return 0;
        long h = toLong(hSteamAPICall);
        if (h == 0) return 0;
        return SteamAPI.INSTANCE.SteamAPI_ISteamUtils_GetAPICallFailureReason(utils, h);
    }

    /**
     * Fetches the result of an async call. pCallback must be a buffer of size cubCallback.
     * iCallbackExpected = callback type ID (e.g. SteamUGCQueryCompleted_t).
     * Returns true if the call completed and result was written; pbFailed is set if the call failed.
     */
    public static boolean GetAPICallResult(Number hSteamAPICall, int iCallbackExpected, byte[] pCallback, boolean[] pbFailed) {
        Pointer utils = getUtils();
        if (utils == null || pCallback == null || pbFailed == null || pbFailed.length < 1) return false;
        long h = toLong(hSteamAPICall);
        if (h == 0) return false;
        Pointer buf = new Memory(pCallback.length);
        boolean ok = SteamAPI.INSTANCE.SteamAPI_ISteamUtils_GetAPICallResult(utils, hSteamPipe, h, buf, pCallback.length, iCallbackExpected, pbFailed);
        if (ok) buf.read(0, pCallback, 0, pCallback.length);
        return ok;
    }

    private SteamUtils() {}
}
