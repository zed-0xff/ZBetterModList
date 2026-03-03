package me.zed_0xff.zb_better_modlist;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import zombie.core.znet.SteamUtils;
import zombie.gameStates.ChooseGameInfo;

import me.zed_0xff.zombie_buddy.Exposer;

@Exposer.LuaClass
public class ModListHelper {

    public static ArrayList<String> listFiles(String modId) {
        ArrayList<String> result = new ArrayList<>();
        var mod = ChooseGameInfo.getAvailableModDetails(modId);
        if (mod == null) return result;

        File modDir = new File(mod.getDir());
        if (modDir.exists() && modDir.isDirectory()) {
            collectFiles(modDir, "", result);
        }
        Collections.sort(result);
        return result;
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

    /**
     * Unsubscribe from a Steam Workshop mod by its workshop ID (e.g. "1234567890").
     * Backend only: sends the request to Steam; the item is removed after the game quits.
     *
     * @param workshopId Workshop published file ID as string (from mod info / getWorkshopID).
     * @return true if the unsubscribe request was sent, false if Steam unavailable or invalid ID.
     */
    public static boolean unsubscribeFromWorkshopItem(String workshopId) {
        if (workshopId == null || workshopId.isEmpty()) {
            return false;
        }
        if (!SteamUtils.isValidSteamID(workshopId)) {
            return false;
        }
        long id = SteamUtils.convertStringToSteamID(workshopId);
        return SteamUGCJNA.unsubscribeItem(id);
    }
}
