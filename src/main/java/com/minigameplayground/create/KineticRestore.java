package com.minigameplayground.create;

import com.minigameplayground.zone.PlaygroundZone;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rebuilds Create's in-memory kinetic networks after a block restore.
 *
 * <p>Block entity NBT stores {@code Speed}, {@code Source} and {@code Network}
 * metadata, but the actual {@link KineticNetwork} objects and their member maps live
 * only in memory. WorldEdit replaces the kinetic block entities, so without this step
 * the restored blocks can have valid-looking NBT while their network is empty. Some
 * shafts/cogs then remain stopped or become overstressed until Create happens to
 * rebuild the network later.</p>
 */
final class KineticRestore {
    private static final double SCAN_MARGIN = 2.0D;

    private KineticRestore() {
    }

    static void rebuild(ServerLevel level, PlaygroundZone zone) {
        List<KineticBlockEntity> kinetics = collect(level, zone);
        if (kinetics.isEmpty()) {
            return;
        }

        Set<Long> networkIds = new LinkedHashSet<>();
        for (KineticBlockEntity kinetic : kinetics) {
            if (kinetic.network != null) {
                networkIds.add(kinetic.network);
            }
        }

        // setNetwork(null) removes a stale entry from the old (usually empty) in-memory
        // network; setNetwork(savedId) then registers the block in the real network
        // object and repopulates its members/sources maps.
        for (KineticBlockEntity kinetic : kinetics) {
            Long savedNetwork = kinetic.network;
            if (savedNetwork != null) {
                kinetic.setNetwork(null);
                kinetic.setNetwork(savedNetwork);
            }
            kinetic.networkDirty = true;
            kinetic.updateSpeed = true;
            kinetic.setChanged();
        }

        Map<Long, KineticNetwork> redesigned = new HashMap<>();
        for (KineticBlockEntity kinetic : kinetics) {
            Long networkId = kinetic.network;
            if (networkId == null) {
                continue;
            }
            KineticNetwork network = kinetic.getOrCreateNetwork();
            if (network != null) {
                redesigned.put(networkId, network);
            }
        }

        for (KineticNetwork network : redesigned.values()) {
            network.updateNetwork();
            network.sync();
        }

        // Push the repaired speed/stress state to clients. Block entity NBT is
        // normally included in the block update, but an explicit notify is useful
        // after a bulk schematic paste.
        for (KineticBlockEntity kinetic : kinetics) {
            if (level.getBlockEntity(kinetic.getBlockPos()) == kinetic) {
                kinetic.setChanged();
                level.sendBlockUpdated(kinetic.getBlockPos(), kinetic.getBlockState(),
                        kinetic.getBlockState(), 3);
            }
        }
    }

    private static List<KineticBlockEntity> collect(ServerLevel level, PlaygroundZone zone) {
        List<KineticBlockEntity> result = new ArrayList<>();
        Set<BlockPos> extraPositions = new LinkedHashSet<>();
        var box = zone.getBlockBounds().inflate(SCAN_MARGIN);

        for (BlockPos pos : BlockPos.betweenClosed(
                (int) Math.floor(box.minX), (int) Math.floor(box.minY), (int) Math.floor(box.minZ),
                (int) Math.floor(box.maxX), (int) Math.floor(box.maxY), (int) Math.floor(box.maxZ))) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof KineticBlockEntity kinetic) {
                result.add(kinetic);
                if (kinetic.source != null) {
                    extraPositions.add(kinetic.source.immutable());
                }
            }
        }

        // A network's source may sit just outside the playground (for example a
        // creative motor behind a wall). Include that block so stress/capacity can be
        // reconstructed instead of making the whole restored network overstressed.
        for (BlockPos pos : extraPositions) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof KineticBlockEntity kinetic && !result.contains(kinetic)) {
                result.add(kinetic);
            }
        }
        return result;
    }
}
