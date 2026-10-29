package com.minigameplayground.create;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import org.slf4j.Logger;

import java.lang.reflect.Field;

/** Re-registers restored logistics links with Create's global logistics manager. */
final class LogisticsRestore {
    private static final Field ADDED_GLOBALLY = field("addedGlobally");
    private static final Field LOADED_GLOBALLY = field("loadedGlobally");

    private LogisticsRestore() {
    }

    static void restore(SmartBlockEntity blockEntity) {
        LogisticallyLinkedBehaviour behaviour =
                blockEntity.getBehaviour(LogisticallyLinkedBehaviour.TYPE);
        if (behaviour == null || ADDED_GLOBALLY == null || LOADED_GLOBALLY == null) {
            return;
        }

        try {
            // The block NBT keeps Added=true. After a block reset the old link may
            // already have been removed from LogisticsNetwork.totalLinks, so a normal
            // initialize() would only add it to loadedLinks and leave global network
            // membership incomplete. Force both registration passes to run again.
            ADDED_GLOBALLY.setBoolean(behaviour, false);
            LOADED_GLOBALLY.setBoolean(behaviour, false);
            behaviour.initialize();
        } catch (Exception exception) {
            MiniGamePlaygroundCreateMod.LOGGER.warn(
                    "Failed to re-register logistics link at {}.", blockEntity.getBlockPos(), exception);
        }
    }

    private static Field field(String name) {
        try {
            Field field = LogisticallyLinkedBehaviour.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Exception exception) {
            MiniGamePlaygroundCreateMod.LOGGER.warn(
                    "Create logistics field '{}' is unavailable; logistics links may not re-register.",
                    name, exception);
            return null;
        }
    }
}
