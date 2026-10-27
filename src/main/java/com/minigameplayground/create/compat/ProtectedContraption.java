package com.minigameplayground.create.compat;

import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

/**
 * Server-side protection state attached to Create contraptions by
 * {@code ContraptionMixin}. The state is written into the contraption NBT so it
 * survives both entity serialization and minecart-contraption item round trips.
 */
public interface ProtectedContraption {
    boolean minigameplayground$isProtected();

    boolean minigameplayground$isMultiZone();

    @Nullable
    String minigameplayground$getZoneId();

    @Nullable
    AABB minigameplayground$getLocalBounds();

    /**
     * Removes the contraption's block map and mounted item inventories without
     * placing any blocks or creating drops.
     */
    void minigameplayground$clearContents();
}
