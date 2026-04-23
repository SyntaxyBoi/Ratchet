package moth.ratchet.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class RebounderCollisionHelper {
    private RebounderCollisionHelper() {}

    public static EntityHitResult raycastLivingEntity(World world, Entity source, Vec3d start, Vec3d end, Box searchBox,
                                                      double hitRadius, Predicate<LivingEntity> predicate) {
        List<LivingEntity> candidates = world.getEntitiesByClass(
                LivingEntity.class,
                searchBox.expand(hitRadius),
                predicate::test
        );

        LivingEntity bestEntity = null;
        Vec3d bestHitPosition = null;
        double bestDistance = Double.MAX_VALUE;

        for (LivingEntity candidate : candidates) {
            if (candidate == source) {
                continue;
            }

            Box candidateBox = candidate.getBoundingBox().expand(hitRadius);
            Optional<Vec3d> hit = candidateBox.raycast(start, end);
            Vec3d hitPosition;
            double distance;

            if (hit.isPresent()) {
                hitPosition = hit.get();
                distance = start.squaredDistanceTo(hitPosition);
            } else if (candidateBox.contains(start)) {
                hitPosition = start;
                distance = 0.0D;
            } else {
                continue;
            }

            if (distance < bestDistance) {
                bestDistance = distance;
                bestEntity = candidate;
                bestHitPosition = hitPosition;
            }
        }

        return bestEntity == null || bestHitPosition == null ? null : new EntityHitResult(bestEntity, bestHitPosition);
    }
}
