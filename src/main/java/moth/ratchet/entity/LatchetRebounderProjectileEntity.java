package moth.ratchet.entity;

import moth.ratchet.RatchetDamageSources;
import moth.ratchet.combat.RebounderCollisionHelper;
import moth.ratchet.combat.RebounderDamageHelper;
import moth.ratchet.network.RatchetNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class LatchetRebounderProjectileEntity extends Entity {
    private static final ChunkTicketType<Integer> REBOUNDER_SIMULATION_TICKET =
            ChunkTicketType.create("latchet_rebounder", Integer::compare, 5);
    private static final int PAUSE_TICKS = 3;
    private static final int MAX_SEGMENT_LIFETIME_TICKS = 300;
    private static final int MAX_TOTAL_RICOCHETS = 8;
    private static final int REPEAT_HIT_COOLDOWN_TICKS = 8;
    private static final int ENTITY_BOUNCE_IGNORE_TICKS = 3;
    private static final int MAX_TRAIL_HISTORY = 12;
    private static final int CLIENT_WHIZ_COOLDOWN_TICKS = 8;
    private static final double BASE_DAMAGE = 4.0D;
    private static final double DAMAGE_PER_CHARGE = 2.0D;
    private static final double BASE_SPEED = 2.70D;
    private static final double SPEED_PER_CHARGE = 0.15D;
    private static final double BLOCK_SPEED_MULTIPLIER = 0.85D;
    private static final double ENTITY_SPEED_MULTIPLIER = 0.95D;
    private static final double MIN_BOUNCE_ANGLE_DEGREES = 5.0D;
    private static final double TARGET_ASSIST_RANGE = 13.5D;
    private static final double TARGET_ASSIST_CONE_DEGREES = 78.0D;
    private static final double TARGET_ASSIST_MAX_DEGREES = 46.0D;
    private static final double TARGET_ASSIST_MIN_BLEND = 0.58D;
    private static final double IMPACT_REPEAT_DISTANCE = 0.25D;
    private static final double IMPACT_REPEAT_NORMAL_DOT = 0.98D;
    private static final double RESUME_OFFSET = 0.05D;
    private static final double SURFACE_RESUME_OFFSET = 0.035D;
    private static final double SHIELD_PASS_THROUGH_OFFSET = 0.85D;
    private static final double ENTITY_HIT_RADIUS = 0.45D;
    private static final double CLIENT_WHIZ_DISTANCE = 2.25D;
    private static final float WHIZ_VOLUME = 0.08F;
    private static final float WHIZ_PITCH = 1.78F;

    private static final TrackedData<Integer> PAUSE_TICKS_REMAINING = DataTracker.registerData(
            LatchetRebounderProjectileEntity.class, TrackedDataHandlerRegistry.INTEGER
    );
    private static final TrackedData<Float> IMPACT_X = DataTracker.registerData(
            LatchetRebounderProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT
    );
    private static final TrackedData<Float> IMPACT_Y = DataTracker.registerData(
            LatchetRebounderProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT
    );
    private static final TrackedData<Float> IMPACT_Z = DataTracker.registerData(
            LatchetRebounderProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT
    );
    private static final TrackedData<Float> INCOMING_X = DataTracker.registerData(
            LatchetRebounderProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT
    );
    private static final TrackedData<Float> INCOMING_Y = DataTracker.registerData(
            LatchetRebounderProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT
    );
    private static final TrackedData<Float> INCOMING_Z = DataTracker.registerData(
            LatchetRebounderProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT
    );
    private static final TrackedData<Float> OUTGOING_X = DataTracker.registerData(
            LatchetRebounderProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT
    );
    private static final TrackedData<Float> OUTGOING_Y = DataTracker.registerData(
            LatchetRebounderProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT
    );
    private static final TrackedData<Float> OUTGOING_Z = DataTracker.registerData(
            LatchetRebounderProjectileEntity.class, TrackedDataHandlerRegistry.FLOAT
    );

    private final Map<Integer, Integer> recentEntityHits = new HashMap<>();
    private final Deque<Vec3d> clientTrailHistory = new ArrayDeque<>();
    private Vec3d pendingResumeDirection = Vec3d.ZERO;
    private Vec3d pendingResumeNormal = Vec3d.ZERO;
    private Vec3d clientLastTrailSample = null;
    private double pendingResumeOffset = RESUME_OFFSET;
    private double pendingResumeSurfaceOffset = SURFACE_RESUME_OFFSET;
    private double pendingResumeSpeed = 0.0D;
    private Vec3d lastImpactPosition = null;
    private Vec3d lastImpactNormal = null;
    private Vec3d priorImpactPosition = null;
    private Vec3d priorImpactNormal = null;
    private UUID ownerUuid;
    private float damage = (float) BASE_DAMAGE;
    private int ownerId = -1;
    private int sourceCharge;
    private int remainingBounces;
    private int ticksSinceBounceOrSpawn;
    private int totalRicochets;
    private int clientWhizCooldown;
    private int ignoredEntityId = -1;
    private int ignoredEntityUntilAge = -1;

    public LatchetRebounderProjectileEntity(EntityType<? extends LatchetRebounderProjectileEntity> entityType, World world) {
        super(entityType, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    public void initializeFromShooter(LivingEntity shooter, int charge, Vec3d spawnPos, Vec3d direction) {
        this.ownerUuid = shooter.getUuid();
        this.ownerId = shooter.getId();
        this.sourceCharge = charge;
        this.remainingBounces = charge;
        this.damage = (float) (BASE_DAMAGE + (DAMAGE_PER_CHARGE * charge));

        double speed = BASE_SPEED * (1.0D + (SPEED_PER_CHARGE * charge));
        Vec3d normalizedDirection = normalizedOrFallback(direction, shooter.getRotationVec(1.0F));

        this.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, shooter.getYaw(), shooter.getPitch());
        this.setVelocity(normalizedDirection.multiply(speed));
        this.updateFacing(normalizedDirection);
        this.setTrackedImpact(spawnPos);
        this.setTrackedIncomingDirection(normalizedDirection);
        this.setTrackedOutgoingDirection(normalizedDirection);
        this.ticksSinceBounceOrSpawn = 0;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(PAUSE_TICKS_REMAINING, 0);
        this.dataTracker.startTracking(IMPACT_X, 0.0F);
        this.dataTracker.startTracking(IMPACT_Y, 0.0F);
        this.dataTracker.startTracking(IMPACT_Z, 0.0F);
        this.dataTracker.startTracking(INCOMING_X, 0.0F);
        this.dataTracker.startTracking(INCOMING_Y, 0.0F);
        this.dataTracker.startTracking(INCOMING_Z, 1.0F);
        this.dataTracker.startTracking(OUTGOING_X, 0.0F);
        this.dataTracker.startTracking(OUTGOING_Y, 0.0F);
        this.dataTracker.startTracking(OUTGOING_Z, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) {
            tickClientTrail();
            return;
        }

        ServerWorld serverWorld = (ServerWorld) this.getWorld();
        if (!hasViewers(serverWorld)) {
            this.discard();
            return;
        }

        refreshSimulationTickets(serverWorld);
        this.ticksSinceBounceOrSpawn++;
        if (this.ticksSinceBounceOrSpawn > MAX_SEGMENT_LIFETIME_TICKS) {
            this.discard();
            return;
        }

        pruneExpiredEntityHitLocks();

        if (isPaused()) {
            tickPausedState();
            return;
        }

        Vec3d velocity = this.getVelocity();
        if (velocity.lengthSquared() < 1.0E-6D) {
            this.discard();
            return;
        }

        Vec3d start = this.getPos();
        Vec3d end = start.add(velocity);
        CollisionResult collision = findCollision(start, end);

        if (collision == null) {
            this.setPosition(end);
            this.updateFacing(velocity.normalize());
            return;
        }

        if (collision.hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity livingTarget) {
            handleEntityCollision(livingTarget, entityHit.getPos());
            return;
        }

        if (collision.hit instanceof BlockHitResult blockHit) {
            handleBlockCollision(blockHit);
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.ownerUuid = nbt.containsUuid("OwnerUuid") ? nbt.getUuid("OwnerUuid") : null;
        this.ownerId = nbt.getInt("OwnerId");
        this.sourceCharge = nbt.getInt("SourceCharge");
        this.remainingBounces = nbt.getInt("RemainingBounces");
        this.totalRicochets = nbt.getInt("TotalRicochets");
        this.ticksSinceBounceOrSpawn = nbt.getInt("TicksSinceBounceOrSpawn");
        this.damage = nbt.getFloat("Damage");
        this.pendingResumeDirection = readVector(nbt, "PendingDirection");
        this.pendingResumeNormal = nbt.contains("PendingNormal") ? readVector(nbt, "PendingNormal") : Vec3d.ZERO;
        this.pendingResumeOffset = nbt.contains("PendingResumeOffset") ? nbt.getDouble("PendingResumeOffset") : RESUME_OFFSET;
        this.pendingResumeSurfaceOffset = nbt.contains("PendingSurfaceOffset") ? nbt.getDouble("PendingSurfaceOffset") : SURFACE_RESUME_OFFSET;
        this.pendingResumeSpeed = nbt.getDouble("PendingSpeed");
        this.lastImpactPosition = readNullableVector(nbt, "LastImpact");
        this.lastImpactNormal = readNullableVector(nbt, "LastNormal");
        this.priorImpactPosition = readNullableVector(nbt, "PriorImpact");
        this.priorImpactNormal = readNullableVector(nbt, "PriorNormal");
        this.ignoredEntityId = nbt.getInt("IgnoredEntityId");
        this.ignoredEntityUntilAge = nbt.getInt("IgnoredEntityUntilAge");
        this.setTrackedImpact(readVector(nbt, "TrackedImpact"));
        this.setTrackedIncomingDirection(readVector(nbt, "TrackedIncoming"));
        this.setTrackedOutgoingDirection(readVector(nbt, "TrackedOutgoing"));
        this.dataTracker.set(PAUSE_TICKS_REMAINING, nbt.getInt("PauseTicks"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (this.ownerUuid != null) {
            nbt.putUuid("OwnerUuid", this.ownerUuid);
        }
        nbt.putInt("OwnerId", this.ownerId);
        nbt.putInt("SourceCharge", this.sourceCharge);
        nbt.putInt("RemainingBounces", this.remainingBounces);
        nbt.putInt("TotalRicochets", this.totalRicochets);
        nbt.putInt("TicksSinceBounceOrSpawn", this.ticksSinceBounceOrSpawn);
        nbt.putFloat("Damage", this.damage);
        writeVector(nbt, "PendingDirection", this.pendingResumeDirection);
        writeVector(nbt, "PendingNormal", this.pendingResumeNormal);
        nbt.putDouble("PendingResumeOffset", this.pendingResumeOffset);
        nbt.putDouble("PendingSurfaceOffset", this.pendingResumeSurfaceOffset);
        nbt.putDouble("PendingSpeed", this.pendingResumeSpeed);
        writeNullableVector(nbt, "LastImpact", this.lastImpactPosition);
        writeNullableVector(nbt, "LastNormal", this.lastImpactNormal);
        writeNullableVector(nbt, "PriorImpact", this.priorImpactPosition);
        writeNullableVector(nbt, "PriorNormal", this.priorImpactNormal);
        nbt.putInt("IgnoredEntityId", this.ignoredEntityId);
        nbt.putInt("IgnoredEntityUntilAge", this.ignoredEntityUntilAge);
        writeVector(nbt, "TrackedImpact", getTrackedImpact());
        writeVector(nbt, "TrackedIncoming", getTrackedIncomingDirection());
        writeVector(nbt, "TrackedOutgoing", getTrackedOutgoingDirection());
        nbt.putInt("PauseTicks", this.dataTracker.get(PAUSE_TICKS_REMAINING));
    }

    public boolean isPaused() {
        return this.dataTracker.get(PAUSE_TICKS_REMAINING) > 0;
    }

    public float getPauseBlend(float tickDelta) {
        int pauseTicksRemaining = this.dataTracker.get(PAUSE_TICKS_REMAINING);
        if (pauseTicksRemaining <= 0) {
            return 1.0F;
        }

        float progress = (PAUSE_TICKS - pauseTicksRemaining) + tickDelta;
        return MathHelper.clamp(progress / (float) PAUSE_TICKS, 0.0F, 1.0F);
    }

    public Vec3d getTrackedImpact() {
        return new Vec3d(
                this.dataTracker.get(IMPACT_X),
                this.dataTracker.get(IMPACT_Y),
                this.dataTracker.get(IMPACT_Z)
        );
    }

    public Vec3d getTrackedIncomingDirection() {
        return normalizedOrFallback(new Vec3d(
                this.dataTracker.get(INCOMING_X),
                this.dataTracker.get(INCOMING_Y),
                this.dataTracker.get(INCOMING_Z)
        ), new Vec3d(0.0D, 0.0D, 1.0D));
    }

    public Vec3d getTrackedOutgoingDirection() {
        return normalizedOrFallback(new Vec3d(
                this.dataTracker.get(OUTGOING_X),
                this.dataTracker.get(OUTGOING_Y),
                this.dataTracker.get(OUTGOING_Z)
        ), new Vec3d(0.0D, 0.0D, 1.0D));
    }

    public Vec3d getVisualDirection(float tickDelta) {
        if (!isPaused()) {
            Vec3d velocity = this.getVelocity();
            if (velocity.lengthSquared() > 1.0E-6D) {
                return velocity.normalize();
            }
            return getTrackedOutgoingDirection();
        }

        return getTrackedIncomingDirection().lerp(getTrackedOutgoingDirection(), getPauseBlend(tickDelta)).normalize();
    }

    public List<Vec3d> getTrailHistory() {
        return new ArrayList<>(this.clientTrailHistory);
    }

    @Override
    protected Box calculateBoundingBox() {
        return this.getType().getDimensions().getBoxAt(this.getPos());
    }

    private void handleBlockCollision(BlockHitResult blockHit) {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            this.discard();
            return;
        }

        Vec3d impactPosition = blockHit.getPos();
        Vec3d surfaceNormal = Vec3d.of(blockHit.getSide().getVector());
        RatchetNetworking.sendImpactBurst(serverWorld, impactPosition, surfaceNormal, willConsumeFinalBounce());

        if (shouldDiscardForRepeatedImpact(impactPosition, surfaceNormal)) {
            this.discard();
            return;
        }

        if (this.remainingBounces <= 0 || this.totalRicochets >= MAX_TOTAL_RICOCHETS) {
            this.setPosition(impactPosition);
            this.discard();
            return;
        }

        Vec3d incomingDirection = this.getVelocity().normalize();
        Vec3d outgoingDirection = applyTargetAssist(impactPosition, clampBounceAngle(reflect(incomingDirection, surfaceNormal), surfaceNormal));
        playBounceSound(impactPosition);
        queueBounce(
                impactPosition,
                surfaceNormal,
                incomingDirection,
                outgoingDirection,
                this.getVelocity().length() * BLOCK_SPEED_MULTIPLIER,
                RESUME_OFFSET,
                SURFACE_RESUME_OFFSET
        );
    }

    private void handleEntityCollision(LivingEntity target, Vec3d impactPosition) {
        Integer previousHitAge = this.recentEntityHits.get(target.getId());
        if (previousHitAge != null && (this.age - previousHitAge) < REPEAT_HIT_COOLDOWN_TICKS) {
            this.discard();
            return;
        }
        this.recentEntityHits.put(target.getId(), this.age);

        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            this.discard();
            return;
        }

        Vec3d incomingDirection = normalizedOrFallback(this.getVelocity(), getTrackedOutgoingDirection());
        Vec3d normalizedIncomingDirection = incomingDirection.normalize();
        boolean shieldPassThrough = isShieldPassThroughTarget(target, impactPosition);
        Vec3d surfaceNormal = shieldPassThrough
                ? incomingDirection.multiply(-1.0D)
                : approximateEntityImpactNormal(target, impactPosition, normalizedIncomingDirection);
        RatchetNetworking.sendImpactBurst(serverWorld, impactPosition, surfaceNormal, !shieldPassThrough && willConsumeFinalBounce());

        LivingEntity attacker = getOwnerLiving();
        ItemStack weaponStack = attacker == null ? ItemStack.EMPTY : attacker.getMainHandStack();
        DamageSource normalSource = attacker == null
                ? serverWorld.getDamageSources().thrown(this, null)
                : RatchetDamageSources.rebounderShot(serverWorld, this, attacker, weaponStack, false, false);
        RebounderDamageHelper.applyPiercingDamage(serverWorld, target, this, attacker, weaponStack, normalSource, this.damage);
        playImpactHitSound(impactPosition);

        if (!target.isAlive()) {
            this.discard();
            return;
        }

        if (shieldPassThrough) {
            Vec3d direction = normalizedOrFallback(this.getVelocity(), getTrackedOutgoingDirection());
            double offset = Math.max(target.getWidth(), SHIELD_PASS_THROUGH_OFFSET) + 0.1D;
            setIgnoredEntity(target.getId(), ENTITY_BOUNCE_IGNORE_TICKS);
            this.setPosition(impactPosition.add(direction.multiply(offset)));
            this.updateFacing(direction);
            return;
        }

        if (shouldDiscardForRepeatedImpact(impactPosition, surfaceNormal)) {
            this.discard();
            return;
        }

        if (this.remainingBounces <= 0 || this.totalRicochets >= MAX_TOTAL_RICOCHETS) {
            this.setPosition(impactPosition);
            this.discard();
            return;
        }

        Vec3d outgoingDirection = applyTargetAssist(impactPosition, clampBounceAngle(reflect(normalizedIncomingDirection, surfaceNormal), surfaceNormal));
        setIgnoredEntity(target.getId(), ENTITY_BOUNCE_IGNORE_TICKS);
        queueBounce(
                impactPosition,
                surfaceNormal,
                normalizedIncomingDirection,
                outgoingDirection,
                this.getVelocity().length() * ENTITY_SPEED_MULTIPLIER,
                Math.max(RESUME_OFFSET, ENTITY_HIT_RADIUS + 0.26D),
                Math.max(SURFACE_RESUME_OFFSET, ENTITY_HIT_RADIUS + 0.08D)
        );
    }

    private void queueBounce(Vec3d impactPosition, Vec3d surfaceNormal, Vec3d incomingDirection,
                             Vec3d outgoingDirection, double newSpeed, double resumeOffset, double surfaceResumeOffset) {
        this.priorImpactPosition = this.lastImpactPosition;
        this.priorImpactNormal = this.lastImpactNormal;
        this.lastImpactPosition = impactPosition;
        this.lastImpactNormal = surfaceNormal;
        this.remainingBounces--;
        this.totalRicochets++;
        this.ticksSinceBounceOrSpawn = 0;
        this.pendingResumeDirection = outgoingDirection.normalize();
        this.pendingResumeNormal = normalizedOrFallback(surfaceNormal, outgoingDirection.multiply(-1.0D));
        this.pendingResumeOffset = Math.max(RESUME_OFFSET, resumeOffset);
        this.pendingResumeSurfaceOffset = Math.max(SURFACE_RESUME_OFFSET, surfaceResumeOffset);
        this.pendingResumeSpeed = Math.max(0.01D, newSpeed);

        this.setVelocity(Vec3d.ZERO);
        this.setPosition(impactPosition);
        this.setTrackedImpact(impactPosition);
        this.setTrackedIncomingDirection(incomingDirection);
        this.setTrackedOutgoingDirection(outgoingDirection);
        this.dataTracker.set(PAUSE_TICKS_REMAINING, PAUSE_TICKS);
    }

    private void tickPausedState() {
        this.setPosition(getTrackedImpact());

        int ticksRemaining = this.dataTracker.get(PAUSE_TICKS_REMAINING);
        if (ticksRemaining > 1) {
            this.dataTracker.set(PAUSE_TICKS_REMAINING, ticksRemaining - 1);
            return;
        }

        Vec3d resumeDirection = normalizedOrFallback(this.pendingResumeDirection, getTrackedOutgoingDirection());
        Vec3d resumeNormal = normalizedOrFallback(this.pendingResumeNormal, resumeDirection.multiply(-1.0D));
        this.setPosition(getTrackedImpact()
                .add(resumeNormal.multiply(this.pendingResumeSurfaceOffset))
                .add(resumeDirection.multiply(this.pendingResumeOffset)));
        this.setVelocity(resumeDirection.multiply(this.pendingResumeSpeed));
        this.updateFacing(resumeDirection);
        this.dataTracker.set(PAUSE_TICKS_REMAINING, 0);
    }

    private CollisionResult findCollision(Vec3d start, Vec3d end) {
        Box searchBox = new Box(start, end).expand(ENTITY_HIT_RADIUS + 1.0D);
        BlockHitResult blockHit = this.getWorld().raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));

        double maxEntityDistance = blockHit.getType() == HitResult.Type.MISS
                ? start.squaredDistanceTo(end)
                : start.squaredDistanceTo(blockHit.getPos());

        EntityHitResult entityHit = RebounderCollisionHelper.raycastLivingEntity(
                this.getWorld(),
                this,
                start,
                end,
                searchBox,
                ENTITY_HIT_RADIUS,
                this::canHitEntity
        );

        if (entityHit == null) {
            return blockHit.getType() == HitResult.Type.MISS ? null : new CollisionResult(blockHit);
        }

        double entityDistance = start.squaredDistanceTo(entityHit.getPos());
        if (blockHit.getType() != HitResult.Type.MISS && start.squaredDistanceTo(blockHit.getPos()) <= entityDistance) {
            return new CollisionResult(blockHit);
        }

        if (entityDistance > maxEntityDistance) {
            return blockHit.getType() == HitResult.Type.MISS ? null : new CollisionResult(blockHit);
        }

        return new CollisionResult(entityHit);
    }

    private boolean canHitEntity(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }

        LivingEntity owner = getOwnerLiving();
        return living.isAlive()
                && living != owner
                && (this.ignoredEntityId < 0 || living.getId() != this.ignoredEntityId || this.age >= this.ignoredEntityUntilAge)
                && !(living instanceof ArmorStandEntity)
                && !living.isSpectator()
                && !living.isInvulnerable()
                && (owner == null || (!living.isTeammate(owner) && !owner.isTeammate(living)));
    }

    private boolean isShieldPassThroughTarget(LivingEntity target, Vec3d impactPosition) {
        if (!target.isBlocking() || !target.getActiveItem().isOf(Items.SHIELD)) {
            return false;
        }

        Vec3d lookDirection = target.getRotationVec(1.0F).normalize();
        Vec3d toImpact = impactPosition.subtract(target.getPos());
        if (toImpact.lengthSquared() < 1.0E-4D) {
            return true;
        }

        return lookDirection.dotProduct(toImpact.normalize()) > 0.2D;
    }

    private Vec3d approximateEntityImpactNormal(LivingEntity target, Vec3d impactPosition, Vec3d incomingDirection) {
        Box box = target.getBoundingBox().expand(ENTITY_HIT_RADIUS * 0.35D);
        List<FaceDistance> candidates = List.of(
                new FaceDistance(Math.abs(impactPosition.x - box.minX), Direction.WEST),
                new FaceDistance(Math.abs(box.maxX - impactPosition.x), Direction.EAST),
                new FaceDistance(Math.abs(impactPosition.y - box.minY), Direction.DOWN),
                new FaceDistance(Math.abs(box.maxY - impactPosition.y), Direction.UP),
                new FaceDistance(Math.abs(impactPosition.z - box.minZ), Direction.NORTH),
                new FaceDistance(Math.abs(box.maxZ - impactPosition.z), Direction.SOUTH)
        );

        Vec3d incomingOpposite = incomingDirection.multiply(-1.0D);
        Vec3d radial = impactPosition.subtract(box.getCenter());
        if (radial.lengthSquared() < 1.0E-4D) {
            radial = incomingOpposite;
        }

        Vec3d faceNormal = candidates.stream()
                .max(Comparator.comparingDouble(candidate -> {
                    Vec3d normal = Vec3d.of(candidate.direction.getVector());
                    double alignment = incomingOpposite.dotProduct(normal);
                    return (alignment * 3.0D) - (candidate.distance * 5.0D);
                }))
                .map(candidate -> Vec3d.of(candidate.direction.getVector()))
                .orElse(incomingOpposite);

        Vec3d blended = faceNormal.multiply(0.85D).add(radial.normalize().multiply(1.15D));
        if (blended.dotProduct(incomingOpposite) < 0.2D) {
            blended = faceNormal.multiply(1.2D).add(incomingOpposite.multiply(0.7D));
        }

        return normalizedOrFallback(blended, incomingOpposite);
    }

    private Vec3d applyTargetAssist(Vec3d impactPosition, Vec3d reflectedDirection) {
        LivingEntity owner = getOwnerLiving();
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            return reflectedDirection;
        }

        double coneThreshold = Math.cos(Math.toRadians(TARGET_ASSIST_CONE_DEGREES));
        Vec3d start = impactPosition.add(reflectedDirection.multiply(RESUME_OFFSET));
        List<LivingEntity> candidates = serverWorld.getEntitiesByClass(
                LivingEntity.class,
                new Box(impactPosition, impactPosition).expand(TARGET_ASSIST_RANGE),
                entity -> canHitEntity(entity)
        );

        LivingEntity bestTarget = null;
        Vec3d bestAimPoint = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (LivingEntity candidate : candidates) {
            for (Vec3d aimPoint : getAssistAimPoints(candidate)) {
                Vec3d toTarget = aimPoint.subtract(start);
                double distance = toTarget.length();
                if (distance <= 1.0E-4D || distance > TARGET_ASSIST_RANGE) {
                    continue;
                }

                Vec3d directionToTarget = toTarget.normalize();
                double alignment = reflectedDirection.dotProduct(directionToTarget);
                if (alignment < coneThreshold) {
                    continue;
                }

                BlockHitResult sightHit = serverWorld.raycast(new RaycastContext(
                        start,
                        aimPoint,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        owner == null ? this : owner
                ));
                if (sightHit.getType() != HitResult.Type.MISS) {
                    continue;
                }

                double score = (alignment * 5.0D) + ((1.0D - (distance / TARGET_ASSIST_RANGE)) * 1.5D);
                if (score > bestScore) {
                    bestScore = score;
                    bestTarget = candidate;
                    bestAimPoint = aimPoint;
                }
            }
        }

        if (bestTarget == null || bestAimPoint == null) {
            return reflectedDirection;
        }

        Vec3d targetDirection = bestAimPoint.subtract(start).normalize();

        return steerToward(reflectedDirection, targetDirection, TARGET_ASSIST_MAX_DEGREES, TARGET_ASSIST_MIN_BLEND);
    }

    private List<Vec3d> getAssistAimPoints(LivingEntity candidate) {
        Vec3d center = candidate.getBoundingBox().getCenter();
        return List.of(
                new Vec3d(candidate.getX(), candidate.getEyeY(), candidate.getZ()),
                new Vec3d(candidate.getX(), candidate.getBodyY(0.7D), candidate.getZ()),
                center
        );
    }

    private boolean willConsumeFinalBounce() {
        return this.remainingBounces == 1 || this.totalRicochets >= (MAX_TOTAL_RICOCHETS - 1);
    }

    private boolean shouldDiscardForRepeatedImpact(Vec3d impactPosition, Vec3d surfaceNormal) {
        return matchesImpact(this.lastImpactPosition, this.lastImpactNormal, impactPosition, surfaceNormal)
                || matchesImpact(this.priorImpactPosition, this.priorImpactNormal, impactPosition, surfaceNormal);
    }

    private boolean matchesImpact(Vec3d previousImpactPosition, Vec3d previousImpactNormal, Vec3d impactPosition, Vec3d surfaceNormal) {
        return previousImpactPosition != null
                && previousImpactNormal != null
                && previousImpactPosition.squaredDistanceTo(impactPosition) <= (IMPACT_REPEAT_DISTANCE * IMPACT_REPEAT_DISTANCE)
                && previousImpactNormal.normalize().dotProduct(surfaceNormal.normalize()) >= IMPACT_REPEAT_NORMAL_DOT;
    }

    private void pruneExpiredEntityHitLocks() {
        this.recentEntityHits.entrySet().removeIf(entry -> (this.age - entry.getValue()) >= REPEAT_HIT_COOLDOWN_TICKS);
        if (this.ignoredEntityId >= 0 && this.age >= this.ignoredEntityUntilAge) {
            this.ignoredEntityId = -1;
            this.ignoredEntityUntilAge = -1;
        }
    }

    private void setIgnoredEntity(int entityId, int ticks) {
        this.ignoredEntityId = entityId;
        this.ignoredEntityUntilAge = this.age + Math.max(1, ticks);
    }

    private LivingEntity getOwnerLiving() {
        if (this.ownerId >= 0) {
            Entity entity = this.getWorld().getEntityById(this.ownerId);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }

        if (this.ownerUuid == null || !(this.getWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }

        Entity entity = serverWorld.getEntity(this.ownerUuid);
        if (entity instanceof LivingEntity living) {
            this.ownerId = living.getId();
            return living;
        }
        return null;
    }

    private Vec3d reflect(Vec3d incomingDirection, Vec3d normal) {
        Vec3d reflected = incomingDirection.subtract(normal.multiply(2.0D * incomingDirection.dotProduct(normal)));
        return normalizedOrFallback(reflected, incomingDirection.multiply(-1.0D));
    }

    private Vec3d clampBounceAngle(Vec3d reflectedDirection, Vec3d normal) {
        Vec3d normalizedNormal = normalizedOrFallback(normal, new Vec3d(0.0D, 1.0D, 0.0D));
        double planeDot = Math.abs(reflectedDirection.normalize().dotProduct(normalizedNormal));
        double minimumPlaneDot = Math.sin(Math.toRadians(MIN_BOUNCE_ANGLE_DEGREES));
        if (planeDot >= minimumPlaneDot) {
            return reflectedDirection.normalize();
        }

        Vec3d planeComponent = reflectedDirection.subtract(normalizedNormal.multiply(reflectedDirection.dotProduct(normalizedNormal)));
        planeComponent = normalizedOrFallback(planeComponent, perpendicular(normalizedNormal));

        double normalStrength = Math.copySign(minimumPlaneDot, reflectedDirection.dotProduct(normalizedNormal));
        double planeStrength = Math.sqrt(Math.max(0.0D, 1.0D - (minimumPlaneDot * minimumPlaneDot)));
        return planeComponent.multiply(planeStrength).add(normalizedNormal.multiply(normalStrength)).normalize();
    }

    private Vec3d steerToward(Vec3d currentDirection, Vec3d desiredDirection, double maxDegrees, double minimumBlend) {
        Vec3d from = currentDirection.normalize();
        Vec3d to = desiredDirection.normalize();
        double dot = MathHelper.clamp((float) from.dotProduct(to), -1.0F, 1.0F);
        double angle = Math.acos(dot);
        double maxRadians = Math.toRadians(maxDegrees);
        if (angle <= maxRadians) {
            return to;
        }

        double mix = Math.max(minimumBlend, maxRadians / angle);
        return from.lerp(to, mix).normalize();
    }

    private Vec3d perpendicular(Vec3d vector) {
        Vec3d axis = Math.abs(vector.y) < 0.9D ? new Vec3d(0.0D, 1.0D, 0.0D) : new Vec3d(1.0D, 0.0D, 0.0D);
        return normalizedOrFallback(vector.crossProduct(axis), new Vec3d(1.0D, 0.0D, 0.0D));
    }

    private void updateFacing(Vec3d direction) {
        Vec3d normalized = normalizedOrFallback(direction, new Vec3d(0.0D, 0.0D, 1.0D));
        float horizontal = MathHelper.sqrt((float) (normalized.x * normalized.x + normalized.z * normalized.z));
        this.prevYaw = this.getYaw();
        this.prevPitch = this.getPitch();
        this.setYaw((float) (MathHelper.atan2(normalized.z, normalized.x) * (180.0F / Math.PI)) - 90.0F);
        this.setPitch((float) (-(MathHelper.atan2(normalized.y, horizontal) * (180.0F / Math.PI))));
    }

    private void tickClientTrail() {
        Vec3d sample = isPaused() ? getTrackedImpact() : this.getPos();
        tickClientWhiz(sample);
        if (!this.clientTrailHistory.isEmpty() && this.clientTrailHistory.peekFirst().squaredDistanceTo(sample) <= 1.0E-5D) {
            this.clientLastTrailSample = sample;
            return;
        }

        this.clientTrailHistory.addFirst(sample);
        this.clientLastTrailSample = sample;
        while (this.clientTrailHistory.size() > MAX_TRAIL_HISTORY) {
            this.clientTrailHistory.removeLast();
        }
    }

    private void tickClientWhiz(Vec3d currentSample) {
        if (this.clientWhizCooldown > 0) {
            this.clientWhizCooldown--;
        }

        if (this.clientWhizCooldown > 0 || isPaused() || this.clientLastTrailSample == null) {
            return;
        }

        PlayerEntity listenerEntity = this.getWorld().getClosestPlayer(this, CLIENT_WHIZ_DISTANCE + 1.5D);
        if (listenerEntity == null) {
            return;
        }

        Vec3d listener = listenerEntity.getEyePos();
        double distance = squaredDistanceToSegment(listener, this.clientLastTrailSample, currentSample);
        if (distance > (CLIENT_WHIZ_DISTANCE * CLIENT_WHIZ_DISTANCE)) {
            return;
        }

        Vec3d velocity = this.getVelocity();
        Vec3d toListener = listener.subtract(currentSample);
        if (velocity.lengthSquared() < 1.0E-4D || toListener.lengthSquared() < 1.0E-4D) {
            return;
        }

        double approach = velocity.normalize().dotProduct(toListener.normalize());
        if (approach <= 0.15D) {
            return;
        }

        this.getWorld().playSound(currentSample.x, currentSample.y, currentSample.z, SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, WHIZ_VOLUME, WHIZ_PITCH, false);
        this.clientWhizCooldown = CLIENT_WHIZ_COOLDOWN_TICKS;
    }

    private double squaredDistanceToSegment(Vec3d point, Vec3d start, Vec3d end) {
        Vec3d segment = end.subtract(start);
        double lengthSquared = segment.lengthSquared();
        if (lengthSquared < 1.0E-6D) {
            return point.squaredDistanceTo(start);
        }

        double t = MathHelper.clamp((float) (point.subtract(start).dotProduct(segment) / lengthSquared), 0.0F, 1.0F);
        Vec3d nearest = start.add(segment.multiply(t));
        return point.squaredDistanceTo(nearest);
    }

    private void playImpactHitSound(Vec3d impactPosition) {
        this.getWorld().playSound(null, impactPosition.x, impactPosition.y, impactPosition.z, SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 1.5F, 0.9F);
    }

    private void playBounceSound(Vec3d impactPosition) {
        this.getWorld().playSound(null, impactPosition.x, impactPosition.y, impactPosition.z, SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.PLAYERS, 1.1F, 1.32F);
    }

    private boolean hasViewers(ServerWorld world) {
        int viewDistanceChunks = Math.max(2, world.getServer().getPlayerManager().getViewDistance());
        double maxDistance = Math.max((viewDistanceChunks * 16.0D) + 80.0D, 256.0D);
        double maxDistanceSquared = maxDistance * maxDistance;

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            if (player.squaredDistanceTo(this) <= maxDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private void refreshSimulationTickets(ServerWorld world) {
        ChunkPos currentChunk = new ChunkPos(BlockPos.ofFloored(this.getPos()));
        world.getChunkManager().addTicket(REBOUNDER_SIMULATION_TICKET, currentChunk, 2, this.getId());

        Vec3d projectedPosition = this.getPos().add(this.getVelocity());
        ChunkPos nextChunk = new ChunkPos(BlockPos.ofFloored(projectedPosition));
        if (!nextChunk.equals(currentChunk)) {
            world.getChunkManager().addTicket(REBOUNDER_SIMULATION_TICKET, nextChunk, 2, this.getId());
        }
    }

    private void setTrackedImpact(Vec3d position) {
        this.dataTracker.set(IMPACT_X, (float) position.x);
        this.dataTracker.set(IMPACT_Y, (float) position.y);
        this.dataTracker.set(IMPACT_Z, (float) position.z);
    }

    private void setTrackedIncomingDirection(Vec3d direction) {
        Vec3d normalized = normalizedOrFallback(direction, new Vec3d(0.0D, 0.0D, 1.0D));
        this.dataTracker.set(INCOMING_X, (float) normalized.x);
        this.dataTracker.set(INCOMING_Y, (float) normalized.y);
        this.dataTracker.set(INCOMING_Z, (float) normalized.z);
    }

    private void setTrackedOutgoingDirection(Vec3d direction) {
        Vec3d normalized = normalizedOrFallback(direction, new Vec3d(0.0D, 0.0D, 1.0D));
        this.dataTracker.set(OUTGOING_X, (float) normalized.x);
        this.dataTracker.set(OUTGOING_Y, (float) normalized.y);
        this.dataTracker.set(OUTGOING_Z, (float) normalized.z);
    }

    private Vec3d normalizedOrFallback(Vec3d vector, Vec3d fallback) {
        if (vector.lengthSquared() < 1.0E-6D) {
            return fallback;
        }
        return vector.normalize();
    }

    private Vec3d normalizedOrFallback(Vec3d vector, org.joml.Vector3f fallback) {
        return normalizedOrFallback(vector, new Vec3d(fallback.x(), fallback.y(), fallback.z()));
    }

    private static Vec3d readVector(NbtCompound nbt, String key) {
        NbtCompound vectorNbt = nbt.getCompound(key);
        return new Vec3d(vectorNbt.getDouble("X"), vectorNbt.getDouble("Y"), vectorNbt.getDouble("Z"));
    }

    private static Vec3d readNullableVector(NbtCompound nbt, String key) {
        return nbt.contains(key) ? readVector(nbt, key) : null;
    }

    private static void writeVector(NbtCompound nbt, String key, Vec3d vector) {
        NbtCompound vectorNbt = new NbtCompound();
        vectorNbt.putDouble("X", vector.x);
        vectorNbt.putDouble("Y", vector.y);
        vectorNbt.putDouble("Z", vector.z);
        nbt.put(key, vectorNbt);
    }

    private static void writeNullableVector(NbtCompound nbt, String key, Vec3d vector) {
        if (vector != null) {
            writeVector(nbt, key, vector);
        }
    }

    private record CollisionResult(HitResult hit) {}

    private record FaceDistance(double distance, Direction direction) {}
}
