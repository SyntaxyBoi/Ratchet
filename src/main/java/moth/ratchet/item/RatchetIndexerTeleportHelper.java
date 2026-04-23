package moth.ratchet.item;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

final class RatchetIndexerTeleportHelper {
    private static final int MAX_NEAR_ANCHOR_RADIUS = 4;
    private static final int[] VERTICAL_SEARCH_ORDER = {0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6};

    private RatchetIndexerTeleportHelper() {}

    @Nullable
    public static Vec3d findSafePositionNearAnchor(ServerWorld world, LivingEntity entity, BlockPos anchorFeetPos,
                                                   Set<BlockPos> reservedFeetPositions) {
        for (int radius = 0; radius <= MAX_NEAR_ANCHOR_RADIUS; radius++) {
            for (int verticalOffset : VERTICAL_SEARCH_ORDER) {
                int candidateY = anchorFeetPos.getY() + verticalOffset;
                if (!isWithinHeight(world, candidateY)) {
                    continue;
                }

                if (radius == 0) {
                    BlockPos candidate = new BlockPos(anchorFeetPos.getX(), candidateY, anchorFeetPos.getZ());
                    Vec3d position = tryFeetPosition(world, entity, candidate, reservedFeetPositions);
                    if (position != null) {
                        return position;
                    }
                    continue;
                }

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }

                        BlockPos candidate = new BlockPos(anchorFeetPos.getX() + dx, candidateY, anchorFeetPos.getZ() + dz);
                        Vec3d position = tryFeetPosition(world, entity, candidate, reservedFeetPositions);
                        if (position != null) {
                            return position;
                        }
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    public static Vec3d findNearestVerticalPosition(ServerWorld world, LivingEntity entity, BlockPos anchorFeetPos) {
        int maxDistance = Math.max(
                world.getTopY() - anchorFeetPos.getY(),
                anchorFeetPos.getY() - world.getBottomY()
        );

        for (int distance = 0; distance <= maxDistance; distance++) {
            int upY = anchorFeetPos.getY() + distance;
            if (isWithinHeight(world, upY)) {
                Vec3d position = tryFeetPosition(world, entity, new BlockPos(anchorFeetPos.getX(), upY, anchorFeetPos.getZ()), null);
                if (position != null) {
                    return position;
                }
            }

            if (distance == 0) {
                continue;
            }

            int downY = anchorFeetPos.getY() - distance;
            if (isWithinHeight(world, downY)) {
                Vec3d position = tryFeetPosition(world, entity, new BlockPos(anchorFeetPos.getX(), downY, anchorFeetPos.getZ()), null);
                if (position != null) {
                    return position;
                }
            }
        }

        return null;
    }

    @Nullable
    public static Vec3d findHighestSafePosition(ServerWorld world, LivingEntity entity, BlockPos anchorFeetPos) {
        for (int y = world.getTopY() - 1; y >= world.getBottomY() + 1; y--) {
            Vec3d position = tryFeetPosition(world, entity, new BlockPos(anchorFeetPos.getX(), y, anchorFeetPos.getZ()), null);
            if (position != null) {
                return position;
            }
        }

        return null;
    }

    @Nullable
    public static Vec3d findLowestSafePosition(ServerWorld world, LivingEntity entity, BlockPos anchorFeetPos) {
        for (int y = world.getBottomY() + 1; y < world.getTopY(); y++) {
            Vec3d position = tryFeetPosition(world, entity, new BlockPos(anchorFeetPos.getX(), y, anchorFeetPos.getZ()), null);
            if (position != null) {
                return position;
            }
        }

        return null;
    }

    @Nullable
    private static Vec3d tryFeetPosition(ServerWorld world, LivingEntity entity, BlockPos feetPos,
                                         @Nullable Set<BlockPos> reservedFeetPositions) {
        if (reservedFeetPositions != null && reservedFeetPositions.contains(feetPos)) {
            return null;
        }

        if (!isSafeFeetPosition(world, entity, feetPos)) {
            return null;
        }

        return Vec3d.ofBottomCenter(feetPos);
    }

    private static boolean isSafeFeetPosition(ServerWorld world, LivingEntity entity, BlockPos feetPos) {
        if (!isWithinHeight(world, feetPos.getY()) || !world.getWorldBorder().contains(feetPos)) {
            return false;
        }

        BlockPos floorPos = feetPos.down();
        if (!hasStandingSurface(world, floorPos)) {
            return false;
        }

        Vec3d destination = Vec3d.ofBottomCenter(feetPos);
        Box destinationBox = entity.getBoundingBox().offset(
                destination.x - entity.getX(),
                destination.y - entity.getY(),
                destination.z - entity.getZ()
        );

        if (!world.isSpaceEmpty(entity, destinationBox)) {
            return false;
        }

        return !containsUnsafeBlocks(world, destinationBox);
    }

    private static boolean isWithinHeight(ServerWorld world, int feetY) {
        return feetY > world.getBottomY() && feetY < world.getTopY();
    }

    private static boolean hasStandingSurface(ServerWorld world, BlockPos floorPos) {
        if (!world.getWorldBorder().contains(floorPos)) {
            return false;
        }

        BlockState floorState = world.getBlockState(floorPos);
        if (floorState.isAir() || isUnsafeBlock(floorState)) {
            return false;
        }

        VoxelShape shape = floorState.getCollisionShape(world, floorPos);
        return !shape.isEmpty();
    }

    private static boolean containsUnsafeBlocks(ServerWorld world, Box box) {
        int minX = MathHelper.floor(box.minX + 1.0E-5D);
        int minY = MathHelper.floor(box.minY + 1.0E-5D);
        int minZ = MathHelper.floor(box.minZ + 1.0E-5D);
        int maxX = MathHelper.floor(box.maxX - 1.0E-5D);
        int maxY = MathHelper.floor(box.maxY - 1.0E-5D);
        int maxZ = MathHelper.floor(box.maxZ - 1.0E-5D);

        for (BlockPos pos : BlockPos.iterate(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockState state = world.getBlockState(pos);
            if (isUnsafeBlock(state)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isUnsafeBlock(BlockState state) {
        return !state.getFluidState().isEmpty()
                || state.isOf(Blocks.LAVA)
                || state.isOf(Blocks.FIRE)
                || state.isOf(Blocks.SOUL_FIRE)
                || state.isOf(Blocks.CACTUS)
                || state.isOf(Blocks.MAGMA_BLOCK)
                || state.isOf(Blocks.WITHER_ROSE)
                || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.POWDER_SNOW)
                || CampfireBlock.isLitCampfire(state);
    }
}
