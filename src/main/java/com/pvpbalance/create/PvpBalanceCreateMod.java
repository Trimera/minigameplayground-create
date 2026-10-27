package com.pvpbalance.create;

import com.pvpbalance.zone.ZoneManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create compatibility addon for PVP Balance.
 *
 * <p>All zone and configuration data lives in the core {@code pvpbalance} mod.
 * This addon only attaches protection state to Create contraptions and verifies
 * that they cannot be disassembled outside the zone they were assembled in.</p>
 */
public final class PvpBalanceCreateMod implements ModInitializer {
    public static final String MOD_ID = "pvpbalance-create";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Touch the core API once during startup so a missing/incompatible core
        // installation fails visibly instead of surfacing only on first assembly.
        int knownZones = ZoneManager.size();
        LOGGER.info("PVP Balance Create compatibility initialized (core API available, {} zone(s) loaded at init).",
                knownZones);
    }
}
