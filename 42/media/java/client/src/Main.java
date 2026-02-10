package me.zed_0xff.zb_better_modlist;

import me.zed_0xff.zombie_buddy.Exposer;

public class Main {
    public static void main(String[] args) {
        Exposer.exposeClassToLua(ModListHelper.class);
    }
}
