package com.pvpbalance.create;

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
        LOGGER.info("PVP Balance Create compatibility initialized.");
    }
}
