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
        // Trains are intentionally not restored by playground snapshots. Their
        // authoritative state is RailwaySavedData (TrackGraph/Train/SignalEdgeGroup),
        // which is outside the block/entity snapshot format. Saving carriages alone
        // would leave orphaned train entities. Current trains in the zone are still
        // cleared by clearEntities(), but they are never re-created here.
        return ZoneSnapshots.snapshotEntityNbt(level, zone,
                entity -> handlesEntity(entity) && !isTrainEntity(entity));
    }

    private static boolean isTrainEntity(Entity entity) {
        return entity.getClass().getName().startsWith("com.simibubi.create.content.trains.");
    }

    @Override
    public void clearEntities(ServerLevel level, PlaygroundZone zone) {
        ZoneSnapshots.clearEntities(level, zone, this::handlesEntity);
    }

    @Override
    public void afterRestore(ServerLevel level, PlaygroundZone zone) {
        // Old snapshots created before train exclusion may still contain carriage
        // entities in their extension file. Remove them after the generic core
        // restore instead of leaving an orphaned part of a train in the world.
        ZoneSnapshots.clearEntities(level, zone, CreateSnapshotExtension::isTrainEntity);
        KineticRestore.rebuild(level, zone);
    }
}
