package me.zed_0xff.zb_better_modlist;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.zed_0xff.zombie_buddy.Exposer;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;

/**
 * Async fetch of Steam Workshop collection details via the public Steam Web API.
 * <p>
 * Replaces the native {@code ISteamUGC::CreateQueryUGCDetailsRequest} + {@code SetReturnChildren}
 * flow, which on PZ's bundled {@code libsteam_api.dylib} (STEAMUGC_INTERFACE_VERSION021+)
 * returns {@code k_EResultFail} for any "extended fields" setter on a details query.
 * <p>
 * Hits two public endpoints (no API key required):
 * <ul>
 *   <li>{@code ISteamRemoteStorage/GetCollectionDetails/v1/} -> children IDs</li>
 *   <li>{@code ISteamRemoteStorage/GetPublishedFileDetails/v1/} -> collection title</li>
 * </ul>
 * Lua usage:
 * <pre>
 *   local pending = SteamCollection.fetch(id)
 *   -- each frame:
 *   local result = pending:poll()
 *   if result then
 *       -- { id = "...", title = "...", children = { "id1", "id2", ... } }
 *   end
 * </pre>
 */
@Exposer.LuaClass
public final class SteamCollection {

    private static final String API_BASE = "https://api.steampowered.com/ISteamRemoteStorage/";
    private static final String GET_COLLECTION_DETAILS = API_BASE + "GetCollectionDetails/v1/";
    private static final String GET_PUBLISHED_FILE_DETAILS = API_BASE + "GetPublishedFileDetails/v1/";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    /** Matches {@code "children": [ ... ]} block and captures its body. */
    private static final Pattern CHILDREN_BLOCK = Pattern.compile(
            "\"children\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    /** Matches any {@code "publishedfileid": "12345"} occurrence. */
    private static final Pattern PUBLISHED_FILE_ID = Pattern.compile(
            "\"publishedfileid\"\\s*:\\s*\"(\\d+)\"");
    /** Matches a top-level {@code "title": "..."} field (first occurrence wins). */
    private static final Pattern TITLE_FIELD = Pattern.compile(
            "\"title\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final String collectionId;
    private volatile KahluaTable result;
    private volatile boolean done;

    private SteamCollection(String id) {
        this.collectionId = id;
        Thread t = new Thread(this::run, "SteamCollection-" + id);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Start an async fetch for the given workshop collection ID. Non-blocking.
     *
     * @param id workshop collection PublishedFileId as a string.
     * @return a handle whose {@link #poll()} returns the result table when ready, or null on bad input.
     */
    public static SteamCollection fetch(String id) {
        if (id == null) return null;
        String trimmed = id.trim();
        if (trimmed.isEmpty()) return null;
        return new SteamCollection(trimmed);
    }

    /**
     * @return result table {@code { id, title, children = {cid, ...} }} once ready, or null while pending.
     *         When done but the remote call failed, returns a table with just {@code id} set and
     *         an empty {@code children} table (so callers can stop waiting).
     */
    public KahluaTable poll() {
        return done ? result : null;
    }

    private void run() {
        KahluaTable out = LuaManager.platform.newTable();
        out.rawset("id", collectionId);
        KahluaTable children = LuaManager.platform.newTable();
        out.rawset("children", children);
        try {
            List<String> childIds = fetchChildIds(collectionId);
            int i = 0;
            for (String cid : childIds) children.rawset(Double.valueOf(++i), cid);

            String title = fetchTitle(collectionId);
            if (title != null && !title.isEmpty()) out.rawset("title", title);
        } catch (Throwable t) {
            System.out.println("[ZBetterModList] SteamCollection.fetch(" + collectionId + ") failed: " + t);
        } finally {
            result = out;
            done = true;
        }
    }

    private static List<String> fetchChildIds(String collectionId) throws IOException {
        String body = "collectioncount=1&" + urlEncodeArrayParam("publishedfileids", 0, collectionId);
        String json = httpPostForm(GET_COLLECTION_DETAILS, body);
        return parseChildIds(json);
    }

    private static String fetchTitle(String collectionId) throws IOException {
        String body = "itemcount=1&" + urlEncodeArrayParam("publishedfileids", 0, collectionId);
        String json = httpPostForm(GET_PUBLISHED_FILE_DETAILS, body);
        return parseTitle(json);
    }

    private static String urlEncodeArrayParam(String name, int index, String value) {
        String key = URLEncoder.encode(name + "[" + index + "]", StandardCharsets.UTF_8);
        String val = URLEncoder.encode(value, StandardCharsets.UTF_8);
        return key + "=" + val;
    }

    private static String httpPostForm(String url, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept", "application/json");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " from " + url);
            try (InputStream is = conn.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] tmp = new byte[4096];
                int n;
                while ((n = is.read(tmp)) > 0) buf.write(tmp, 0, n);
                return buf.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Extract child publishedfileids from a GetCollectionDetails response. Shape:
     * {@code response.collectiondetails[0].children[*].publishedfileid}.
     */
    static List<String> parseChildIds(String json) {
        List<String> out = new ArrayList<>();
        if (json == null) return out;
        Matcher block = CHILDREN_BLOCK.matcher(json);
        if (!block.find()) return out;
        Matcher m = PUBLISHED_FILE_ID.matcher(block.group(1));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /**
     * Extract the first {@code "title"} field from a GetPublishedFileDetails response. Steam returns
     * {@code response.publishedfiledetails[0].title} as the only title in this JSON, so first-match wins.
     */
    static String parseTitle(String json) {
        if (json == null) return null;
        Matcher m = TITLE_FIELD.matcher(json);
        if (!m.find()) return null;
        return unescapeJsonString(m.group(1));
    }

    /** Minimal JSON string-escape decoder: handles quote, backslash, slash, b/f/n/r/t, and unicode escapes. */
    private static String unescapeJsonString(String s) {
        if (s == null || s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) { sb.append(c); continue; }
            char esc = s.charAt(++i);
            switch (esc) {
                case '"': case '\\': case '/': sb.append(esc); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    if (i + 4 < s.length()) {
                        try { sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; }
                        catch (NumberFormatException e) { sb.append(esc); }
                    } else sb.append(esc);
                    break;
                default: sb.append(esc);
            }
        }
        return sb.toString();
    }
}
