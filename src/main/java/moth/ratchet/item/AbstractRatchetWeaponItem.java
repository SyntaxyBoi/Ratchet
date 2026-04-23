package moth.ratchet.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import moth.ratchet.RatchetDamageSources;
import moth.ratchet.enchant.ModEnchantments;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public abstract class AbstractRatchetWeaponItem extends Item {
    public static final float CHARGE_DAMAGE_BLOCK_RATIO = 0.30F;
    private static final String CHARGE_KEY = "RatchetCharge";
    private static final String IDLE_START_TICK_KEY = "RatchetIdleStartTick";
    private static final String CHARGING_KEY = "RatchetCharging";
    private static final String CHARGE_SESSION_START_KEY = "RatchetChargeSessionStart";
    private static final int MAX_USE_TIME = 72000;
    private static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString("c07e4986-136f-46fc-a5ef-b6e0d4dd2f70");
    private static final UUID ATTACK_SPEED_MODIFIER_ID = UUID.fromString("87de5ae8-cab4-421c-83b1-c42fbe9f7a27");

    private final RatchetWeaponProfile profile;
    private final Multimap<EntityAttribute, EntityAttributeModifier> attributeModifiers;

    protected AbstractRatchetWeaponItem(RatchetWeaponProfile profile, Settings settings) {
        super(settings.maxCount(1));
        this.profile = profile;

        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> attributes = ImmutableMultimap.builder();
        attributes.put(
                EntityAttributes.GENERIC_ATTACK_DAMAGE,
                new EntityAttributeModifier(
                        ATTACK_DAMAGE_MODIFIER_ID,
                        "Weapon modifier",
                        profile.attackDamageModifier(),
                        EntityAttributeModifier.Operation.ADDITION
                )
        );
        attributes.put(
                EntityAttributes.GENERIC_ATTACK_SPEED,
                new EntityAttributeModifier(
                        ATTACK_SPEED_MODIFIER_ID,
                        "Weapon modifier",
                        profile.attackSpeedModifier(),
                        EntityAttributeModifier.Operation.ADDITION
                )
        );
        this.attributeModifiers = attributes.build();
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (hand != Hand.MAIN_HAND || user.getMainHandStack() != stack) {
            return TypedActionResult.fail(stack);
        }

        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return MAX_USE_TIME;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantability() {
        return 15;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world.isClient || !(user instanceof PlayerEntity player) || player.getMainHandStack() != stack) {
            return;
        }

        if (!wasCharging(stack)) {
            setCharging(stack, true);
            setChargeSessionStart(stack, getCurrentCharge(stack));
        }

        int currentCharge = getCurrentCharge(stack);
        if (currentCharge >= getMaxCharge(stack)) {
            return;
        }

        int usedTicks = getMaxUseTime(stack) - remainingUseTicks;
        int sessionStartCharge = getChargeSessionStart(stack);
        int nextCharge = currentCharge + 1;
        int nextThreshold = getChargeThresholdTicks(stack, sessionStartCharge, nextCharge);

        if (usedTicks >= nextThreshold) {
            int newCharge = currentCharge + 1;
            setCurrentCharge(stack, newCharge);
            setIdleStartTick(stack, world.getTime());

            playChargeGainSound(world, player, newCharge, getMaxCharge(stack));
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!world.isClient) {
            setCharging(stack, false);
            clearChargeSessionStart(stack);
            if (getCurrentCharge(stack) > 0) {
                setIdleStartTick(stack, world.getTime());
            } else {
                clearIdleStartTick(stack);
            }
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient || !(entity instanceof PlayerEntity player)) {
            return;
        }

        boolean activelyCharging = isActiveMainHandUse(player, stack);
        if (activelyCharging) {
            return;
        }

        int currentCharge = getCurrentCharge(stack);
        if (currentCharge <= 0) {
            setCharging(stack, false);
            clearChargeSessionStart(stack);
            clearIdleStartTick(stack);
            return;
        }

        if (wasCharging(stack)) {
            setCharging(stack, false);
            clearChargeSessionStart(stack);
            setIdleStartTick(stack, world.getTime());
            return;
        }

        long idleStartTick = getIdleStartTick(stack);
        if (idleStartTick <= 0L) {
            setIdleStartTick(stack, world.getTime());
            return;
        }

        if (world.getTime() - idleStartTick >= profile.decayIntervalTicks()) {
            int newCharge = currentCharge - 1;
            setCurrentCharge(stack, newCharge);
            if (newCharge > 0) {
                setIdleStartTick(stack, world.getTime());
            } else {
                clearIdleStartTick(stack);
            }
            playDecaySound(world, player);
        }
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.getWorld().isClient) {
            return true;
        }

        int charge = getCurrentCharge(stack);
        if (charge <= 0) {
            return true;
        }

        float directBonusDamage = getDirectBonusDamage(target, attacker, charge);
        applyDirectImpact(target, attacker, charge, directBonusDamage);
        applySecondaryEffects(target, attacker, charge, directBonusDamage);
        setCurrentCharge(stack, 0);
        setCharging(stack, false);
        clearChargeSessionStart(stack);
        clearIdleStartTick(stack);
        return true;
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return getCurrentCharge(stack) > 0;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round((getCurrentCharge(stack) / (float) getMaxCharge(stack)) * 13.0F);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return profile.itemBarColor();
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? this.attributeModifiers : super.getAttributeModifiers(slot);
    }

    @Override
    public boolean allowNbtUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.ratchet.charge", getCurrentCharge(stack), getMaxCharge(stack)).formatted(Formatting.GOLD));
        tooltip.add(Text.translatable(profile.flavorTooltipKey()).formatted(Formatting.GRAY));
    }

    public int getCurrentCharge(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) {
            return 0;
        }
        return Math.min(getMaxCharge(stack), Math.max(0, nbt.getInt(CHARGE_KEY)));
    }

    public static boolean isActivelyCharging(PlayerEntity player) {
        if (!player.isUsingItem() || player.getActiveHand() != Hand.MAIN_HAND) {
            return false;
        }

        ItemStack activeStack = player.getActiveItem();
        return !activeStack.isEmpty()
                && activeStack == player.getMainHandStack()
                && activeStack.getItem() instanceof AbstractRatchetWeaponItem;
    }

    protected void setCurrentCharge(ItemStack stack, int charge) {
        stack.getOrCreateNbt().putInt(CHARGE_KEY, Math.max(0, Math.min(getMaxCharge(stack), charge)));
    }

    protected RatchetWeaponProfile getProfile() {
        return profile;
    }

    protected int getMaxCharge(ItemStack stack) {
        return profile.maxCharge() + (ModEnchantments.hasOvercharge(stack) ? 2 : 0);
    }

    protected final int getEffectCharge(int charge) {
        return Math.min(profile.maxCharge(), Math.max(0, charge));
    }

    protected final float getEffectChargeProgress(int charge) {
        if (profile.maxCharge() <= 0) {
            return 0.0F;
        }

        return getEffectCharge(charge) / (float) profile.maxCharge();
    }

    protected void resetDecayTimer(ItemStack stack, long tick) {
        setIdleStartTick(stack, tick);
    }

    private long getIdleStartTick(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null ? 0L : nbt.getLong(IDLE_START_TICK_KEY);
    }

    private int getChargeSessionStart(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) {
            return getCurrentCharge(stack);
        }
        return Math.min(getMaxCharge(stack), Math.max(0, nbt.getInt(CHARGE_SESSION_START_KEY)));
    }

    private void setIdleStartTick(ItemStack stack, long tick) {
        stack.getOrCreateNbt().putLong(IDLE_START_TICK_KEY, Math.max(0L, tick));
    }

    private void setChargeSessionStart(ItemStack stack, int charge) {
        stack.getOrCreateNbt().putInt(CHARGE_SESSION_START_KEY, Math.max(0, Math.min(getMaxCharge(stack), charge)));
    }

    private void clearIdleStartTick(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null) {
            nbt.remove(IDLE_START_TICK_KEY);
        }
    }

    private void clearChargeSessionStart(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null) {
            nbt.remove(CHARGE_SESSION_START_KEY);
        }
    }

    private boolean wasCharging(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.getBoolean(CHARGING_KEY);
    }

    private void setCharging(ItemStack stack, boolean charging) {
        if (charging) {
            if (!wasCharging(stack)) {
                stack.getOrCreateNbt().putBoolean(CHARGING_KEY, true);
            }
            return;
        }

        NbtCompound nbt = stack.getNbt();
        if (nbt != null) {
            nbt.remove(CHARGING_KEY);
        }
    }

    private boolean isActiveMainHandUse(PlayerEntity player, ItemStack stack) {
        return player.isUsingItem()
                && player.getActiveHand() == Hand.MAIN_HAND
                && player.getMainHandStack() == stack
                && player.getActiveItem() == stack;
    }

    private int getChargeThresholdTicks(ItemStack stack, int sessionStartCharge, int targetCharge) {
        int maxCharge = getMaxCharge(stack);
        if (targetCharge <= sessionStartCharge) {
            return 0;
        }

        int totalBaseChargeTicks = maxCharge * profile.chargeIntervalTicks();
        double totalWeight = 0.0D;
        double cumulativeWeight = 0.0D;

        for (int chargeIndex = 1; chargeIndex <= maxCharge; chargeIndex++) {
            double stepWeight = getChargeStepWeight(chargeIndex, maxCharge);
            totalWeight += stepWeight;
            if (chargeIndex > sessionStartCharge && chargeIndex <= targetCharge) {
                cumulativeWeight += stepWeight;
            }
        }

        return (int) Math.round((cumulativeWeight / totalWeight) * totalBaseChargeTicks);
    }

    private double getChargeStepWeight(int chargeIndex, int maxCharge) {
        if (maxCharge <= 1) {
            return 1.0D;
        }

        double progress = (chargeIndex - 1.0D) / (maxCharge - 1.0D);
        return Math.max(0.1D, 1.0D + profile.chargeRampStrength() * (1.0D - progress));
    }

    protected final void playChargeGainSound(World world, PlayerEntity player, int newCharge, int maxCharge) {
        playSound(world, player, SoundEvents.BLOCK_METAL_PRESSURE_PLATE_CLICK_ON, 0.65F, 0.9F + (newCharge * 0.07F));
        if (newCharge >= maxCharge) {
            playSound(world, player, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, 0.9F, 1.25F);
        }
    }

    private float getDirectBonusDamage(LivingEntity target, LivingEntity attacker, int charge) {
        float baseBonusDamage = profile.bonusDirectDamagePerCharge() * charge;
        return modifyDirectBonusDamage(target, attacker, charge, baseBonusDamage);
    }

    private void applyDirectImpact(LivingEntity target, LivingEntity attacker, int charge, float directBonusDamage) {
        if (directBonusDamage > 0.0F && target.isAlive()) {
            applyBonusDirectDamage(target, attacker, charge, directBonusDamage);
        }

        float directKnockback = getDirectKnockback(target, attacker, charge);
        if (directKnockback > 0.0F && target.isAlive()) {
            Vec3d push = horizontalDirection(attacker.getPos(), target.getPos(), attacker.getRotationVec(1.0F))
                    .multiply(directKnockback);
            target.addVelocity(push.x, getDirectKnockbackLift(target, attacker, charge), push.z);
            target.velocityModified = true;
        }

        applyExtraPrimaryImpact(target, attacker, charge, directBonusDamage);
        applyOverchargeBacklash(attacker, charge, directBonusDamage);
    }

    private void applyBonusDirectDamage(LivingEntity target, LivingEntity attacker, int charge, float bonusDamage) {
        float armorPierceRatio = getArmorPierceRatio(charge);
        if (armorPierceRatio <= 0.0F || !(attacker.getWorld() instanceof ServerWorld serverWorld)) {
            dealDamageInstance(target, createDamageSource(attacker), bonusDamage);
            return;
        }

        float pierceDamage = bonusDamage * armorPierceRatio;
        float normalDamage = bonusDamage - pierceDamage;

        dealDamageInstance(target, createDamageSource(attacker), normalDamage);
        if (target.isAlive()) {
            dealDamageInstance(target, RatchetDamageSources.heavyRatchetPierce(serverWorld, attacker, attacker.getMainHandStack()), pierceDamage);
        }
    }

    private float getArmorPierceRatio(int charge) {
        if (profile.maxArmorPierceRatioAtFullCharge() <= 0.0F || profile.maxCharge() <= 0) {
            return 0.0F;
        }

        return Math.min(
                profile.maxArmorPierceRatioAtFullCharge(),
                profile.maxArmorPierceRatioAtFullCharge() * getEffectChargeProgress(charge)
        );
    }

    private void dealDamageInstance(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0.0F || !target.isAlive()) {
            return;
        }

        target.timeUntilRegen = 0;
        target.damage(source, amount);
    }

    private void applySecondaryEffects(LivingEntity primaryTarget, LivingEntity attacker, int charge, float directBonusDamage) {
        float radius = getAreaRadius(primaryTarget, attacker, charge, directBonusDamage);
        if (radius <= 0.0F) {
            return;
        }

        if (attacker.getWorld() instanceof ServerWorld serverWorld) {
            spawnGroundImpactParticles(serverWorld, primaryTarget, attacker, charge, directBonusDamage, radius);
        }

        List<LivingEntity> nearbyEntities = attacker.getWorld().getEntitiesByClass(
                LivingEntity.class,
                primaryTarget.getBoundingBox().expand(radius),
                entity -> isValidSecondaryTarget(entity, attacker, primaryTarget)
        );

        if (nearbyEntities.isEmpty()) {
            return;
        }

        for (LivingEntity secondaryTarget : nearbyEntities) {
            float splashDamage = getSplashDamage(secondaryTarget, primaryTarget, attacker, charge, directBonusDamage, radius);
            if (splashDamage > 0.0F) {
                secondaryTarget.damage(createDamageSource(attacker), splashDamage);
            }

            float shockwaveForce = getShockwaveForce(secondaryTarget, primaryTarget, attacker, charge, directBonusDamage, radius);
            if (shockwaveForce > 0.0F) {
                Vec3d push = horizontalDirection(primaryTarget.getPos(), secondaryTarget.getPos(), attacker.getRotationVec(1.0F))
                        .multiply(shockwaveForce);
                double lift = getShockwaveLift(secondaryTarget, primaryTarget, attacker, charge, directBonusDamage, radius);
                secondaryTarget.addVelocity(push.x, lift, push.z);
                secondaryTarget.velocityModified = true;
            }
        }
    }

    private void applyOverchargeBacklash(LivingEntity attacker, int charge, float directBonusDamage) {
        ItemStack stack = attacker.getMainHandStack();
        if (!ModEnchantments.hasOvercharge(stack) || charge <= profile.maxCharge() || directBonusDamage <= 0.0F) {
            return;
        }

        attacker.damage(attacker.getDamageSources().magic(), directBonusDamage * 0.2F);
    }

    private boolean isValidSecondaryTarget(LivingEntity candidate, LivingEntity attacker, LivingEntity primaryTarget) {
        return candidate.isAlive()
                && candidate != attacker
                && candidate != primaryTarget
                && !(candidate instanceof ArmorStandEntity)
                && !candidate.isSpectator()
                && !candidate.isInvulnerable()
                && !candidate.isTeammate(attacker)
                && !attacker.isTeammate(candidate);
    }

    protected final DamageSource createDamageSource(LivingEntity attacker) {
        ItemStack stack = attacker.getMainHandStack();
        if (attacker.getWorld() instanceof ServerWorld serverWorld) {
            if (stack.getItem() instanceof RatchetDriverItem) {
                return RatchetDamageSources.ratchetDriver(
                        serverWorld,
                        attacker,
                        stack,
                        ModEnchantments.hasObliterate(stack),
                        ModEnchantments.hasOvercharge(stack)
                );
            }
            if (stack.getItem() instanceof HeavyRatchetDriverItem) {
                return RatchetDamageSources.heavyRatchetDriver(
                        serverWorld,
                        attacker,
                        stack,
                        ModEnchantments.hasObliterate(stack),
                        ModEnchantments.hasOvercharge(stack)
                );
            }
            if (stack.getItem() instanceof OvercrankRatchetDriverItem) {
                return RatchetDamageSources.overcrankRatchetDriver(
                        serverWorld,
                        attacker,
                        stack,
                        ModEnchantments.hasInversion(stack),
                        ModEnchantments.hasOvercharge(stack)
                );
            }
        }

        if (attacker instanceof PlayerEntity player) {
            return attacker.getDamageSources().playerAttack(player);
        }
        return attacker.getDamageSources().mobAttack(attacker);
    }

    private Vec3d horizontalDirection(Vec3d source, Vec3d target, Vec3d fallback) {
        Vec3d horizontal = new Vec3d(target.x - source.x, 0.0D, target.z - source.z);
        if (horizontal.lengthSquared() < 1.0E-4D) {
            Vec3d fallbackHorizontal = new Vec3d(fallback.x, 0.0D, fallback.z);
            if (fallbackHorizontal.lengthSquared() < 1.0E-4D) {
                return new Vec3d(1.0D, 0.0D, 0.0D);
            }
            return fallbackHorizontal.normalize();
        }
        return horizontal.normalize();
    }

    private void playDecaySound(World world, PlayerEntity player) {
        playSound(world, player, SoundEvents.BLOCK_METAL_PRESSURE_PLATE_CLICK_OFF, 0.45F, 0.65F);
    }

    private void playSound(World world, PlayerEntity player, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
    }

    protected float modifyDirectBonusDamage(LivingEntity target, LivingEntity attacker, int charge, float baseBonusDamage) {
        ItemStack stack = attacker.getMainHandStack();
        float modifiedDamage = baseBonusDamage;
        if (ModEnchantments.hasObliterate(stack)) {
            modifiedDamage *= 0.5F;
        }
        if (ModEnchantments.hasInversion(stack)) {
            modifiedDamage *= 1.35F;
        }
        return modifiedDamage;
    }

    protected float getDirectKnockback(LivingEntity target, LivingEntity attacker, int charge) {
        return profile.directKnockbackPerCharge() * getEffectCharge(charge);
    }

    protected double getDirectKnockbackLift(LivingEntity target, LivingEntity attacker, int charge) {
        return 0.05D;
    }

    protected void applyExtraPrimaryImpact(LivingEntity target, LivingEntity attacker, int charge, float directBonusDamage) {
    }

    protected float getAreaRadius(LivingEntity primaryTarget, LivingEntity attacker, int charge, float directBonusDamage) {
        return profile.areaRadius();
    }

    protected float getSplashDamage(LivingEntity secondaryTarget, LivingEntity primaryTarget, LivingEntity attacker,
                                    int charge, float directBonusDamage, float radius) {
        float splashDamage = profile.splashDamagePerCharge() * charge;
        ItemStack stack = attacker.getMainHandStack();
        if (ModEnchantments.hasObliterate(stack)) {
            splashDamage *= 2.0F;
        }
        if (ModEnchantments.hasInversion(stack)) {
            splashDamage *= 1.35F;
        }
        return splashDamage;
    }

    protected float getShockwaveForce(LivingEntity secondaryTarget, LivingEntity primaryTarget, LivingEntity attacker,
                                      int charge, float directBonusDamage, float radius) {
        float force = profile.shockwaveForcePerCharge() * getEffectCharge(charge);
        ItemStack stack = attacker.getMainHandStack();
        if (ModEnchantments.hasObliterate(stack)) {
            force *= 2.4F;
        }
        if (ModEnchantments.hasInversion(stack)) {
            force *= -1.35F;
        }
        return force;
    }

    protected double getShockwaveLift(LivingEntity secondaryTarget, LivingEntity primaryTarget, LivingEntity attacker,
                                      int charge, float directBonusDamage, float radius) {
        return profile.shockwaveLiftPerCharge() * getEffectCharge(charge);
    }

    protected boolean usesGroundImpactParticles() {
        return false;
    }

    protected float getParticleReferenceDamage() {
        return profile.bonusDirectDamagePerCharge() * profile.maxCharge();
    }

    protected float getParticleRadius(LivingEntity primaryTarget, LivingEntity attacker, int charge, float directBonusDamage,
                                      float radius) {
        return radius;
    }

    protected void spawnGroundImpactParticles(ServerWorld world, LivingEntity primaryTarget, LivingEntity attacker,
                                              int charge, float directBonusDamage, float radius) {
        if (!usesGroundImpactParticles()) {
            return;
        }

        float referenceDamage = Math.max(0.5F, getParticleReferenceDamage());
        float fullness = MathHelper.clamp(directBonusDamage / (referenceDamage * 0.9F), 0.0F, 1.0F);
        float density = 0.25F + (0.75F * fullness);
        float particleRadius = Math.max(0.75F, getParticleRadius(primaryTarget, attacker, charge, directBonusDamage, radius));
        int particleCount = Math.max(16, Math.round((36.0F * particleRadius) * density));

        BlockPos basePos = primaryTarget.getSteppingPos();
        if (world.getBlockState(basePos).isAir()) {
            basePos = primaryTarget.getBlockPos().down();
        }

        if (world.getBlockState(basePos).isAir()) {
            return;
        }

        BlockStateParticleEffect particle = new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(basePos));
        Vec3d center = primaryTarget.getPos();

        for (int i = 0; i < particleCount; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(world.random.nextDouble()) * particleRadius;
            double x = center.x + Math.cos(angle) * distance;
            double z = center.z + Math.sin(angle) * distance;
            double y = basePos.getY() + 1.02D;
            double vx = (world.random.nextDouble() - 0.5D) * 0.12D;
            double vy = 0.16D + (world.random.nextDouble() * 0.26D);
            double vz = (world.random.nextDouble() - 0.5D) * 0.12D;

            world.spawnParticles(particle, x, y, z, 0, vx, vy, vz, 0.0D);
        }
    }
}
