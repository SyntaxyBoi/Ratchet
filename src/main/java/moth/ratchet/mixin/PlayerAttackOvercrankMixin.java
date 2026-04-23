package moth.ratchet.mixin;

import moth.ratchet.item.OvercrankRatchetDriverItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerAttackOvercrankMixin {
    @Inject(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
            )
    )
    private void ratchet$trackOvercrankCrit(Entity target, CallbackInfo ci) {
        if (!(target instanceof LivingEntity)) {
            return;
        }

        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!isCritical(player)) {
            return;
        }

        ItemStack stack = player.getMainHandStack();
        OvercrankRatchetDriverItem.onCriticalHit(player, stack);
    }

    private boolean isCritical(PlayerEntity player) {
        World world = player.getWorld();
        if (world.isClient()) return false;
        if (player.getAttackCooldownProgress(0.5F) <= 0.9F) return false;
        if (player.isOnGround()) return false;
        if (player.fallDistance <= 0.0F) return false;
        if (player.isClimbing()) return false;
        if (player.isTouchingWater()) return false;
        if (player.hasStatusEffect(StatusEffects.BLINDNESS)) return false;
        if (player.hasVehicle()) return false;
        return !player.isSprinting();
    }
}
