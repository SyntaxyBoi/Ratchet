package moth.ratchet.item;

import moth.ratchet.mixin.LivingEntityArmorDamageInvoker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Rarity;
import net.minecraft.world.World;

public class OvercrankRatchetDriverItem extends AbstractRatchetWeaponItem {
    private static final String PENDING_CRIT_REWARD_TICK_KEY = "PendingCritRewardTick";
    private static final int CRIT_REWARD_DELAY_TICKS = 60;
    private static final float BASE_ARMOR_DURABILITY_DAMAGE_MULTIPLIER = 1.3F;
    private static final float ARMOR_DURABILITY_DAMAGE_MULTIPLIER_PER_CHARGE = 0.1F;

    private static final RatchetWeaponProfile PROFILE = new RatchetWeaponProfile(
            6.5D,
            -2.9D,
            8,
            10,
            0.6F,
            200,
            1.0F,
            0.0F,
            0.0F,
            4.0F,
            0.0F,
            0.45F,
            0.03F,
            0xE4A949,
            "tooltip.ratchet.overcrank_ratchet_driver"
    );

    public OvercrankRatchetDriverItem() {
        super(PROFILE, new Item.Settings().rarity(Rarity.UNCOMMON).fireproof());
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);

        if (world.isClient || !(entity instanceof PlayerEntity player)) {
            return;
        }

        long pendingTick = getPendingCritRewardTick(stack);
        if (pendingTick <= 0L || world.getTime() < pendingTick) {
            return;
        }

        clearPendingCritReward(stack);
        int currentCharge = getCurrentCharge(stack);
        if (currentCharge >= getMaxCharge(stack)) {
            return;
        }

        int newCharge = currentCharge + 1;
        setCurrentCharge(stack, newCharge);
        resetDecayTimer(stack, world.getTime());
        playChargeGainSound(world, player, newCharge, getMaxCharge(stack));
    }

    @Override
    protected void applyExtraPrimaryImpact(LivingEntity target, LivingEntity attacker, int charge, float directBonusDamage) {
        if (directBonusDamage <= 0.0F || !hasDamageableArmor(target)) {
            return;
        }

        float armorDamageAmount = directBonusDamage * getArmorDurabilityDamageMultiplier(charge);
        ((LivingEntityArmorDamageInvoker) target).ratchet$damageArmor(createDamageSource(attacker), armorDamageAmount);
    }

    public static void onCriticalHit(PlayerEntity player, ItemStack stack) {
        if (!(stack.getItem() instanceof OvercrankRatchetDriverItem) || player.getWorld().isClient) {
            return;
        }

        stack.getOrCreateNbt().putLong(PENDING_CRIT_REWARD_TICK_KEY, player.getWorld().getTime() + CRIT_REWARD_DELAY_TICKS);
    }

    public static void clearPendingCritCharge(PlayerEntity player) {
        clearPendingCritCharge(player.getMainHandStack());
        clearPendingCritCharge(player.getOffHandStack());
        for (ItemStack stack : player.getInventory().main) {
            clearPendingCritCharge(stack);
        }
    }

    private static void clearPendingCritCharge(ItemStack stack) {
        if (stack.getItem() instanceof OvercrankRatchetDriverItem) {
            NbtCompound nbt = stack.getNbt();
            if (nbt != null) {
                nbt.remove(PENDING_CRIT_REWARD_TICK_KEY);
            }
        }
    }

    private long getPendingCritRewardTick(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null ? 0L : nbt.getLong(PENDING_CRIT_REWARD_TICK_KEY);
    }

    private void clearPendingCritReward(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null) {
            nbt.remove(PENDING_CRIT_REWARD_TICK_KEY);
        }
    }

    private float getArmorDurabilityDamageMultiplier(int charge) {
        return BASE_ARMOR_DURABILITY_DAMAGE_MULTIPLIER + (ARMOR_DURABILITY_DAMAGE_MULTIPLIER_PER_CHARGE * getEffectCharge(charge));
    }

    private boolean hasDamageableArmor(LivingEntity target) {
        for (ItemStack armorStack : target.getArmorItems()) {
            if (!armorStack.isEmpty() && armorStack.isDamageable()) {
                return true;
            }
        }
        return false;
    }
}
