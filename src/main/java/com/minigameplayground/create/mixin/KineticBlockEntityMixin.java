package com.minigameplayground.create.mixin;

import com.minigameplayground.RestoreContext;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create's schematic/block placement path can mark a kinetic block entity as
 * "moved" before its saved NBT is read. In that state KineticBlockEntity.read()
 * deliberately skips Speed, Source and Network and leaves the block at 0 speed.
 *
 * <p>During a playground restore the block is not being carried by a contraption;
 * it is being placed back from a snapshot. Reapply the saved kinetic fields at the
 * end of read() so a transient wasMoved flag cannot discard them.</p>
 */
@Mixin(KineticBlockEntity.class)
public abstract class KineticBlockEntityMixin {
    @Inject(method = "read", at = @At("TAIL"))
    private void minigameplayground$reapplySavedKineticsAfterRestore(
            CompoundTag tag, boolean clientPacket, CallbackInfo ci) {
        if (!RestoreContext.isRestoring()) {
            return;
        }

        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        if (tag.contains("Speed")) {
            self.setSpeed(tag.getFloat("Speed"));
        }
        if (tag.contains("Source", CompoundTag.TAG_COMPOUND)) {
            BlockPos source = NbtUtils.readBlockPos(tag.getCompound("Source"));
            if (source != null) {
                self.setSource(source);
            }
        }
        if (tag.contains("Network", CompoundTag.TAG_COMPOUND)) {
            CompoundTag network = tag.getCompound("Network");
            if (network.contains("Id", CompoundTag.TAG_LONG)) {
                self.setNetwork(network.getLong("Id"));
            }
        }
    }
}
