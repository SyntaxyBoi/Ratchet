package moth.ratchet.enchant;

import moth.ratchet.RatchetMod;
import moth.ratchet.item.ModItems;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEnchantments {
    public static final Enchantment RAPID_FIRE = register("rapid_fire",
            new RestrictedEnchantment(
                    Enchantment.Rarity.RARE,
                    EnchantmentTarget.WEAPON,
                    stack -> stack.isOf(ModItems.LATCHET_REBOUNDER),
                    EquipmentSlot.MAINHAND,
                    EquipmentSlot.OFFHAND
            ));

    public static final Enchantment RAILPIERCER = register("railpiercer",
            new RestrictedEnchantment(
                    Enchantment.Rarity.RARE,
                    EnchantmentTarget.WEAPON,
                    stack -> stack.isOf(ModItems.LATCHET_REBOUNDER),
                    EquipmentSlot.MAINHAND,
                    EquipmentSlot.OFFHAND
            ));

    public static final Enchantment OBLITERATE = register("obliterate",
            new RestrictedEnchantment(
                    Enchantment.Rarity.RARE,
                    EnchantmentTarget.WEAPON,
                    stack -> stack.isOf(ModItems.RATCHET_DRIVER) || stack.isOf(ModItems.HEAVY_RATCHET_DRIVER),
                    ModEnchantments::canCombineWithObliterate,
                    EquipmentSlot.MAINHAND
            ));

    public static final Enchantment OVERCHARGE = register("overcharge",
            new RestrictedEnchantment(
                    Enchantment.Rarity.RARE,
                    EnchantmentTarget.WEAPON,
                    stack -> stack.isOf(ModItems.RATCHET_DRIVER)
                            || stack.isOf(ModItems.HEAVY_RATCHET_DRIVER)
                            || stack.isOf(ModItems.OVERCRANK_RATCHET_DRIVER),
                    ModEnchantments::canCombineWithOvercharge,
                    EquipmentSlot.MAINHAND
            ));

    public static final Enchantment INVERSION = register("inversion",
            new RestrictedEnchantment(
                    Enchantment.Rarity.RARE,
                    EnchantmentTarget.WEAPON,
                    stack -> stack.isOf(ModItems.OVERCRANK_RATCHET_DRIVER),
                    EquipmentSlot.MAINHAND
            ));

    private ModEnchantments() {}

    public static void init() {
    }

    public static boolean hasRapidFire(ItemStack stack) {
        return EnchantmentHelper.getLevel(RAPID_FIRE, stack) > 0;
    }

    public static boolean hasRailpiercer(ItemStack stack) {
        return EnchantmentHelper.getLevel(RAILPIERCER, stack) > 0;
    }

    public static boolean hasObliterate(ItemStack stack) {
        return EnchantmentHelper.getLevel(OBLITERATE, stack) > 0;
    }

    public static boolean hasOvercharge(ItemStack stack) {
        return EnchantmentHelper.getLevel(OVERCHARGE, stack) > 0;
    }

    public static boolean hasInversion(ItemStack stack) {
        return EnchantmentHelper.getLevel(INVERSION, stack) > 0;
    }

    private static boolean canCombineWithObliterate(Enchantment enchantment) {
        return enchantment != OVERCHARGE;
    }

    private static boolean canCombineWithOvercharge(Enchantment enchantment) {
        return enchantment != OBLITERATE;
    }

    private static Enchantment register(String path, Enchantment enchantment) {
        return Registry.register(Registries.ENCHANTMENT, RatchetMod.id(path), enchantment);
    }
}
