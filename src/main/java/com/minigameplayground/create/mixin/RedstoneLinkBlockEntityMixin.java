package com.minigameplayground.create.mixin;

import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WorldEdit can load block-entity NBT before Create has created the link behaviour.
 * {@code RedstoneLinkBlockEntity.read()} then dereferences a null {@code link} while
 * reading {@code newPosition}, aborting the whole zone restore. Create the behaviour
 * first, using the transmitter flag from the incoming tag so the correct mode is used.
 */
@Mixin(RedstoneLinkBlockEntity.class)
public abstract class RedstoneLinkBlockEntityMixin {
    @Shadow
    protected LinkBehaviour link;

    @Shadow
    private boolean transmitter;

    @Shadow
    protected abstract void createLink();

    @Inject(method = "read", at = @At("HEAD"))
    private void minigameplayground$createLinkBeforeRead(
            CompoundTag tag, boolean clientPacket, CallbackInfo ci) {
        if (link == null) {
            transmitter = tag.getBoolean("Transmitter");
            createLink();
        }
    }
}
