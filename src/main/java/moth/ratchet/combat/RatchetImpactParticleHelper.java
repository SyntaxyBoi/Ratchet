package moth.ratchet.combat;

import net.minecraft.block.BlockState;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RatchetImpactParticleHelper {
    private RatchetImpactParticleHelper() {}

    public static void spawnGroundImpactParticles(ServerWorld world, Vec3d center, BlockPos basePos,
                                                  float impactDamage, float referenceDamage, float radius) {
        BlockPos particleBasePos = basePos;
        if (world.getBlockState(particleBasePos).isAir()) {
            particleBasePos = particleBasePos.down();
        }

        BlockState blockState = world.getBlockState(particleBasePos);
        if (blockState.isAir()) {
            return;
        }

        float safeReferenceDamage = Math.max(0.5F, referenceDamage);
        float fullness = MathHelper.clamp(impactDamage / (safeReferenceDamage * 0.9F), 0.0F, 1.0F);
        float density = 0.25F + (0.75F * fullness);
        float particleRadius = Math.max(0.75F, radius);
        int particleCount = Math.max(16, Math.round((36.0F * particleRadius) * density));

        BlockStateParticleEffect particle = new BlockStateParticleEffect(ParticleTypes.BLOCK, blockState);
        for (int i = 0; i < particleCount; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(world.random.nextDouble()) * particleRadius;
            double x = center.x + Math.cos(angle) * distance;
            double z = center.z + Math.sin(angle) * distance;
            double y = particleBasePos.getY() + 1.02D;
            double vx = (world.random.nextDouble() - 0.5D) * 0.12D;
            double vy = 0.16D + (world.random.nextDouble() * 0.26D);
            double vz = (world.random.nextDouble() - 0.5D) * 0.12D;

            world.spawnParticles(particle, x, y, z, 0, vx, vy, vz, 0.0D);
        }
    }
}
