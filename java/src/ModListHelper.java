package me.zed_0xff.zb_better_modlist;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
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
}
