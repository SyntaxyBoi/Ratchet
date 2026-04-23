package moth.ratchet.mixin;

import moth.ratchet.combat.RebounderDamageHelper;
import moth.ratchet.item.AbstractRatchetWeaponItem;
import moth.ratchet.item.LatchetRebounderItem;
import moth.ratchet.item.OvercrankRatchetDriverItem;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.entity.LivingEntity.class)
public abstract class LivingEntityOvercrankDamageMixin {
    @ModifyVariable(method = "applyDamage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float ratchet$reduceDamageWhileChargingDriver(float amount) {
        if (amount <= 0.0F || !((Object) this instanceof PlayerEntity player) || !AbstractRatchetWeaponItem.isActivelyCharging(player)) {
            return amount;
        }

        return amount * (1.0F - AbstractRatchetWeaponItem.CHARGE_DAMAGE_BLOCK_RATIO);
    }

    @Inject(method = "applyDamage", at = @At("HEAD"))
    private void ratchet$clearOvercrankCritChargeOnDamage(DamageSource source, float amount, CallbackInfo ci) {
        if (amount <= 0.0F) {
            return;
        }

        if ((Object) this instanceof PlayerEntity player) {
            OvercrankRatchetDriverItem.clearPendingCritCharge(player);
        }
    }

    @Inject(method = "applyDamage", at = @At("TAIL"))
    private void ratchet$loseRebounderChargeAfterAppliedDamage(DamageSource source, float amount, CallbackInfo ci) {
        if (amount <= 0.0F || !((Object) this instanceof PlayerEntity player)) {
            return;
        }

        if (RebounderDamageHelper.isValidChargeLossSource(source)) {
            LatchetRebounderItem.loseHeldCharge(player);
        }
    }
}
