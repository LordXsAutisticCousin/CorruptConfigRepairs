package dev.autism.prominence.annihilator;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class Annihilator implements PreLaunchEntrypoint {
    public static final String MOD_ID = "corruptconfigannihilator";

    @Override
    public void onPreLaunch() {
        Repair.run(FabricLoader.getInstance().getGameDir());
    }
}
