package com.minigameplayground.create.mixin;

import com.minigameplayground.create.MiniGamePlaygroundCreateMod;
import com.minigameplayground.create.compat.ProtectedContraption;
import com.minigameplayground.zone.PlaygroundZone;
import com.minigameplayground.zone.ZoneManager;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fail-closed guard for protected contraption entities that are destroyed without
 * going through {@link Contraption#addBlocksToWorld}. Examples are killing a
 * minecart contraption or removing it from the world outside its zone.
 *
 * <p>Normal disassembly sets {@link Contraption#disassembled} before the entity is
 * discarded, so this guard does not interfere with a valid disassembly.</p>
 */
@Mixin(AbstractContraptionEntity.class)
public abstract class AbstractContraptionEntityMixin {
    @Shadow
    protected Contraption contraption;

    @Inject(method = "remove", at = @At("HEAD"))
    private void minigameplayground$clearProtectedEntityRemovedOutsideZone(RemovalReason reason, CallbackInfo ci) {
        if (reason == RemovalReason.UNLOADED_TO_CHUNK
                || reason == RemovalReason.UNLOADED_WITH_PLAYER
                || reason == RemovalReason.CHANGED_DIMENSION) {
            return;
        }
        if (this.contraption == null || this.contraption.disassembled) {
            return;
        }

        Entity self = (Entity) (Object) this;
        Level level = self.level();
        if (level == null || level.isClientSide()) {
            return;
        }

        ProtectedContraption protection = (ProtectedContraption) (Object) this.contraption;
        if (!protection.minigameplayground$isProtected()) {
            return;
        }

        String reasonText = null;
        if (protection.minigameplayground$isMultiZone()) {
            reasonText = "entity removed while contraption touched multiple zones";
        } else {
            String zoneId = protection.minigameplayground$getZoneId();
            PlaygroundZone zone = zoneId == null ? null : ZoneManager.getZone(zoneId);
            if (zone == null) {
                reasonText = "entity removed and its zone no longer exists";
            } else if (!zone.isDimension(level)) {
                reasonText = "entity removed in a different dimension from its zone";
            } else if (!zone.contains(self.getX(), self.getY(), self.getZ())) {
                reasonText = "entity removed outside its zone";
            }
        }

        if (reasonText == null) {
            return;
        }

        protection.minigameplayground$clearContents();
        this.contraption.disassembled = true;
        MiniGamePlaygroundCreateMod.LOGGER.warn(
                "Cleared protected Create contraption entity at {}: {}",
                self.position(), reasonText);
    }
}
