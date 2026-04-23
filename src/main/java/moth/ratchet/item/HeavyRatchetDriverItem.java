package moth.ratchet.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.MathHelper;

public class HeavyRatchetDriverItem extends AbstractRatchetWeaponItem {
    private static final float MIN_AOE_RADIUS = 2.5F;
    private static final float MAX_AOE_RADIUS = 6.0F;

    private static final RatchetWeaponProfile PROFILE = new RatchetWeaponProfile(
            7.5D,
            -3.15D,
            4,
            30,
            0.6F,
            300,
            2.75F,
            0.25F,
            0.0F,
            MAX_AOE_RADIUS,
            0.0F,
            0.18F,
            0.03F,
            0xC44721,
            "tooltip.ratchet.heavy_ratchet_driver"
    );

    public HeavyRatchetDriverItem() {
        super(PROFILE, new Item.Settings().rarity(Rarity.RARE).fireproof());
    }

    @Override
    protected void applyExtraPrimaryImpact(LivingEntity target, LivingEntity attacker, int charge, float directBonusDamage) {
        int effectCharge = getEffectCharge(charge);
        double slamVelocity = -0.3D - (0.08D * effectCharge);
        target.setVelocity(target.getVelocity().x, Math.min(target.getVelocity().y, slamVelocity), target.getVelocity().z);
        target.velocityModified = true;

        float armorPierceRatio = getCurrentArmorPierceRatio(charge);
        if (armorPierceRatio > 0.0F && isFullyArmored(target)) {
            playArmorCrackSound(target, armorPierceRatio);
        }
    }

    @Override
    protected float getAreaRadius(LivingEntity primaryTarget, LivingEntity attacker, int charge, float directBonusDamage) {
        return MathHelper.lerp(getEffectChargeProgress(charge), MIN_AOE_RADIUS, MAX_AOE_RADIUS);
    }

    @Override
    protected float getSplashDamage(LivingEntity secondaryTarget, LivingEntity primaryTarget, LivingEntity attacker,
                                    int charge, float directBonusDamage, float radius) {
        return directBonusDamage * 0.6F;
    }

    @Override
    protected float getShockwaveForce(LivingEntity secondaryTarget, LivingEntity primaryTarget, LivingEntity attacker,
                                      int charge, float directBonusDamage, float radius) {
        return 0.14F + (0.03F * getEffectCharge(charge));
    }

    @Override
    protected double getShockwaveLift(LivingEntity secondaryTarget, LivingEntity primaryTarget, LivingEntity attacker,
                                      int charge, float directBonusDamage, float radius) {
        return 0.015D + (0.01D * getEffectCharge(charge));
    }

    @Override
    protected boolean usesGroundImpactParticles() {
        return true;
    }

    @Override
    protected float getParticleRadius(LivingEntity primaryTarget, LivingEntity attacker, int charge, float directBonusDamage,
                                      float radius) {
        return radius;
    }

    private float getCurrentArmorPierceRatio(int charge) {
        if (getProfile().maxArmorPierceRatioAtFullCharge() <= 0.0F || getProfile().maxCharge() <= 0) {
            return 0.0F;
        }

        return Math.min(
                getProfile().maxArmorPierceRatioAtFullCharge(),
                getProfile().maxArmorPierceRatioAtFullCharge() * getEffectChargeProgress(charge)
        );
    }

    private boolean isFullyArmored(LivingEntity target) {
        int equippedArmorPieces = 0;
        for (ItemStack armorStack : target.getArmorItems()) {
            if (!armorStack.isEmpty()) {
                equippedArmorPieces++;
            }
        }
        return equippedArmorPieces >= 4;
    }

    private void playArmorCrackSound(LivingEntity target, float armorPierceRatio) {
        float intensity = MathHelper.clamp(
                armorPierceRatio / Math.max(0.01F, getProfile().maxArmorPierceRatioAtFullCharge()),
                0.0F,
                1.0F
        );

        float impactVolume = 0.7F + (0.7F * intensity);
        float impactPitch = 0.8F - (0.15F * intensity);
        float crackVolume = 0.35F + (0.6F * intensity);
        float crackPitch = 1.15F - (0.2F * intensity);

        target.getWorld().playSound(
                null,
                target.getX(),
                target.getY(),
                target.getZ(),
                SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR,
                SoundCategory.PLAYERS,
                impactVolume,
                impactPitch
        );
        target.getWorld().playSound(
                null,
                target.getX(),
                target.getY(),
                target.getZ(),
                SoundEvents.BLOCK_ANVIL_LAND,
                SoundCategory.PLAYERS,
                crackVolume,
                crackPitch
        );
    }
}
