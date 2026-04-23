package moth.ratchet;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class RatchetDamageSources {
    public static final RegistryKey<DamageType> RATCHET_DRIVER_KEY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, RatchetMod.id("ratchet_driver"));
    public static final RegistryKey<DamageType> RATCHET_DRIVER_OBLITERATE_KEY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, RatchetMod.id("ratchet_driver_obliterate"));
    public static final RegistryKey<DamageType> RATCHET_DRIVER_OVERCHARGE_KEY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, RatchetMod.id("ratchet_driver_overcharge"));
    public static final RegistryKey<DamageType> HEAVY_RATCHET_DRIVER_KEY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, RatchetMod.id("heavy_ratchet_driver"));
    public static final RegistryKey<DamageType> HEAVY_RATCHET_DRIVER_OBLITERATE_KEY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, RatchetMod.id("heavy_ratchet_driver_obliterate"));
    public static final RegistryKey<DamageType> HEAVY_RATCHET_DRIVER_OVERCHARGE_KEY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, RatchetMod.id("heavy_ratchet_driver_overcharge"));
    public static final RegistryKey<DamageType> OVERCRANK_RATCHET_DRIVER_KEY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, RatchetMod.id("overcrank_ratchet_driver"));
    public static final RegistryKey<DamageType> OVERCRANK_RATCHET_DRIVER_OVERCHARGE_KEY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, RatchetMod.id("overcrank_ratchet_driver_overcharge"));
    public static final RegistryKey<DamageType> OVERCRANK_RATCHET_DRIVER_INVERSION_KEY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, RatchetMod.id("overcrank_ratchet_driver_inversion"));
    public static final RegistryKey<DamageType> OVERCRANK_RATCHET_DRIVER_OVERCHARGE_INVERSION_KEY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, RatchetMod.id("overcrank_ratchet_driver_overcharge_inversion"));
    public static final RegistryKey<DamageType> LATCHET_REBOUNDER_KEY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, RatchetMod.id("latchet_rebounder"));
    public static final RegistryKey<DamageType> LATCHET_REBOUNDER_RAPID_FIRE_KEY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, RatchetMod.id("latchet_rebounder_rapid_fire"));
    public static final RegistryKey<DamageType> LATCHET_REBOUNDER_RAILPIERCER_KEY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, RatchetMod.id("latchet_rebounder_railpiercer"));
    public static final RegistryKey<DamageType> HEAVY_RATCHET_PIERCE_KEY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, RatchetMod.id("heavy_ratchet_pierce"));
    public static final RegistryKey<DamageType> REBOUNDER_PIERCE_KEY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, RatchetMod.id("rebounder_pierce"));

    private RatchetDamageSources() {}

    public static DamageSource ratchetDriver(ServerWorld world, LivingEntity attacker, ItemStack weaponStack, boolean obliterate, boolean overcharge) {
        return createItemDamageSource(
                world,
                obliterate ? RATCHET_DRIVER_OBLITERATE_KEY : (overcharge ? RATCHET_DRIVER_OVERCHARGE_KEY : RATCHET_DRIVER_KEY),
                attacker,
                attacker,
                weaponStack,
                attacker instanceof PlayerEntity player ? world.getDamageSources().playerAttack(player) : world.getDamageSources().mobAttack(attacker)
        );
    }

    public static DamageSource heavyRatchetDriver(ServerWorld world, LivingEntity attacker, ItemStack weaponStack, boolean obliterate, boolean overcharge) {
        return createItemDamageSource(
                world,
                obliterate ? HEAVY_RATCHET_DRIVER_OBLITERATE_KEY : (overcharge ? HEAVY_RATCHET_DRIVER_OVERCHARGE_KEY : HEAVY_RATCHET_DRIVER_KEY),
                attacker,
                attacker,
                weaponStack,
                attacker instanceof PlayerEntity player ? world.getDamageSources().playerAttack(player) : world.getDamageSources().mobAttack(attacker)
        );
    }

    public static DamageSource overcrankRatchetDriver(ServerWorld world, LivingEntity attacker, ItemStack weaponStack, boolean inversion, boolean overcharge) {
        RegistryKey<DamageType> key;
        if (inversion && overcharge) {
            key = OVERCRANK_RATCHET_DRIVER_OVERCHARGE_INVERSION_KEY;
        } else if (inversion) {
            key = OVERCRANK_RATCHET_DRIVER_INVERSION_KEY;
        } else if (overcharge) {
            key = OVERCRANK_RATCHET_DRIVER_OVERCHARGE_KEY;
        } else {
            key = OVERCRANK_RATCHET_DRIVER_KEY;
        }

        return createItemDamageSource(
                world,
                key,
                attacker,
                attacker,
                weaponStack,
                attacker instanceof PlayerEntity player ? world.getDamageSources().playerAttack(player) : world.getDamageSources().mobAttack(attacker)
        );
    }

    public static DamageSource heavyRatchetPierce(ServerWorld world, LivingEntity attacker, ItemStack weaponStack) {
        DamageSource fallback = attacker instanceof PlayerEntity player
                ? world.getDamageSources().playerAttack(player)
                : world.getDamageSources().mobAttack(attacker);
        return createItemDamageSource(world, HEAVY_RATCHET_PIERCE_KEY, attacker, attacker, weaponStack, fallback);
    }

    public static DamageSource rebounderShot(ServerWorld world, Entity source, @Nullable LivingEntity attacker, ItemStack weaponStack,
                                             boolean rapidFire, boolean railpiercer) {
        RegistryKey<DamageType> key = railpiercer
                ? LATCHET_REBOUNDER_RAILPIERCER_KEY
                : (rapidFire ? LATCHET_REBOUNDER_RAPID_FIRE_KEY : LATCHET_REBOUNDER_KEY);
        DamageSource fallback = world.getDamageSources().thrown(source, attacker);
        return createItemDamageSource(world, key, source, attacker, weaponStack, fallback);
    }

    public static DamageSource rebounderPierce(ServerWorld world, Entity source, @Nullable LivingEntity attacker, ItemStack weaponStack) {
        return createItemDamageSource(world, REBOUNDER_PIERCE_KEY, source, attacker, weaponStack, world.getDamageSources().thrown(source, attacker));
    }

    private static DamageSource createItemDamageSource(ServerWorld world, RegistryKey<DamageType> key, Entity source,
                                                       @Nullable LivingEntity attacker, ItemStack weaponStack, DamageSource fallback) {
        RegistryEntry<DamageType> entry = getDamageTypeEntry(world, key);
        if (entry == null) {
            return fallback;
        }

        return new RatchetItemDamageSource(entry, source, attacker, weaponStack);
    }

    private static RegistryEntry<DamageType> getDamageTypeEntry(ServerWorld world, RegistryKey<DamageType> key) {
        return world.getRegistryManager()
                .get(RegistryKeys.DAMAGE_TYPE)
                .getEntry(key)
                .orElse(null);
    }

    private static final class RatchetItemDamageSource extends DamageSource {
        private final ItemStack weaponStack;

        private RatchetItemDamageSource(RegistryEntry<DamageType> type, Entity source, @Nullable LivingEntity attacker, ItemStack weaponStack) {
            super(type, source, attacker);
            this.weaponStack = weaponStack == null ? ItemStack.EMPTY : weaponStack.copyWithCount(1);
        }

        @Override
        public Text getDeathMessage(LivingEntity killed) {
            String key = "death.attack." + this.getType().msgId();
            Entity attacker = this.getAttacker();
            if (attacker != null) {
                return Text.translatable(key + ".player", killed.getDisplayName(), attacker.getDisplayName(), getWeaponName());
            }

            return Text.translatable(key, killed.getDisplayName(), Text.empty(), getWeaponName());
        }

        private Text getWeaponName() {
            return this.weaponStack.isEmpty() ? Text.empty() : Objects.requireNonNullElse(this.weaponStack.toHoverableText(), Text.empty());
        }
    }
}
