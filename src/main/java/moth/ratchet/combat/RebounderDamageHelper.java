package moth.ratchet.combat;

import moth.ratchet.RatchetDamageSources;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class RebounderDamageHelper {
    public static final int ARMOR_PIERCE_LOCKOUT_TICKS = 60;
    private static final float ARMOR_PIERCE_RATIO = 0.5F;
    private static final Map<ArmorPierceKey, Long> ARMOR_PIERCE_LOCKOUTS = new HashMap<>();

    private RebounderDamageHelper() {}

    public static boolean isValidChargeLossSource(DamageSource source) {
        return source.isOf(DamageTypes.EXPLOSION)
                || source.isOf(DamageTypes.PLAYER_EXPLOSION)
                || source.isIndirect()
                || source.getAttacker() instanceof LivingEntity;
    }

    public static void applyPiercingDamage(ServerWorld world, LivingEntity target, Entity source, LivingEntity attacker,
                                           ItemStack weaponStack, DamageSource normalSource, float damage) {
        if (damage <= 0.0F || !target.isAlive()) {
            return;
        }

        long worldTime = world.getTime();
        pruneExpiredLocks(worldTime);

        ArmorPierceKey key = new ArmorPierceKey(world.getRegistryKey().getValue().toString(), target.getUuid());
        Long lockExpiresAt = ARMOR_PIERCE_LOCKOUTS.get(key);
        if (lockExpiresAt != null && lockExpiresAt > worldTime) {
            dealDamageInstance(target, normalSource, damage);
            return;
        }

        float normalDamage = damage * (1.0F - ARMOR_PIERCE_RATIO);
        float pierceDamage = damage * ARMOR_PIERCE_RATIO;

        dealDamageInstance(target, normalSource, normalDamage);
        if (target.isAlive()) {
            dealDamageInstance(target, RatchetDamageSources.rebounderPierce(world, source, attacker, weaponStack), pierceDamage);
            ARMOR_PIERCE_LOCKOUTS.put(key, worldTime + ARMOR_PIERCE_LOCKOUT_TICKS);
        }
    }

    public static void applyKnockbackImpulse(LivingEntity target, Vec3d direction, double horizontalStrength, double verticalStrength) {
        Vec3d horizontal = new Vec3d(direction.x, 0.0D, direction.z);
        if (horizontal.lengthSquared() < 1.0E-4D) {
            horizontal = direction;
        }
        if (horizontal.lengthSquared() < 1.0E-4D) {
            horizontal = new Vec3d(1.0D, 0.0D, 0.0D);
        }

        Vec3d impulse = horizontal.normalize().multiply(horizontalStrength);
        target.addVelocity(impulse.x, verticalStrength, impulse.z);
        target.velocityModified = true;
    }

    private static void dealDamageInstance(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0.0F || !target.isAlive()) {
            return;
        }

        target.timeUntilRegen = 0;
        target.damage(source, amount);
    }

    private static void pruneExpiredLocks(long worldTime) {
        Iterator<Map.Entry<ArmorPierceKey, Long>> iterator = ARMOR_PIERCE_LOCKOUTS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= worldTime) {
                iterator.remove();
            }
        }
    }

    private record ArmorPierceKey(String worldKey, UUID targetId) {}
}
