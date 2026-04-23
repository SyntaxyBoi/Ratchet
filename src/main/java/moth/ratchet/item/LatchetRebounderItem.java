package moth.ratchet.item;

import moth.ratchet.RatchetDamageSources;
import moth.ratchet.RebounderVisualState;
import moth.ratchet.combat.RebounderCollisionHelper;
import moth.ratchet.combat.RebounderDamageHelper;
import moth.ratchet.enchant.ModEnchantments;
import moth.ratchet.network.RatchetNetworking;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BellBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CakeBlock;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.RepeaterBlock;
import net.minecraft.block.TrapdoorBlock;

public class LatchetRebounderItem extends Item {
    private static final String CHARGE_KEY = "LatchetCharge";
    private static final String IDLE_START_TICK_KEY = "LatchetIdleStartTick";
    private static final String NEXT_FIRE_TICK_KEY = "LatchetNextFireTick";
    private static final String VISIBLE_COOLDOWN_END_TICK_KEY = "LatchetVisibleCooldownEndTick";
    private static final String VISIBLE_COOLDOWN_DURATION_KEY = "LatchetVisibleCooldownDuration";
    private static final String DISPLAY_TICK_KEY = "LatchetDisplayTick";
    private static final String RAPID_FIRING_TICKS_KEY = "LatchetRapidFiringTicks";
    private static final String RAPID_INTERVAL_CARRY_KEY = "LatchetRapidIntervalCarry";

    private static final int MAX_CHARGE = 5;
    private static final int MAX_USE_TIME = 72000;
    private static final int CHARGE_INTERVAL_TICKS = 16;
    private static final int DECAY_INTERVAL_TICKS = 600;
    private static final int BASE_COOLDOWN_TICKS = 30;
    private static final int COOLDOWN_PER_CHARGE_TICKS = 5;
    private static final int RAPID_RELEASE_COOLDOWN_TICKS = 200;
    private static final int RAPID_SPEED_RAMP_TICKS = 100;
    private static final int RAPID_FULL_SPREAD_TICKS = 300;
    private static final int RAPID_MAX_FIRING_TICKS = 900;
    private static final double LOADED_BURST_YAW_STEP_DEGREES = 4.0D;
    private static final double LOADED_BURST_PITCH_STEP_DEGREES = 1.1D;
    private static final double HITSCAN_RANGE = 48.0D;
    private static final double INTERACT_BLOCK_RANGE = 5.0D;
    private static final double HITSCAN_ENTITY_HIT_RADIUS = 0.35D;
    private static final double HAND_FORWARD_OFFSET = 0.68D;
    private static final double HAND_SIDE_OFFSET = 0.18D;
    private static final double HAND_DROP_OFFSET = 0.18D;
    private static final double RAIL_BLOCK_SKIP_DISTANCE = 1.05D;
    private static final double RAIL_ENTITY_SKIP_DISTANCE = 0.45D;
    private static final float RAPID_MIN_RATE = 2.0F;
    private static final float RAPID_MAX_RATE = 8.0F;
    private static final float BASE_DAMAGE = 8.0F;
    private static final float RAPID_FIRE_DAMAGE = 1.0F;
    private static final float RAILPIERCER_DAMAGE_MULTIPLIER = 1.8F;
    private static final double HITSCAN_KNOCKBACK_HORIZONTAL = 0.56D;
    private static final double HITSCAN_KNOCKBACK_VERTICAL = 0.08D;
    private static final double RAPID_KNOCKBACK_HORIZONTAL = 0.0D;
    private static final double RAPID_KNOCKBACK_VERTICAL = 0.0D;
    private static final int ITEM_BAR_COLOR = 0xF0C63C;
    private static final int COOLDOWN_BAR_COLOR = 0xD96A28;
    private static final int RAILPIERCER_BAR_COLOR = 0xA05BFF;
    private static final int RAILPIERCER_COOLDOWN_BAR_COLOR = 0x6D39D6;
    private static final int DEFAULT_SHOT_COLOR = 0xFFD654;
    private static final int RAILPIERCER_SHOT_COLOR = 0xB76DFF;

    public LatchetRebounderItem() {
        super(new Item.Settings().maxCount(1).rarity(Rarity.RARE));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantability() {
        return 16;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!usesStoredCharge(stack)) {
            return TypedActionResult.fail(stack);
        }

        if (isCoolingDown(stack, world.getTime()) || getCurrentCharge(stack) >= getMaxCharge(stack)) {
            return TypedActionResult.fail(stack);
        }

        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return usesStoredCharge(stack) ? UseAction.BOW : UseAction.NONE;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return MAX_USE_TIME;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world.isClient || !(user instanceof PlayerEntity player) || !usesStoredCharge(stack) || isCoolingDown(stack, world.getTime())) {
            return;
        }

        int currentCharge = getCurrentCharge(stack);
        if (currentCharge >= getMaxCharge(stack)) {
            return;
        }

        int usedTicks = getMaxUseTime(stack) - remainingUseTicks;
        if (usedTicks > 0 && usedTicks % CHARGE_INTERVAL_TICKS == 0) {
            int newCharge = Math.min(getMaxCharge(stack), currentCharge + 1);
            if (newCharge != currentCharge) {
                setCurrentCharge(stack, newCharge);
                setIdleStartTick(stack, world.getTime());
                playChargeGainSound(world, player, newCharge);
            }
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!world.isClient && usesStoredCharge(stack)) {
            if (getCurrentCharge(stack) > 0) {
                setIdleStartTick(stack, world.getTime());
            } else {
                clearIdleStartTick(stack);
            }
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        long now = world.getTime();
        clearVisibleCooldown(stack, now);

        if (world.isClient || !(entity instanceof PlayerEntity player)) {
            return;
        }

        if (!usesStoredCharge(stack)) {
            clearChargeState(stack);
            return;
        }

        if (isActivelyCharging(player, stack)) {
            return;
        }

        int currentCharge = getCurrentCharge(stack);
        if (currentCharge <= 0) {
            clearIdleStartTick(stack);
            return;
        }

        long idleStartTick = getIdleStartTick(stack);
        if (idleStartTick <= 0L) {
            setIdleStartTick(stack, now);
            return;
        }

        if ((now - idleStartTick) >= DECAY_INTERVAL_TICKS) {
            int newCharge = currentCharge - 1;
            setCurrentCharge(stack, newCharge);
            if (newCharge > 0) {
                setIdleStartTick(stack, now);
            } else {
                clearIdleStartTick(stack);
            }
            playDecaySound(world, player);
        }
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return getVisibleCooldownRemaining(stack, stack.getNbt()) > 0L
                || (usesStoredCharge(stack) && getCurrentCharge(stack) > 0);
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        long remainingCooldown = getVisibleCooldownRemaining(stack, nbt);
        if (remainingCooldown > 0L) {
            int duration = Math.max(1, getVisibleCooldownDuration(stack));
            return Math.max(1, Math.round((remainingCooldown / (float) duration) * 13.0F));
        }

        if (!usesStoredCharge(stack)) {
            return 0;
        }

        return Math.round((getCurrentCharge(stack) / (float) getMaxCharge(stack)) * 13.0F);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        boolean railpiercer = ModEnchantments.hasRailpiercer(stack);
        if (getVisibleCooldownRemaining(stack, stack.getNbt()) > 0L) {
            return railpiercer ? RAILPIERCER_COOLDOWN_BAR_COLOR : COOLDOWN_BAR_COLOR;
        }
        return railpiercer ? RAILPIERCER_BAR_COLOR : ITEM_BAR_COLOR;
    }

    @Override
    public boolean allowNbtUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        if (usesStoredCharge(stack)) {
            tooltip.add(Text.translatable("tooltip.ratchet.charge", getCurrentCharge(stack), getMaxCharge(stack)).formatted(Formatting.GOLD));
        } else if (ModEnchantments.hasRapidFire(stack)) {
            tooltip.add(Text.translatable("tooltip.ratchet.latchet_rebounder_rapid").formatted(Formatting.GOLD));
        }

        if (ModEnchantments.hasRailpiercer(stack)) {
            tooltip.add(Text.translatable("tooltip.ratchet.latchet_rebounder_railpiercer").formatted(Formatting.LIGHT_PURPLE));
        }

        tooltip.add(Text.translatable("tooltip.ratchet.latchet_rebounder").formatted(Formatting.GRAY));
    }

    public void tryFire(ServerPlayerEntity player, Hand hand) {
        if (player.isSpectator() || !player.isAlive() || player.getWorld().isClient) {
            return;
        }

        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof LatchetRebounderItem) || !(player.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        long now = serverWorld.getTime();
        if (isCoolingDown(stack, now)) {
            return;
        }

        boolean rapidFire = ModEnchantments.hasRapidFire(stack);
        if (!rapidFire) {
            if (hand != Hand.MAIN_HAND || (usesStoredCharge(stack) && getCurrentCharge(stack) <= 0)) {
                return;
            }
        } else if (isLookingAtInteractableBlock(serverWorld, player, raycastInteractableBlock(serverWorld, player))) {
            return;
        }

        FireSolution fireSolution = createFireSolution(serverWorld, player, hand, HITSCAN_RANGE);
        boolean railpiercer = ModEnchantments.hasRailpiercer(stack);
        if (rapidFire) {
            fireRapidShot(serverWorld, player, hand, stack, fireSolution, railpiercer, now);
        } else {
            fireLoadedShot(serverWorld, player, hand, stack, fireSolution, railpiercer, now);
        }
    }

    public void stopFiring(ServerPlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof LatchetRebounderItem) || !ModEnchantments.hasRapidFire(stack)) {
            return;
        }

        int rapidTicks = getRapidFiringTicks(stack);
        if (rapidTicks <= 0 || !(player.getWorld() instanceof ServerWorld world)) {
            return;
        }

        beginRapidCooldown(stack, world.getTime());
    }

    public int getCurrentCharge(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) {
            return 0;
        }
        return Math.max(0, Math.min(getMaxCharge(stack), nbt.getInt(CHARGE_KEY)));
    }

    public static void loseHeldCharge(PlayerEntity player) {
        long now = player.getWorld().getTime();
        reduceCharge(player.getMainHandStack(), now);
        reduceCharge(player.getOffHandStack(), now);
    }

    private static void reduceCharge(ItemStack stack, long worldTime) {
        if (!(stack.getItem() instanceof LatchetRebounderItem item) || !item.usesStoredCharge(stack)) {
            return;
        }

        int currentCharge = item.getCurrentCharge(stack);
        if (currentCharge <= 0) {
            return;
        }

        item.setCurrentCharge(stack, currentCharge - 1);
        if (item.getCurrentCharge(stack) > 0) {
            item.setIdleStartTick(stack, worldTime);
        } else {
            item.clearIdleStartTick(stack);
        }
    }

    private void fireRapidShot(ServerWorld world, ServerPlayerEntity player, Hand hand, ItemStack stack,
                               FireSolution fireSolution, boolean railpiercer, long now) {
        int rapidTicks = getRapidFiringTicks(stack);
        if (rapidTicks >= RAPID_MAX_FIRING_TICKS) {
            beginRapidCooldown(stack, now);
            return;
        }

        double spreadProgress = MathHelper.clamp(rapidTicks / (double) RAPID_FULL_SPREAD_TICKS, 0.0D, 1.0D);
        double yawSpread = MathHelper.lerp((float) spreadProgress, 0.2F, 24.0F);
        double pitchSpread = MathHelper.lerp((float) spreadProgress, 0.15F, 18.0F);
        Vec3d shotDirection = applySpread(
                fireSolution.direction(),
                (player.getRandom().nextDouble() - 0.5D) * yawSpread,
                (player.getRandom().nextDouble() - 0.5D) * pitchSpread
        );

        fireHitscanLine(
                world,
                player,
                hand,
                fireSolution.start(),
                shotDirection,
                RAPID_FIRE_DAMAGE * (railpiercer ? RAILPIERCER_DAMAGE_MULTIPLIER : 1.0F),
                railpiercer,
                true
        );

        int nextInterval = getRapidFireIntervalTicks(stack, rapidTicks);
        int updatedRapidTicks = rapidTicks + nextInterval;
        if (updatedRapidTicks >= RAPID_MAX_FIRING_TICKS) {
            beginRapidCooldown(stack, now);
        } else {
            setRapidFiringTicks(stack, updatedRapidTicks);
            setNextFireTick(stack, now + nextInterval);
            clearVisibleCooldown(stack, now);
        }

        if ((rapidTicks & 1) == 0) {
            playFireSound(world, player, rapidFireVolumeScale(rapidTicks), railpiercer, 0);
        }
    }

    private void fireLoadedShot(ServerWorld world, ServerPlayerEntity player, Hand hand, ItemStack stack,
                                FireSolution fireSolution, boolean railpiercer, long now) {
        int projectileCount = railpiercer ? 1 : Math.max(1, getCurrentCharge(stack));
        float damage = BASE_DAMAGE * (railpiercer ? RAILPIERCER_DAMAGE_MULTIPLIER : 1.0F);
        fireLoadedBurst(world, player, hand, fireSolution.start(), fireSolution.direction(), damage, railpiercer, projectileCount);

        clearChargeState(stack);
        setRapidFiringTicks(stack, 0);

        int cooldown = railpiercer
                ? (BASE_COOLDOWN_TICKS + COOLDOWN_PER_CHARGE_TICKS) * 4
                : BASE_COOLDOWN_TICKS + (COOLDOWN_PER_CHARGE_TICKS * projectileCount);
        long cooldownEnd = now + cooldown;
        setNextFireTick(stack, cooldownEnd);
        setVisibleCooldown(stack, cooldownEnd, cooldown);
        playFireSound(world, player, 1.0F, railpiercer, projectileCount);
    }

    private void fireLoadedBurst(ServerWorld world, ServerPlayerEntity player, Hand hand, Vec3d start, Vec3d direction,
                                 float damage, boolean railpiercer, int projectileCount) {
        if (projectileCount <= 1) {
            fireHitscanLine(world, player, hand, start, direction, damage, railpiercer, false);
            return;
        }

        double centerIndex = (projectileCount - 1) / 2.0D;
        for (int projectileIndex = 0; projectileIndex < projectileCount; projectileIndex++) {
            double centeredIndex = projectileIndex - centerIndex;
            double yawOffset = centeredIndex * LOADED_BURST_YAW_STEP_DEGREES;
            double pitchOffset = projectileCount >= 4 ? Math.signum(centeredIndex) * LOADED_BURST_PITCH_STEP_DEGREES : 0.0D;
            Vec3d shotDirection = applySpread(direction, yawOffset, pitchOffset);
            fireHitscanLine(world, player, hand, start, shotDirection, damage, railpiercer, false);
        }
    }

    private void fireHitscanLine(ServerWorld world, ServerPlayerEntity player, Hand hand, Vec3d start, Vec3d direction,
                                 float damage, boolean railpiercer, boolean rapidFire) {
        Vec3d normalizedDirection = normalizedOrFallback(direction, player.getRotationVec(1.0F));
        int traceColor = railpiercer ? RAILPIERCER_SHOT_COLOR : DEFAULT_SHOT_COLOR;
        double remainingRange = HITSCAN_RANGE;
        Vec3d currentStart = start;
        double traversedDistance = 0.0D;
        int remainingEntityPierces = railpiercer ? 1 : 0;
        int remainingBlockPierces = railpiercer ? 1 : 0;
        Set<Integer> ignoredEntities = new HashSet<>();

        while (remainingRange > 0.15D) {
            Vec3d maxEnd = currentStart.add(normalizedDirection.multiply(remainingRange));
            CollisionResult collision = findCollision(world, player, currentStart, maxEnd, ignoredEntities);

            if (collision == null) {
                RatchetNetworking.sendHitscanTrace(world, currentStart, maxEnd, traceColor);
                return;
            }

            RatchetNetworking.sendHitscanTrace(world, currentStart, collision.position(), traceColor);

            if (collision.entityHit() != null && collision.entityHit().getEntity() instanceof LivingEntity livingTarget) {
                double impactDistance = traversedDistance + currentStart.distanceTo(collision.position());
                float appliedDamage = rapidFire
                        ? damage * getRapidFireDistanceMultiplier(impactDistance) * getRapidFireArmorMultiplier(livingTarget)
                        : damage;
                ItemStack weaponStack = player.getStackInHand(hand);
                RebounderDamageHelper.applyPiercingDamage(
                        world,
                        livingTarget,
                        player,
                        player,
                        weaponStack,
                        RatchetDamageSources.rebounderShot(world, player, player, weaponStack, rapidFire, railpiercer),
                        appliedDamage
                );

                double knockbackHorizontal = rapidFire ? RAPID_KNOCKBACK_HORIZONTAL : HITSCAN_KNOCKBACK_HORIZONTAL;
                double knockbackVertical = rapidFire ? RAPID_KNOCKBACK_VERTICAL : HITSCAN_KNOCKBACK_VERTICAL;
                RebounderDamageHelper.applyKnockbackImpulse(livingTarget, normalizedDirection, knockbackHorizontal, knockbackVertical);

                RatchetNetworking.sendImpactBurst(
                        world,
                        collision.position(),
                        approximateImpactNormal(livingTarget, collision.position(), normalizedDirection.multiply(-1.0D)),
                        false,
                        traceColor
                );
                playImpactHitSound(world, collision.position());
                ignoredEntities.add(livingTarget.getId());

                double traveled = currentStart.distanceTo(collision.position());
                if (remainingEntityPierces > 0) {
                    remainingEntityPierces--;
                    traversedDistance += traveled + RAIL_ENTITY_SKIP_DISTANCE;
                    remainingRange -= traveled + RAIL_ENTITY_SKIP_DISTANCE;
                    currentStart = collision.position().add(normalizedDirection.multiply(RAIL_ENTITY_SKIP_DISTANCE));
                    continue;
                }
                return;
            }

            if (collision.blockHit() != null) {
                RatchetNetworking.sendImpactBurst(
                        world,
                        collision.position(),
                        Vec3d.of(collision.blockHit().getSide().getVector()),
                        false,
                        traceColor
                );
                playImpactBlockSound(world, collision.position());

                double traveled = currentStart.distanceTo(collision.position());
                if (remainingBlockPierces > 0) {
                    remainingBlockPierces--;
                    traversedDistance += traveled + RAIL_BLOCK_SKIP_DISTANCE;
                    remainingRange -= traveled + RAIL_BLOCK_SKIP_DISTANCE;
                    currentStart = collision.position().add(normalizedDirection.multiply(RAIL_BLOCK_SKIP_DISTANCE));
                    continue;
                }
                return;
            }
        }
    }

    private CollisionResult findCollision(ServerWorld world, PlayerEntity player, Vec3d start, Vec3d end, Set<Integer> ignoredEntities) {
        BlockHitResult blockHit = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        double maxEntityDistance = blockHit.getType() == HitResult.Type.MISS
                ? start.squaredDistanceTo(end)
                : start.squaredDistanceTo(blockHit.getPos());

        EntityHitResult entityHit = RebounderCollisionHelper.raycastLivingEntity(
                world,
                player,
                start,
                end,
                new Box(start, end).expand(HITSCAN_ENTITY_HIT_RADIUS + 0.85D),
                HITSCAN_ENTITY_HIT_RADIUS,
                entity -> isValidHitscanTarget(entity, player, ignoredEntities)
        );

        if (entityHit != null) {
            double entityDistance = start.squaredDistanceTo(entityHit.getPos());
            if (entityDistance <= maxEntityDistance) {
                return new CollisionResult(entityHit, null, entityHit.getPos());
            }
        }

        if (blockHit.getType() != HitResult.Type.MISS) {
            return new CollisionResult(null, blockHit, blockHit.getPos());
        }

        return null;
    }

    private FireSolution createFireSolution(ServerWorld world, ServerPlayerEntity player, Hand hand, double range) {
        Vec3d lookDirection = normalizedOrFallback(player.getRotationVec(1.0F), new Vec3d(0.0D, 0.0D, 1.0D));
        Vec3d eyePos = player.getEyePos();
        Vec3d eyeEnd = eyePos.add(lookDirection.multiply(range));

        BlockHitResult eyeBlockHit = world.raycast(new RaycastContext(
                eyePos,
                eyeEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        double maxEyeEntityDistance = eyeBlockHit.getType() == HitResult.Type.MISS
                ? eyePos.squaredDistanceTo(eyeEnd)
                : eyePos.squaredDistanceTo(eyeBlockHit.getPos());

        EntityHitResult eyeEntityHit = RebounderCollisionHelper.raycastLivingEntity(
                world,
                player,
                eyePos,
                eyeEnd,
                new Box(eyePos, eyeEnd).expand(HITSCAN_ENTITY_HIT_RADIUS + 0.85D),
                HITSCAN_ENTITY_HIT_RADIUS,
                entity -> isValidHitscanTarget(entity, player, Set.of())
        );

        Vec3d aimPoint = eyeEnd;
        if (eyeEntityHit != null && eyePos.squaredDistanceTo(eyeEntityHit.getPos()) <= maxEyeEntityDistance) {
            aimPoint = eyeEntityHit.getPos();
        } else if (eyeBlockHit.getType() != HitResult.Type.MISS) {
            aimPoint = eyeBlockHit.getPos();
        }

        Vec3d muzzlePos = getHandMuzzlePos(player, hand, lookDirection);
        Vec3d firingDirection = normalizedOrFallback(aimPoint.subtract(muzzlePos), lookDirection);
        return new FireSolution(muzzlePos, firingDirection);
    }

    private boolean isValidHitscanTarget(LivingEntity entity, PlayerEntity player, Set<Integer> ignoredEntities) {
        return entity.isAlive()
                && entity != player
                && !(entity instanceof ArmorStandEntity)
                && !entity.isSpectator()
                && !entity.isInvulnerable()
                && !ignoredEntities.contains(entity.getId())
                && !entity.isTeammate(player)
                && !player.isTeammate(entity);
    }

    private int getMaxCharge(ItemStack stack) {
        return MAX_CHARGE;
    }

    private int getRapidReleaseCooldown(ItemStack stack) {
        return ModEnchantments.hasRailpiercer(stack) ? RAPID_RELEASE_COOLDOWN_TICKS * 2 : RAPID_RELEASE_COOLDOWN_TICKS;
    }

    private int getRapidFireIntervalTicks(ItemStack stack, int rapidTicks) {
        double rampProgress = MathHelper.clamp(rapidTicks / (double) RAPID_SPEED_RAMP_TICKS, 0.0D, 1.0D);
        double smoothedProgress = rampProgress * rampProgress * (3.0D - (2.0D * rampProgress));
        double desiredRate = MathHelper.lerp((float) smoothedProgress, RAPID_MIN_RATE, RAPID_MAX_RATE);
        double desiredInterval = 20.0D / desiredRate;
        int interval = Math.max(1, (int) Math.floor(desiredInterval));
        double carry = getRapidIntervalCarry(stack) + (desiredInterval - interval);
        if (carry >= 1.0D) {
            interval++;
            carry -= 1.0D;
        }
        setRapidIntervalCarry(stack, carry);
        return interval;
    }

    private float getRapidFireDistanceMultiplier(double distance) {
        float progress = MathHelper.clamp((float) (distance / HITSCAN_RANGE), 0.0F, 1.0F);
        return MathHelper.lerp(progress, 0.25F, 1.5F);
    }

    private float getRapidFireArmorMultiplier(LivingEntity target) {
        int armorPieces = 0;
        for (ItemStack armorStack : target.getArmorItems()) {
            if (!armorStack.isEmpty()) {
                armorPieces++;
            }
        }

        return Math.max(1.0F, 2.0F - (0.25F * Math.min(4, armorPieces)));
    }

    private Vec3d applyVerticalSpread(Vec3d direction, double degrees) {
        Vec3d forward = normalizedOrFallback(direction, new Vec3d(0.0D, 0.0D, 1.0D));
        Vec3d right = forward.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
        if (right.lengthSquared() < 1.0E-6D) {
            right = new Vec3d(1.0D, 0.0D, 0.0D);
        }
        right = right.normalize();
        Vec3d up = right.crossProduct(forward).normalize();

        double radians = Math.toRadians(degrees);
        Vec3d spread = forward.multiply(Math.cos(radians)).add(up.multiply(Math.sin(radians)));
        return normalizedOrFallback(spread, forward);
    }

    private Vec3d applySpread(Vec3d direction, double yawDegrees, double pitchDegrees) {
        Vec3d yawSpread = direction.rotateY((float) Math.toRadians(yawDegrees));
        return applyVerticalSpread(yawSpread, pitchDegrees);
    }

    private Vec3d getHandMuzzlePos(PlayerEntity player, Hand hand, Vec3d forward) {
        Vec3d eyePos = player.getEyePos();
        Vec3d right = forward.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
        if (right.lengthSquared() < 1.0E-6D) {
            float yawRadians = (player.getYaw() - 90.0F) * ((float) Math.PI / 180.0F);
            right = new Vec3d(MathHelper.cos(yawRadians), 0.0D, MathHelper.sin(yawRadians));
        }
        right = normalizedOrFallback(right, new Vec3d(-1.0D, 0.0D, 0.0D));
        Vec3d up = normalizedOrFallback(right.crossProduct(forward), new Vec3d(0.0D, 1.0D, 0.0D));

        double mainHandSide = player.getMainArm() == Arm.RIGHT ? 1.0D : -1.0D;
        double side = hand == Hand.MAIN_HAND ? mainHandSide : -mainHandSide;
        return eyePos
                .add(forward.multiply(HAND_FORWARD_OFFSET))
                .add(right.multiply(HAND_SIDE_OFFSET * side))
                .subtract(up.multiply(HAND_DROP_OFFSET + (player.isSneaking() ? 0.03D : 0.0D)));
    }

    private Vec3d approximateImpactNormal(LivingEntity target, Vec3d impactPosition, Vec3d fallback) {
        Box box = target.getBoundingBox();
        Direction direction = Direction.NORTH;
        double bestDistance = Double.MAX_VALUE;

        for (Direction candidate : Direction.values()) {
            double distance = switch (candidate) {
                case WEST -> Math.abs(impactPosition.x - box.minX);
                case EAST -> Math.abs(box.maxX - impactPosition.x);
                case DOWN -> Math.abs(impactPosition.y - box.minY);
                case UP -> Math.abs(box.maxY - impactPosition.y);
                case NORTH -> Math.abs(impactPosition.z - box.minZ);
                case SOUTH -> Math.abs(box.maxZ - impactPosition.z);
            };
            if (distance < bestDistance) {
                bestDistance = distance;
                direction = candidate;
            }
        }

        return normalizedOrFallback(Vec3d.of(direction.getVector()), fallback);
    }

    private long getIdleStartTick(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null ? 0L : nbt.getLong(IDLE_START_TICK_KEY);
    }

    private void setIdleStartTick(ItemStack stack, long tick) {
        stack.getOrCreateNbt().putLong(IDLE_START_TICK_KEY, Math.max(0L, tick));
    }

    private void clearIdleStartTick(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null) {
            nbt.remove(IDLE_START_TICK_KEY);
        }
    }

    private long getNextFireTick(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null ? 0L : nbt.getLong(NEXT_FIRE_TICK_KEY);
    }

    private void setNextFireTick(ItemStack stack, long tick) {
        stack.getOrCreateNbt().putLong(NEXT_FIRE_TICK_KEY, Math.max(0L, tick));
    }

    private void setVisibleCooldown(ItemStack stack, long cooldownEndTick, int duration) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putLong(VISIBLE_COOLDOWN_END_TICK_KEY, Math.max(0L, cooldownEndTick));
        nbt.putInt(VISIBLE_COOLDOWN_DURATION_KEY, Math.max(1, duration));
    }

    private void clearVisibleCooldown(ItemStack stack, long currentTick) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) {
            return;
        }
        if (nbt.getLong(VISIBLE_COOLDOWN_END_TICK_KEY) > currentTick) {
            return;
        }
        nbt.remove(VISIBLE_COOLDOWN_END_TICK_KEY);
        nbt.remove(VISIBLE_COOLDOWN_DURATION_KEY);
    }

    private long getVisibleCooldownRemaining(ItemStack stack, @Nullable NbtCompound nbt) {
        if (nbt == null) {
            return 0L;
        }

        long cooldownEndTick = nbt.getLong(VISIBLE_COOLDOWN_END_TICK_KEY);
        if (cooldownEndTick <= 0L) {
            return 0L;
        }

        long displayTick = RebounderVisualState.getClientWorldTime();
        if (displayTick < 0L) {
            displayTick = nbt.getLong(DISPLAY_TICK_KEY);
        }
        return Math.max(0L, cooldownEndTick - displayTick);
    }

    private int getVisibleCooldownDuration(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null ? 0 : nbt.getInt(VISIBLE_COOLDOWN_DURATION_KEY);
    }

    private int getRapidFiringTicks(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null ? 0 : nbt.getInt(RAPID_FIRING_TICKS_KEY);
    }

    private void setRapidFiringTicks(ItemStack stack, int ticks) {
        if (ticks <= 0) {
            NbtCompound nbt = stack.getNbt();
            if (nbt != null) {
                nbt.remove(RAPID_FIRING_TICKS_KEY);
            }
            return;
        }

        stack.getOrCreateNbt().putInt(RAPID_FIRING_TICKS_KEY, ticks);
    }

    private double getRapidIntervalCarry(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null ? 0.0D : nbt.getDouble(RAPID_INTERVAL_CARRY_KEY);
    }

    private void setRapidIntervalCarry(ItemStack stack, double carry) {
        if (carry <= 0.0D) {
            NbtCompound nbt = stack.getNbt();
            if (nbt != null) {
                nbt.remove(RAPID_INTERVAL_CARRY_KEY);
            }
            return;
        }

        stack.getOrCreateNbt().putDouble(RAPID_INTERVAL_CARRY_KEY, carry);
    }

    public boolean isCoolingDown(ItemStack stack, long worldTime) {
        return worldTime < getNextFireTick(stack);
    }

    private boolean isRapidBurstActive(ItemStack stack) {
        return ModEnchantments.hasRapidFire(stack) && getRapidFiringTicks(stack) > 0;
    }

    public static boolean shouldCaptureRapidUse(PlayerEntity player, World world, Hand hand, @Nullable HitResult hitResult) {
        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof LatchetRebounderItem item) || !ModEnchantments.hasRapidFire(stack)) {
            return false;
        }

        if (!item.isRapidBurstActive(stack) && item.isCoolingDown(stack, world.getTime())) {
            return false;
        }

        if (hand == Hand.OFF_HAND && !canOffhandRapidFire(player)) {
            return false;
        }

        return !isLookingAtInteractableBlock(world, player, hitResult);
    }

    public static boolean canOffhandRapidFire(PlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        return mainHand.isEmpty() || (mainHand.getItem() instanceof LatchetRebounderItem && ModEnchantments.hasRapidFire(mainHand));
    }

    public static boolean isLookingAtInteractableBlock(World world, PlayerEntity player, @Nullable HitResult hitResult) {
        if (!(hitResult instanceof BlockHitResult blockHit)) {
            return false;
        }

        Block block = world.getBlockState(blockHit.getBlockPos()).getBlock();
        return world.getBlockState(blockHit.getBlockPos()).createScreenHandlerFactory(world, blockHit.getBlockPos()) != null
                || block instanceof DoorBlock
                || block instanceof TrapdoorBlock
                || block instanceof FenceGateBlock
                || block instanceof ButtonBlock
                || block instanceof LeverBlock
                || block instanceof AbstractSignBlock
                || block instanceof BellBlock
                || block instanceof BedBlock
                || block instanceof NoteBlock
                || block instanceof CakeBlock
                || block instanceof RepeaterBlock
                || block instanceof ComparatorBlock;
    }

    public static BlockHitResult raycastInteractableBlock(World world, PlayerEntity player) {
        Vec3d eyePos = player.getEyePos();
        Vec3d end = eyePos.add(player.getRotationVec(1.0F).multiply(INTERACT_BLOCK_RANGE));
        return world.raycast(new RaycastContext(
                eyePos,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));
    }

    private boolean isActivelyCharging(PlayerEntity player, ItemStack stack) {
        return player.isUsingItem() && player.getActiveItem() == stack;
    }

    public boolean usesStoredCharge(ItemStack stack) {
        return !ModEnchantments.hasRapidFire(stack) && !ModEnchantments.hasRailpiercer(stack);
    }

    private void setCurrentCharge(ItemStack stack, int charge) {
        stack.getOrCreateNbt().putInt(CHARGE_KEY, Math.max(0, Math.min(getMaxCharge(stack), charge)));
    }

    private void clearChargeState(ItemStack stack) {
        setCurrentCharge(stack, 0);
        clearIdleStartTick(stack);
        NbtCompound nbt = stack.getNbt();
        if (nbt != null) {
            nbt.remove(RAPID_INTERVAL_CARRY_KEY);
        }
    }

    private void beginRapidCooldown(ItemStack stack, long now) {
        int cooldown = getRapidReleaseCooldown(stack);
        setRapidFiringTicks(stack, 0);
        setRapidIntervalCarry(stack, 0.0D);
        setNextFireTick(stack, now + cooldown);
        setVisibleCooldown(stack, now + cooldown, cooldown);
    }

    private void playChargeGainSound(World world, PlayerEntity player, int newCharge) {
        playSound(world, player, SoundEvents.BLOCK_METAL_PRESSURE_PLATE_CLICK_ON, 0.62F, 0.92F + (newCharge * 0.04F));
        if (newCharge >= MAX_CHARGE) {
            playSound(world, player, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, 0.85F, 1.15F);
        }
    }

    private void playDecaySound(World world, PlayerEntity player) {
        playSound(world, player, SoundEvents.BLOCK_METAL_PRESSURE_PLATE_CLICK_OFF, 0.45F, 0.7F);
    }

    private void playFireSound(World world, PlayerEntity player, float volumeScale, boolean railpiercer, int consumedCharge) {
        float strength = railpiercer ? 1.3F : (volumeScale < 0.8F ? 0.65F : 1.0F);
        float mechanicalPitch = railpiercer ? 0.82F : (volumeScale < 0.8F ? 1.18F : 0.96F);
        float bangPitch = railpiercer ? 0.76F : (volumeScale < 0.8F ? 1.14F : 0.92F);

        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.PLAYERS, 0.48F * strength, mechanicalPitch);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.PLAYERS, 0.62F * strength, bangPitch);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), railpiercer ? SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK : SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.PLAYERS, 0.28F * strength, railpiercer ? 0.7F : 1.0F);
    }

    private float rapidFireVolumeScale(int rapidTicks) {
        return rapidTicks < 30 ? 0.32F : 0.24F;
    }

    private void playImpactHitSound(World world, Vec3d position) {
        world.playSound(null, position.x, position.y, position.z, SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 1.6F, 0.92F);
    }

    private void playImpactBlockSound(World world, Vec3d position) {
        world.playSound(null, position.x, position.y, position.z, SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.PLAYERS, 1.35F, 0.9F);
    }

    private void playSound(World world, PlayerEntity player, net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        world.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
    }

    private Vec3d normalizedOrFallback(Vec3d vector, Vec3d fallback) {
        return vector.lengthSquared() < 1.0E-6D ? fallback : vector.normalize();
    }

    private record FireSolution(Vec3d start, Vec3d direction) {}

    private record CollisionResult(@Nullable EntityHitResult entityHit, @Nullable BlockHitResult blockHit, Vec3d position) {}
}
