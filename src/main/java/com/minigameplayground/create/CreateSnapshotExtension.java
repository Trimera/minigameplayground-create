package com.minigameplayground.create;

import com.minigameplayground.zone.PlaygroundZone;
import com.minigameplayground.zone.ZoneSnapshotExtension;
import com.minigameplayground.zone.ZoneSnapshots;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Keeps Create entities, especially contraption entities, in a separate snapshot
 * extension so the core mod never needs a compile-time Create dependency.
 */
public final class CreateSnapshotExtension implements ZoneSnapshotExtension {
    public static final String ID = "create";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean handlesEntity(Entity entity) {
        return entity != null && entity.getClass().getName().startsWith("com.simibubi.create.");
    }

    @Override
    public ListTag saveEntities(ServerLevel level, PlaygroundZone zone) {
        return ZoneSnapshots.snapshotEntityNbt(level, zone, this::handlesEntity);
    }

    @Override
    public void clearEntities(ServerLevel level, PlaygroundZone zone) {
        ZoneSnapshots.clearEntities(level, zone, this::handlesEntity);
    }

    @Override
    public void afterRestore(ServerLevel level, PlaygroundZone zone) {
        KineticRestore.rebuild(level, zone);
    }
}
