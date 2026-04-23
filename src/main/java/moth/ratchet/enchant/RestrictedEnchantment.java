package moth.ratchet.enchant;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.function.Predicate;

public class RestrictedEnchantment extends Enchantment {
    private final Predicate<ItemStack> acceptableItem;
    private final Predicate<Enchantment> compatibleEnchantment;

    public RestrictedEnchantment(Rarity weight, EnchantmentTarget target, Predicate<ItemStack> acceptableItem, EquipmentSlot... slotTypes) {
        this(weight, target, acceptableItem, enchantment -> true, slotTypes);
    }

    public RestrictedEnchantment(Rarity weight, EnchantmentTarget target, Predicate<ItemStack> acceptableItem,
                                 Predicate<Enchantment> compatibleEnchantment, EquipmentSlot... slotTypes) {
        super(weight, target, slotTypes);
        this.acceptableItem = acceptableItem;
        this.compatibleEnchantment = compatibleEnchantment;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        return this.acceptableItem.test(stack);
    }

    @Override
    protected boolean canAccept(Enchantment other) {
        return super.canAccept(other) && this.compatibleEnchantment.test(other);
    }
}
