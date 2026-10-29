package com.minigameplayground.create;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Rebuilds Create's transient fluid pipe networks after a block reset. */
final class FluidRestore {
    private FluidRestore() {
    }

    static void restore(ServerLevel level, BlockPos pos, BlockEntity blockEntity) {
        if (blockEntity instanceof FluidPipeBlockEntity) {
            FluidPropagator.propagateChangedPipe(level, pos, level.getBlockState(pos));
        }
    }
}
