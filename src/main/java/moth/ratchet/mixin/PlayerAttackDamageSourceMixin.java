package moth.ratchet.mixin;

import moth.ratchet.RatchetDamageSources;
import moth.ratchet.enchant.ModEnchantments;
import moth.ratchet.item.HeavyRatchetDriverItem;
import moth.ratchet.item.OvercrankRatchetDriverItem;
import moth.ratchet.item.RatchetDriverItem;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PlayerEntity.class)
public abstract class PlayerAttackDamageSourceMixin {
    @ModifyArg(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
            ),
            index = 0
    )
    private DamageSource ratchet$useWeaponSpecificDamageSource(DamageSource originalSource) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) {
            return originalSource;
        }

        ItemStack stack = player.getMainHandStack();
        if (stack.getItem() instanceof RatchetDriverItem) {
            return RatchetDamageSources.ratchetDriver(
                    serverWorld,
                    player,
                    stack,
                    ModEnchantments.hasObliterate(stack),
                    ModEnchantments.hasOvercharge(stack)
            );
        }
        if (stack.getItem() instanceof HeavyRatchetDriverItem) {
            return RatchetDamageSources.heavyRatchetDriver(
                    serverWorld,
                    player,
                    stack,
                    ModEnchantments.hasObliterate(stack),
                    ModEnchantments.hasOvercharge(stack)
            );
        }
        if (stack.getItem() instanceof OvercrankRatchetDriverItem) {
            return RatchetDamageSources.overcrankRatchetDriver(
                    serverWorld,
                    player,
                    stack,
                    ModEnchantments.hasInversion(stack),
                    ModEnchantments.hasOvercharge(stack)
            );
        }

        return originalSource;
    }
}
