package moth.ratchet.item;

import moth.ratchet.RatchetMod;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RatchetIndexerItem extends Item {
    public static final TagKey<Item> DEPTH_TUNING_ITEMS = TagKey.of(RegistryKeys.ITEM, RatchetMod.id("depth_tuning_items"));

    private static final String HAS_ANCHOR_KEY = "HasAnchor";
    private static final String ANCHOR_X_KEY = "AnchorX";
    private static final String ANCHOR_Y_KEY = "AnchorY";
    private static final String ANCHOR_Z_KEY = "AnchorZ";
    private static final String ANCHOR_DIMENSION_KEY = "AnchorDimension";
    private static final String ANCHOR_OWNER_KEY = "AnchorOwner";
    private static final String MODE_KEY = "Mode";

    private static final double CLEAR_RADIUS = 5.0D;
    private static final double UNTUNED_ENTITY_RADIUS = 8.0D;
    private static final int UNTUNED_COOLDOWN_TICKS = 60;
    private static final int TUNED_COOLDOWN_TICKS = 100;
    private static final int MODIFIED_TUNED_COOLDOWN_TICKS = 140;

    public RatchetIndexerItem() {
        super(new Settings().maxCount(1));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(world instanceof ServerWorld serverWorld)) {
            return TypedActionResult.success(stack, true);
        }

        if (user.isSneaking()) {
            activateOrToggle(serverWorld, user, hand, stack);
        } else {
            setOrClearAnchor(serverWorld, user, stack);
        }

        return TypedActionResult.success(stack, false);
    }

    @Override
    public Text getName(ItemStack stack) {
        return isTuned(stack)
                ? Text.translatable("item.ratchet.ratchet_indexer_tuned")
                : super.getName(stack);
    }

    @Override
    public boolean allowNbtUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.ratchet.ratchet_indexer.mode", Text.translatable(getMode(stack).translationKey()))
                .formatted(Formatting.AQUA));

        if (!hasAnchor(stack)) {
            tooltip.add(Text.translatable("tooltip.ratchet.ratchet_indexer.anchor.unset").formatted(Formatting.GRAY));
            return;
        }

        BlockPos anchorPos = getAnchorPos(stack);
        Identifier anchorDimension = getAnchorDimension(stack);
        if (anchorPos == null || anchorDimension == null) {
            tooltip.add(Text.translatable("tooltip.ratchet.ratchet_indexer.anchor.unset").formatted(Formatting.GRAY));
            return;
        }

        tooltip.add(Text.translatable(
                "tooltip.ratchet.ratchet_indexer.anchor.set",
                anchorPos.getX(),
                anchorPos.getY(),
                anchorPos.getZ()
        ).formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.ratchet.ratchet_indexer.anchor.dimension", anchorDimension.toString())
                .formatted(Formatting.DARK_GRAY));
    }

    public static boolean isTuned(ItemStack stack) {
        return getMode(stack) == IndexerMode.TUNED;
    }

    public static boolean hasStoredAnchor(ItemStack stack) {
        return hasAnchor(stack);
    }

    @Nullable
    public static BlockPos getStoredAnchorPos(ItemStack stack) {
        return getAnchorPos(stack);
    }

    @Nullable
    public static Identifier getStoredAnchorDimension(ItemStack stack) {
        return getAnchorDimension(stack);
    }

    public static boolean shouldRenderAnchorFor(ItemStack stack, PlayerEntity player) {
        if (!hasAnchor(stack)) {
            return false;
        }

        Identifier anchorDimension = getAnchorDimension(stack);
        UUID owner = getAnchorOwner(stack);
        return anchorDimension != null
                && owner != null
                && owner.equals(player.getUuid())
                && anchorDimension.equals(player.getWorld().getRegistryKey().getValue());
    }

    private void setOrClearAnchor(ServerWorld world, PlayerEntity user, ItemStack stack) {
        if (!hasAnchor(stack)) {
            setAnchor(stack, user, user.getBlockPos(), world.getRegistryKey().getValue());
            sendFeedback(user, "message.ratchet.ratchet_indexer.anchor_set", SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8F, 1.15F);
            return;
        }

        BlockPos anchorPos = getAnchorPos(stack);
        Identifier anchorDimension = getAnchorDimension(stack);
        if (anchorPos == null || anchorDimension == null) {
            setAnchor(stack, user, user.getBlockPos(), world.getRegistryKey().getValue());
            sendFeedback(user, "message.ratchet.ratchet_indexer.anchor_set", SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8F, 1.15F);
            return;
        }

        Identifier currentDimension = world.getRegistryKey().getValue();
        if (!anchorDimension.equals(currentDimension)) {
            sendFeedback(user, "message.ratchet.ratchet_indexer.anchor_clear_wrong_dimension", SoundEvents.BLOCK_DISPENSER_FAIL, 0.7F, 0.8F);
            return;
        }

        double distanceSquared = user.getPos().squaredDistanceTo(Vec3d.ofCenter(anchorPos));
        if (distanceSquared > CLEAR_RADIUS * CLEAR_RADIUS) {
            sendFeedback(user, "message.ratchet.ratchet_indexer.anchor_clear_too_far", SoundEvents.BLOCK_DISPENSER_FAIL, 0.7F, 0.85F);
            return;
        }

        clearAnchor(stack);
        sendFeedback(user, "message.ratchet.ratchet_indexer.anchor_cleared", SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), 0.75F, 1.0F);
    }

    private void activateOrToggle(ServerWorld world, PlayerEntity user, Hand hand, ItemStack stack) {
        ItemStack offhandStack = hand == Hand.MAIN_HAND ? user.getOffHandStack() : ItemStack.EMPTY;
        if (offhandStack.isOf(ModItems.MECHANICAL_TUNER)) {
            toggleMode(user, stack);
            return;
        }

        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return;
        }

        if (!hasAnchor(stack)) {
            sendFeedback(user, "message.ratchet.ratchet_indexer.no_anchor", SoundEvents.BLOCK_DISPENSER_FAIL, 0.7F, 0.8F);
            return;
        }

        BlockPos anchorPos = getAnchorPos(stack);
        Identifier anchorDimension = getAnchorDimension(stack);
        if (anchorPos == null || anchorDimension == null) {
            clearAnchor(stack);
            sendFeedback(user, "message.ratchet.ratchet_indexer.no_anchor", SoundEvents.BLOCK_DISPENSER_FAIL, 0.7F, 0.8F);
            return;
        }

        if (!anchorDimension.equals(world.getRegistryKey().getValue())) {
            sendFeedback(user, "message.ratchet.ratchet_indexer.wrong_dimension", SoundEvents.BLOCK_DISPENSER_FAIL, 0.7F, 0.8F);
            return;
        }

        if (getMode(stack) == IndexerMode.UNTUNED) {
            activateUntuned(world, user, anchorPos);
            return;
        }

        activateTuned(world, user, anchorPos, offhandStack);
    }

    private void activateUntuned(ServerWorld world, PlayerEntity user, BlockPos anchorPos) {
        List<LivingEntity> nearbyEntities = world.getEntitiesByClass(
                LivingEntity.class,
                user.getBoundingBox().expand(UNTUNED_ENTITY_RADIUS),
                entity -> entity.isAlive() && entity != user && !entity.isSpectator()
        );

        if (nearbyEntities.isEmpty()) {
            sendFeedback(user, "message.ratchet.ratchet_indexer.no_targets", SoundEvents.BLOCK_DISPENSER_FAIL, 0.7F, 0.85F);
            return;
        }

        Set<BlockPos> reservedFeetPositions = new HashSet<>();
        int teleportedCount = 0;

        for (LivingEntity target : nearbyEntities) {
            Vec3d destination = RatchetIndexerTeleportHelper.findSafePositionNearAnchor(world, target, anchorPos, reservedFeetPositions);
            if (destination == null) {
                continue;
            }

            teleportEntity(target, destination);
            reservedFeetPositions.add(BlockPos.ofFloored(destination));
            teleportedCount++;
        }

        if (teleportedCount <= 0) {
            sendFeedback(user, "message.ratchet.ratchet_indexer.no_safe_location", SoundEvents.BLOCK_DISPENSER_FAIL, 0.7F, 0.8F);
            return;
        }

        user.getItemCooldownManager().set(this, UNTUNED_COOLDOWN_TICKS);
        user.sendMessage(Text.translatable("message.ratchet.ratchet_indexer.untuned_success", teleportedCount), true);
        playFeedbackSound(world, user, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.85F, 1.05F);
    }

    private void activateTuned(ServerWorld world, PlayerEntity user, BlockPos anchorPos, ItemStack offhandStack) {
        Vec3d destination;
        int cooldownTicks;
        String feedbackKey;

        if (offhandStack.isOf(Items.GHAST_TEAR)) {
            destination = RatchetIndexerTeleportHelper.findHighestSafePosition(world, user, anchorPos);
            cooldownTicks = MODIFIED_TUNED_COOLDOWN_TICKS;
            feedbackKey = "message.ratchet.ratchet_indexer.tuned_high_success";
        } else if (offhandStack.isIn(DEPTH_TUNING_ITEMS)) {
            destination = RatchetIndexerTeleportHelper.findLowestSafePosition(world, user, anchorPos);
            cooldownTicks = MODIFIED_TUNED_COOLDOWN_TICKS;
            feedbackKey = "message.ratchet.ratchet_indexer.tuned_low_success";
        } else {
            destination = RatchetIndexerTeleportHelper.findNearestVerticalPosition(world, user, anchorPos);
            cooldownTicks = TUNED_COOLDOWN_TICKS;
            feedbackKey = "message.ratchet.ratchet_indexer.tuned_success";
        }

        if (destination == null) {
            sendFeedback(user, "message.ratchet.ratchet_indexer.no_safe_location", SoundEvents.BLOCK_DISPENSER_FAIL, 0.7F, 0.8F);
            return;
        }

        if (offhandStack.isOf(Items.GHAST_TEAR) && !user.getAbilities().creativeMode) {
            offhandStack.decrement(1);
        }

        teleportEntity(user, destination);
        user.getItemCooldownManager().set(this, cooldownTicks);
        user.sendMessage(Text.translatable(feedbackKey), true);
        playFeedbackSound(world, user, SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, 0.9F, 1.1F);
    }

    private void toggleMode(PlayerEntity user, ItemStack stack) {
        IndexerMode nextMode = getMode(stack) == IndexerMode.UNTUNED ? IndexerMode.TUNED : IndexerMode.UNTUNED;
        setMode(stack, nextMode);
        user.sendMessage(Text.translatable("message.ratchet.ratchet_indexer.mode_toggled", Text.translatable(nextMode.translationKey())), true);
        playFeedbackSound(user.getWorld(), user, SoundEvents.BLOCK_COMPARATOR_CLICK, 0.75F, nextMode == IndexerMode.TUNED ? 1.2F : 0.85F);
    }

    private void teleportEntity(LivingEntity entity, Vec3d destination) {
        World world = entity.getWorld();
        Vec3d origin = entity.getPos();
        entity.stopRiding();
        entity.requestTeleport(destination.x, destination.y, destination.z);
        entity.setVelocity(Vec3d.ZERO);
        entity.velocityModified = true;
        entity.fallDistance = 0.0F;

        world.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 0.6F, 0.9F);
        world.playSound(null, destination.x, destination.y, destination.z, SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 0.6F, 1.05F);
    }

    private void sendFeedback(PlayerEntity user, String translationKey, SoundEvent sound, float volume, float pitch) {
        user.sendMessage(Text.translatable(translationKey), true);
        playFeedbackSound(user.getWorld(), user, sound, volume, pitch);
    }

    private void playFeedbackSound(World world, PlayerEntity user, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, user.getX(), user.getY(), user.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
    }

    private static boolean hasAnchor(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt != null
                && nbt.getBoolean(HAS_ANCHOR_KEY)
                && nbt.contains(ANCHOR_X_KEY)
                && nbt.contains(ANCHOR_Y_KEY)
                && nbt.contains(ANCHOR_Z_KEY)
                && nbt.contains(ANCHOR_DIMENSION_KEY);
    }

    @Nullable
    private static BlockPos getAnchorPos(ItemStack stack) {
        if (!hasAnchor(stack)) {
            return null;
        }

        NbtCompound nbt = stack.getNbt();
        if (nbt == null) {
            return null;
        }

        return new BlockPos(
                nbt.getInt(ANCHOR_X_KEY),
                nbt.getInt(ANCHOR_Y_KEY),
                nbt.getInt(ANCHOR_Z_KEY)
        );
    }

    @Nullable
    private static Identifier getAnchorDimension(ItemStack stack) {
        if (!hasAnchor(stack)) {
            return null;
        }

        NbtCompound nbt = stack.getNbt();
        if (nbt == null) {
            return null;
        }

        return Identifier.tryParse(nbt.getString(ANCHOR_DIMENSION_KEY));
    }

    private static void setAnchor(ItemStack stack, PlayerEntity player, BlockPos pos, Identifier dimensionId) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putBoolean(HAS_ANCHOR_KEY, true);
        nbt.putInt(ANCHOR_X_KEY, pos.getX());
        nbt.putInt(ANCHOR_Y_KEY, pos.getY());
        nbt.putInt(ANCHOR_Z_KEY, pos.getZ());
        nbt.putString(ANCHOR_DIMENSION_KEY, dimensionId.toString());
        nbt.putUuid(ANCHOR_OWNER_KEY, player.getUuid());
    }

    private static void clearAnchor(ItemStack stack) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putBoolean(HAS_ANCHOR_KEY, false);
        nbt.remove(ANCHOR_X_KEY);
        nbt.remove(ANCHOR_Y_KEY);
        nbt.remove(ANCHOR_Z_KEY);
        nbt.remove(ANCHOR_DIMENSION_KEY);
        nbt.remove(ANCHOR_OWNER_KEY);
    }

    private static IndexerMode getMode(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(MODE_KEY)) {
            return IndexerMode.UNTUNED;
        }

        return IndexerMode.fromNbt(nbt.getString(MODE_KEY));
    }

    private static void setMode(ItemStack stack, IndexerMode mode) {
        stack.getOrCreateNbt().putString(MODE_KEY, mode.nbtName());
    }

    @Nullable
    private static UUID getAnchorOwner(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.containsUuid(ANCHOR_OWNER_KEY)) {
            return null;
        }

        return nbt.getUuid(ANCHOR_OWNER_KEY);
    }

    private enum IndexerMode {
        UNTUNED("untuned", "tooltip.ratchet.ratchet_indexer.mode.untuned"),
        TUNED("tuned", "tooltip.ratchet.ratchet_indexer.mode.tuned");

        private final String nbtName;
        private final String translationKey;

        IndexerMode(String nbtName, String translationKey) {
            this.nbtName = nbtName;
            this.translationKey = translationKey;
        }

        public String nbtName() {
            return this.nbtName;
        }

        public String translationKey() {
            return this.translationKey;
        }

        public static IndexerMode fromNbt(String value) {
            for (IndexerMode mode : values()) {
                if (mode.nbtName.equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return UNTUNED;
        }
    }
}
