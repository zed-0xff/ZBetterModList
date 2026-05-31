package me.zed_0xff.zb_better_modlist;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import me.zed_0xff.zombie_buddy.Exposer;
import zombie.gameStates.ChooseGameInfo;

@Exposer.LuaClass
public class ModListHelper {

    private static final Map<String, ArrayList<String>> FILE_LIST_CACHE = new HashMap<>();

    public static ArrayList<String> listFiles(String modId) {
        synchronized (FILE_LIST_CACHE) {
            ArrayList<String> cached = FILE_LIST_CACHE.get(modId);
            if (cached != null) {
                return cached;
            }
        }

        ArrayList<String> result = new ArrayList<>();
        var mod = ChooseGameInfo.getAvailableModDetails(modId);
        if (mod == null) return result;

        File modDir = new File(mod.getDir());
        if (modDir.exists() && modDir.isDirectory()) {
            collectFiles(modDir, "", result);
        }
        Collections.sort(result);

        synchronized (FILE_LIST_CACHE) {
            FILE_LIST_CACHE.put(modId, result);
        }

        return result;
    }

    public static void clearCache() {
        synchronized (FILE_LIST_CACHE) {
            FILE_LIST_CACHE.clear();
        }
    }

    public static void clearCacheFor(String modId) {
        if (modId == null) return;
        synchronized (FILE_LIST_CACHE) {
            FILE_LIST_CACHE.remove(modId);
        }
    }

    private static void collectFiles(File dir, String prefix, ArrayList<String> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = prefix.isEmpty() ? file.getName() : prefix + File.separator + file.getName();
            if (file.isDirectory()) {
                collectFiles(file, name, result);
            } else {
                result.add(name);
            }
        }
    }

    public static long[] toLongArray(ArrayList<String> list) {
        long[] result = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = Long.parseLong(list.get(i));
        }
        return result;
    }
}
