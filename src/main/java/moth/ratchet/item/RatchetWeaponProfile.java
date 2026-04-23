package moth.ratchet.item;

public record RatchetWeaponProfile(
        double attackDamageModifier,
        double attackSpeedModifier,
        int maxCharge,
        int chargeIntervalTicks,
        float chargeRampStrength,
        int decayIntervalTicks,
        float bonusDirectDamagePerCharge,
        float maxArmorPierceRatioAtFullCharge,
        float directKnockbackPerCharge,
        float areaRadius,
        float splashDamagePerCharge,
        float shockwaveForcePerCharge,
        float shockwaveLiftPerCharge,
        int itemBarColor,
        String flavorTooltipKey
) {}
