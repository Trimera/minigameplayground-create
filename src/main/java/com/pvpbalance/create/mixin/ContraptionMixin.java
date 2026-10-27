package com.pvpbalance.create.mixin;

import com.google.common.collect.Multimap;
import com.pvpbalance.create.PvpBalanceCreateMod;
import com.pvpbalance.create.compat.ProtectedContraption;
import com.pvpbalance.zone.PvpZone;
import com.pvpbalance.zone.ZoneManager;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.MountedStorageManager;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/**
 * Marks Create contraptions assembled in or across a PVP zone and verifies their
 * final block bounds when they are disassembled.
 *
 * <p>The marker stores the contraption's local AABB. At disassembly the eight
 * corners are run through Create's current {@link StructureTransform}, then the
 * resulting world AABB must fit entirely in the original zone. A contraption
 * touching multiple zones is cleared unconditionally. Missing zones and missing
 * marker bounds fail closed.</p>
 */
@Mixin(Contraption.class)
public abstract class ContraptionMixin implements ProtectedContraption {
    @Unique
    private static final String PVPBALANCE_PROTECTED_TAG = "PvpBalanceProtected";
    @Unique
    private static final String PVPBALANCE_MULTI_ZONE_TAG = "PvpBalanceMultiZone";
    @Unique
    private static final String PVPBALANCE_ZONE_TAG = "PvpBalanceZone";
    @Unique
    private static final String PVPBALANCE_LOCAL_BOUNDS_TAG = "PvpBalanceLocalBounds";

    @Shadow
    public AABB bounds;

    @Shadow
    public BlockPos anchor;

    @Shadow
    public boolean disassembled;

    @Shadow
    @Nullable
    protected Multimap<BlockPos, StructureBlockInfo> capturedMultiblocks;

    @Shadow
    public abstract Map<BlockPos, StructureBlockInfo> getBlocks();

    @Shadow
    public abstract MountedStorageManager getStorage();

    @Unique
    private boolean pvpbalance$protected;
    @Unique
    private boolean pvpbalance$multiZone;
    @Unique
    private String pvpbalance$zoneId;
    @Unique
    private AABB pvpbalance$localBounds;

    /**
     * Called by every Create assembly path immediately after the block scan and
     * after type-specific blocks have been added. {@link Contraption#bounds} is in
     * the contraption's local coordinate space, so adding the anchor gives the
     * assembly-time world AABB.
     */
    @Inject(method = "removeBlocksFromWorld", at = @At("HEAD"))
    private void pvpbalance$markAssembledContraption(Level world, BlockPos offset, CallbackInfo ci) {
        if (world == null || world.isClientSide() || this.bounds == null || this.anchor == null) {
            return;
        }

        AABB worldBounds = this.bounds.move(this.anchor.getX(), this.anchor.getY(), this.anchor.getZ());
        List<PvpZone> intersecting = ZoneManager.getZonesIntersecting(world, worldBounds);
        if (intersecting.isEmpty()) {
            this.pvpbalance$protected = false;
            this.pvpbalance$multiZone = false;
            this.pvpbalance$zoneId = null;
            this.pvpbalance$localBounds = null;
            return;
        }

        this.pvpbalance$protected = true;
        this.pvpbalance$multiZone = intersecting.size() > 1;
        this.pvpbalance$zoneId = this.pvpbalance$multiZone ? null : intersecting.get(0).getId();
        this.pvpbalance$localBounds = copyOf(this.bounds);

        PvpBalanceCreateMod.LOGGER.debug(
                "Protected Create contraption assembled in {}PvP zone(s) at {}; local bounds {}.",
                this.pvpbalance$multiZone ? "multiple " : "", this.anchor, describe(this.bounds));
    }

    @Inject(method = "addBlocksToWorld", at = @At("HEAD"), cancellable = true)
    private void pvpbalance$validateDisassembly(Level world, StructureTransform transform, CallbackInfo ci) {
        if (!this.pvpbalance$protected || this.disassembled
                || world == null || world.isClientSide()) {
            return;
        }

        String reason = this.pvpbalance$getClearReason(world, transform);
        if (reason == null) {
            return;
        }

        this.pvpbalance$clearContents();
        this.disassembled = true;
        PvpBalanceCreateMod.LOGGER.warn(
                "Cleared protected Create contraption at {} (zone={}): {}",
                this.anchor, this.pvpbalance$zoneId, reason);
        ci.cancel();
    }

    @Inject(method = "writeNBT", at = @At("RETURN"))
    private void pvpbalance$writeProtectionNbt(boolean spawnPacket, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        if (tag == null || !this.pvpbalance$protected) {
            return;
        }

        tag.putBoolean(PVPBALANCE_PROTECTED_TAG, true);
        tag.putBoolean(PVPBALANCE_MULTI_ZONE_TAG, this.pvpbalance$multiZone);
        if (this.pvpbalance$zoneId != null) {
            tag.putString(PVPBALANCE_ZONE_TAG, this.pvpbalance$zoneId);
        }
        if (this.pvpbalance$localBounds != null) {
            AABB box = this.pvpbalance$localBounds;
            ListTag list = new ListTag();
            list.add(DoubleTag.valueOf(box.minX));
            list.add(DoubleTag.valueOf(box.minY));
            list.add(DoubleTag.valueOf(box.minZ));
            list.add(DoubleTag.valueOf(box.maxX));
            list.add(DoubleTag.valueOf(box.maxY));
            list.add(DoubleTag.valueOf(box.maxZ));
            tag.put(PVPBALANCE_LOCAL_BOUNDS_TAG, list);
        }
    }

    @Inject(method = "readNBT", at = @At("RETURN"))
    private void pvpbalance$readProtectionNbt(Level world, CompoundTag tag, boolean spawnData, CallbackInfo ci) {
        this.pvpbalance$protected = tag.getBoolean(PVPBALANCE_PROTECTED_TAG);
        this.pvpbalance$multiZone = tag.getBoolean(PVPBALANCE_MULTI_ZONE_TAG);
        this.pvpbalance$zoneId = tag.contains(PVPBALANCE_ZONE_TAG, Tag.TAG_STRING)
                ? tag.getString(PVPBALANCE_ZONE_TAG)
                : null;
        this.pvpbalance$localBounds = readBounds(tag.getList(PVPBALANCE_LOCAL_BOUNDS_TAG, Tag.TAG_DOUBLE));
    }

    @Override
    public boolean pvpbalance$isProtected() {
        return this.pvpbalance$protected;
    }

    @Override
    public boolean pvpbalance$isMultiZone() {
        return this.pvpbalance$multiZone;
    }

    @Override
    @Nullable
    public String pvpbalance$getZoneId() {
        return this.pvpbalance$zoneId;
    }

    @Override
    @Nullable
    public AABB pvpbalance$getLocalBounds() {
        return this.pvpbalance$localBounds;
    }

    @Override
    public void pvpbalance$clearContents() {
        this.getBlocks().clear();
        if (this.capturedMultiblocks != null) {
            this.capturedMultiblocks.clear();
        }

        // Mounted item storages are what would otherwise survive as chest contents
        // inside a minecart contraption item after the block map is cleared.
        try {
            for (MountedItemStorage storage : this.getStorage().getAllItemStorages().values()) {
                for (int slot = 0; slot < storage.getSlotCount(); slot++) {
                    if (!storage.getStackInSlot(slot).isEmpty()) {
                        storage.setStackInSlot(slot, ItemStack.EMPTY);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // If the storage manager is not initialized there is nothing to clear.
        }
    }

    @Nullable
    private String pvpbalance$getClearReason(Level world, @Nullable StructureTransform transform) {
        if (this.pvpbalance$multiZone) {
            return "contraption touched more than one PvP zone";
        }
        if (this.pvpbalance$zoneId == null) {
            return "protection marker has no zone id";
        }
        if (this.pvpbalance$localBounds == null) {
            return "protection marker has no local bounds";
        }
        if (transform == null) {
            return "no structure transform";
        }

        PvpZone zone = ZoneManager.getZone(this.pvpbalance$zoneId);
        if (zone == null) {
            return "zone '" + this.pvpbalance$zoneId + "' no longer exists";
        }
        if (!zone.isDimension(world)) {
            return "contraption is in a different dimension from zone '" + this.pvpbalance$zoneId + "'";
        }

        AABB finalBounds = transformBounds(this.pvpbalance$localBounds, transform);
        if (!zone.fullyContainsBlockBounds(finalBounds)) {
            return "final bounds " + describe(finalBounds)
                    + " are not fully inside zone bounds " + zone.describeBounds();
        }
        return null;
    }

    @Unique
    private static AABB copyOf(AABB box) {
        return new AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    @Unique
    private static AABB transformBounds(AABB local, StructureTransform transform) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (double x : new double[]{local.minX, local.maxX}) {
            for (double y : new double[]{local.minY, local.maxY}) {
                for (double z : new double[]{local.minZ, local.maxZ}) {
                    Vec3 transformed = transform.apply(new Vec3(x, y, z));
                    minX = Math.min(minX, transformed.x);
                    minY = Math.min(minY, transformed.y);
                    minZ = Math.min(minZ, transformed.z);
                    maxX = Math.max(maxX, transformed.x);
                    maxY = Math.max(maxY, transformed.y);
                    maxZ = Math.max(maxZ, transformed.z);
                }
            }
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Unique
    @Nullable
    private static AABB readBounds(ListTag list) {
        if (list.size() != 6) {
            return null;
        }
        double minX = list.getDouble(0);
        double minY = list.getDouble(1);
        double minZ = list.getDouble(2);
        double maxX = list.getDouble(3);
        double maxY = list.getDouble(4);
        double maxZ = list.getDouble(5);
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                || minX > maxX || minY > maxY || minZ > maxZ) {
            return null;
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Unique
    private static String describe(AABB box) {
        return String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f) -> (%.2f, %.2f, %.2f)",
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }
}
