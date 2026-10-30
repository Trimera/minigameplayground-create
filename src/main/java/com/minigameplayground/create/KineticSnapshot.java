package com.minigameplayground.create;

import com.minigameplayground.zone.PlaygroundZone;
import com.minigameplayground.zone.ZoneSnapshots;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;

/**
 * Reapplies kinetic block-entity fields from the zone's WorldEdit schematic.
 *
 * <p>WorldEdit can reuse a block entity that is already in the world and skip or
 * reorder the NBT load. In that case the block state is restored but the kinetic
 * fields (and sometimes the sequencer fields) of the existing block entity are left
 * at their pre-reset values. Reading the values from the schematic makes the restore
 * independent of WorldEdit's internal paste order and works for existing snapshots
 * that predate this addon version.</p>
 */
final class KineticSnapshot {
    private static final int TAG_COMPOUND = 10;

    private KineticSnapshot() {
    }

    static void apply(ServerLevel level, PlaygroundZone zone) {
        Map<BlockPos, CompoundTag> entries = ZoneSnapshots.readSnapshotBlockEntities(zone);
        for (Map.Entry<BlockPos, CompoundTag> entry : entries.entrySet()) {
            if (!(level.getBlockEntity(entry.getKey()) instanceof KineticBlockEntity kinetic)) {
                continue;
            }
            apply(kinetic, entry.getValue());
        }
    }

    private static void apply(KineticBlockEntity kinetic, CompoundTag saved) {
        CompoundTag data = saved.copy();
        // load() restores subclass fields (sequencer instructions, belt contents,
        // etc.) even when WorldEdit reused the old block entity.
        kinetic.load(data);
        if (data.contains("Speed")) {
            kinetic.setSpeed(data.getFloat("Speed"));
        }
        if (data.contains("Source", TAG_COMPOUND)) {
            BlockPos source = NbtUtils.readBlockPos(data.getCompound("Source"));
            if (source != null) {
                kinetic.source = source;
            }
        }
        if (data.contains("Network", TAG_COMPOUND)
                && data.getCompound("Network").contains("Id", 4)) {
            kinetic.network = data.getCompound("Network").getLong("Id");
        }
        if (kinetic instanceof SequencedGearshiftBlockEntity gearshift) {
            gearshift.sequenceContext = data.contains("Sequence", TAG_COMPOUND)
                    ? SequencedGearshiftBlockEntity.SequenceContext.fromNBT(data.getCompound("Sequence"))
                    : null;
        }
        kinetic.setChanged();
    }
}
