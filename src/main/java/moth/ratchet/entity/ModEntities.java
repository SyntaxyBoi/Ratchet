package moth.ratchet.entity;

import moth.ratchet.RatchetMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
    public static final EntityType<LatchetRebounderProjectileEntity> LATCHET_REBOUNDER_PROJECTILE = Registry.register(
            Registries.ENTITY_TYPE,
            RatchetMod.id("latchet_rebounder_projectile"),
            FabricEntityTypeBuilder.<LatchetRebounderProjectileEntity>create(SpawnGroup.MISC, LatchetRebounderProjectileEntity::new)
                    .dimensions(EntityDimensions.fixed(0.3F, 0.3F))
                    .trackRangeBlocks(512)
                    .trackedUpdateRate(1)
                    .forceTrackedVelocityUpdates(true)
                    .build()
    );

    private ModEntities() {}

    public static void init() {
    }
}
