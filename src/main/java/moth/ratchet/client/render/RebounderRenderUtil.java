package moth.ratchet.client.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class RebounderRenderUtil {
    private RebounderRenderUtil() {}

    public static void applyFacingRotation(MatrixStack matrices, Vec3d direction) {
        Vec3d normalized = normalize(direction, new Vec3d(0.0D, 0.0D, 1.0D));
        float yaw = (float) (MathHelper.atan2(normalized.z, normalized.x) * (180.0F / Math.PI)) - 90.0F;
        float horizontal = MathHelper.sqrt((float) (normalized.x * normalized.x + normalized.z * normalized.z));
        float pitch = (float) (-(MathHelper.atan2(normalized.y, horizontal) * (180.0F / Math.PI)));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
    }

    public static List<Vec3d> buildCatmullRomTrail(List<Vec3d> controlPoints, int samplesPerSegment) {
        if (controlPoints.size() < 2) {
            return List.copyOf(controlPoints);
        }

        List<Vec3d> result = new ArrayList<>();
        for (int index = 0; index < controlPoints.size() - 1; index++) {
            Vec3d p0 = controlPoints.get(Math.max(0, index - 1));
            Vec3d p1 = controlPoints.get(index);
            Vec3d p2 = controlPoints.get(index + 1);
            Vec3d p3 = controlPoints.get(Math.min(controlPoints.size() - 1, index + 2));

            for (int sample = 0; sample < samplesPerSegment; sample++) {
                double t = sample / (double) samplesPerSegment;
                result.add(catmullRom(p0, p1, p2, p3, t));
            }
        }
        result.add(controlPoints.get(controlPoints.size() - 1));
        return result;
    }

    public static void emitTubeSegment(Matrix4f positionMatrix, VertexConsumer consumer, Vec3d start, Vec3d end,
                                       float startRadius, float endRadius, int red, int green, int blue,
                                       int startAlpha, int endAlpha, int sides) {
        Vec3d axis = end.subtract(start);
        if (axis.lengthSquared() < 1.0E-6D) {
            return;
        }

        Vec3d direction = axis.normalize();
        Vec3d basisA = perpendicular(direction).normalize();
        Vec3d basisB = direction.crossProduct(basisA).normalize();

        for (int side = 0; side < sides; side++) {
            double startAngle = (Math.PI * 2.0D * side) / sides;
            double endAngle = (Math.PI * 2.0D * (side + 1)) / sides;

            Vec3d startOffsetA = radialOffset(basisA, basisB, startAngle, startRadius);
            Vec3d startOffsetB = radialOffset(basisA, basisB, endAngle, startRadius);
            Vec3d endOffsetA = radialOffset(basisA, basisB, startAngle, endRadius);
            Vec3d endOffsetB = radialOffset(basisA, basisB, endAngle, endRadius);

            emitQuad(
                    positionMatrix,
                    consumer,
                    start.add(startOffsetA),
                    start.add(startOffsetB),
                    end.add(endOffsetB),
                    end.add(endOffsetA),
                    red,
                    green,
                    blue,
                    startAlpha,
                    endAlpha
            );
        }
    }

    public static void emitTriangle(Matrix4f positionMatrix, VertexConsumer consumer, Vec3d first, Vec3d second, Vec3d third,
                                    int red, int green, int blue, int alpha) {
        emitQuad(positionMatrix, consumer, first, second, third, third, red, green, blue, alpha, alpha);
    }

    private static Vec3d catmullRom(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return new Vec3d(
                0.5D * ((2.0D * p1.x) + (-p0.x + p2.x) * t + (2.0D * p0.x - 5.0D * p1.x + 4.0D * p2.x - p3.x) * t2 + (-p0.x + 3.0D * p1.x - 3.0D * p2.x + p3.x) * t3),
                0.5D * ((2.0D * p1.y) + (-p0.y + p2.y) * t + (2.0D * p0.y - 5.0D * p1.y + 4.0D * p2.y - p3.y) * t2 + (-p0.y + 3.0D * p1.y - 3.0D * p2.y + p3.y) * t3),
                0.5D * ((2.0D * p1.z) + (-p0.z + p2.z) * t + (2.0D * p0.z - 5.0D * p1.z + 4.0D * p2.z - p3.z) * t2 + (-p0.z + 3.0D * p1.z - 3.0D * p2.z + p3.z) * t3)
        );
    }

    private static Vec3d perpendicular(Vec3d direction) {
        Vec3d axis = Math.abs(direction.y) < 0.95D ? new Vec3d(0.0D, 1.0D, 0.0D) : new Vec3d(1.0D, 0.0D, 0.0D);
        return direction.crossProduct(axis);
    }

    private static Vec3d radialOffset(Vec3d basisA, Vec3d basisB, double angle, float radius) {
        return basisA.multiply(Math.cos(angle) * radius).add(basisB.multiply(Math.sin(angle) * radius));
    }

    private static Vec3d normalize(Vec3d vector, Vec3d fallback) {
        return vector.lengthSquared() < 1.0E-6D ? fallback : vector.normalize();
    }

    private static void emitQuad(Matrix4f positionMatrix, VertexConsumer consumer, Vec3d first, Vec3d second, Vec3d third, Vec3d fourth,
                                 int red, int green, int blue, int startAlpha, int endAlpha) {
        consumer.vertex(positionMatrix, (float) first.x, (float) first.y, (float) first.z).color(red, green, blue, startAlpha).next();
        consumer.vertex(positionMatrix, (float) second.x, (float) second.y, (float) second.z).color(red, green, blue, startAlpha).next();
        consumer.vertex(positionMatrix, (float) third.x, (float) third.y, (float) third.z).color(red, green, blue, endAlpha).next();
        consumer.vertex(positionMatrix, (float) fourth.x, (float) fourth.y, (float) fourth.z).color(red, green, blue, endAlpha).next();
    }
}
