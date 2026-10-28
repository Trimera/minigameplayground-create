package com.minigameplayground.create.mixin;

import com.minigameplayground.RestoreContext;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create clears kinetic speed/network data in
 * {@code updateIndirectNeighbourShapes} whenever a kinetic block is placed.
 *
 * <p>WorldEdit and vanilla structure placement call this hook before applying the
 * block entity NBT, which would otherwise restore motors, shafts and belts at
 * speed 0. During a playground restore we skip this destructive hook; the saved
 * block entity NBT then rehydrates the kinetic state.</p>
 */
@Mixin(KineticBlock.class)
public abstract class KineticBlockMixin {
    @Inject(method = "updateIndirectNeighbourShapes", at = @At("HEAD"), cancellable = true)
    private void minigameplayground$preserveKineticStateDuringRestore(
            BlockState state, LevelAccessor level, BlockPos pos, int flags, int count, CallbackInfo ci) {
        if (RestoreContext.isRestoring()) {
            ci.cancel();
        }
    }
}
