package com.minigameplayground.create;

import com.minigameplayground.zone.ZoneManager;
import com.minigameplayground.zone.ZoneSnapshots;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create compatibility addon for MiniGamePlayground.
 *
 * <p>All zone and configuration data lives in the core {@code minigameplayground} mod.
 * This addon attaches protection state to Create contraptions, verifies that they
 * cannot be disassembled outside the zone they were assembled in, and provides the
 * Core snapshot extension that saves/restores Create entities without adding a
 * Create dependency to the core mod.</p>
 */
public final class MiniGamePlaygroundCreateMod implements ModInitializer {
    public static final String MOD_ID = "minigameplayground-create";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Touch the core API once during startup so a missing/incompatible core
        // installation fails visibly instead of surfacing only on first assembly.
        int knownZones = ZoneManager.size();
        ZoneSnapshots.registerExtension(new CreateSnapshotExtension());
        LOGGER.info("MiniGamePlayground Create compatibility initialized (core API available, {} zone(s) at init).",
                knownZones);
    }
}
