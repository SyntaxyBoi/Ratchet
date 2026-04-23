package moth.ratchet.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class LatchetRebounderTraceRenderer {
    private static final int RAILPIERCER_COLOR = 0xB76DFF;
    private static final int TRACE_LIFETIME_TICKS = 6;
    private static final int TRACE_SIDES = 4;
    private static final int BURST_SIDES = 6;
    private static final int NORMAL_BURST_COUNT = 10;
    private static final int FINAL_BURST_COUNT = 18;
    private static final int NORMAL_BURST_LIFETIME = 12;
    private static final int FINAL_BURST_LIFETIME = 18;
    private static final List<Trace> ACTIVE_TRACES = new ArrayList<>();
    private static final List<ImpactSpark> ACTIVE_SPARKS = new ArrayList<>();

    private LatchetRebounderTraceRenderer() {}

    public static void addTrace(Vec3d start, Vec3d end, int color) {
        ACTIVE_TRACES.add(new Trace(start, end, color));
    }

    public static void addImpactBurst(Vec3d position, Vec3d normal, boolean finalBurst, int color) {
        Vec3d axis = normalize(normal, new Vec3d(0.0D, 1.0D, 0.0D));
        Vec3d tangentA = perpendicular(axis);
        Vec3d tangentB = normalize(axis.crossProduct(tangentA), new Vec3d(1.0D, 0.0D, 0.0D));
        int count = finalBurst ? FINAL_BURST_COUNT : NORMAL_BURST_COUNT;
        int lifetime = finalBurst ? FINAL_BURST_LIFETIME : NORMAL_BURST_LIFETIME;
        float radius = finalBurst ? 0.03F : 0.024F;
        double minSpeed = finalBurst ? 0.09D : 0.055D;
        double maxSpeed = finalBurst ? 0.19D : 0.11D;
        double normalPush = finalBurst ? 0.03D : 0.018D;

        for (int index = 0; index < count; index++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
            double speed = ThreadLocalRandom.current().nextDouble(minSpeed, maxSpeed);
            double spread = ThreadLocalRandom.current().nextDouble(0.78D, 1.18D);
            double lift = ThreadLocalRandom.current().nextDouble(-0.01D, 0.02D);
            Vec3d lateral = tangentA.multiply(Math.cos(angle)).add(tangentB.multiply(Math.sin(angle))).normalize();
            Vec3d velocity = lateral.multiply(speed * spread)
                    .add(axis.multiply(normalPush * ThreadLocalRandom.current().nextDouble(0.65D, 1.0D)))
                    .add(0.0D, lift, 0.0D);
            Vec3d spawn = position.add(axis.multiply(0.02D + ThreadLocalRandom.current().nextDouble(0.015D)));
            ACTIVE_SPARKS.add(new ImpactSpark(spawn, velocity, lifetime, radius, color));
        }
    }

    public static void tick(MinecraftClient client) {
        if (client.world == null) {
            ACTIVE_TRACES.clear();
            ACTIVE_SPARKS.clear();
            return;
        }

        Iterator<Trace> traceIterator = ACTIVE_TRACES.iterator();
        while (traceIterator.hasNext()) {
            Trace trace = traceIterator.next();
            trace.age++;
            if (trace.age >= TRACE_LIFETIME_TICKS) {
                traceIterator.remove();
            }
        }

        Iterator<ImpactSpark> sparkIterator = ACTIVE_SPARKS.iterator();
        while (sparkIterator.hasNext()) {
            ImpactSpark spark = sparkIterator.next();
            spark.previousPosition = spark.position;
            spark.position = spark.position.add(spark.velocity);
            spark.velocity = spark.velocity.multiply(0.9D).add(0.0D, -0.002D, 0.0D);
            spark.age++;
            if (spark.age >= spark.lifetime) {
                sparkIterator.remove();
            }
        }
    }

    public static void render(WorldRenderContext context) {
        if ((ACTIVE_TRACES.isEmpty() && ACTIVE_SPARKS.isEmpty()) || context.matrixStack() == null || context.consumers() == null) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        Vec3d cameraPos = context.camera().getPos();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        VertexConsumer consumer = context.consumers().getBuffer(RenderLayer.getLightning());
        renderTraces(positionMatrix, consumer);
        renderImpactSparks(positionMatrix, consumer, context.tickDelta());

        matrices.pop();
    }

    private static void renderTraces(Matrix4f positionMatrix, VertexConsumer consumer) {
        for (Trace trace : ACTIVE_TRACES) {
            float progress = trace.age / (float) TRACE_LIFETIME_TICKS;
            int alpha = Math.round(MathHelper.lerp(1.0F - progress, 0.0F, 185.0F));
            float widthMultiplier = trace.color == RAILPIERCER_COLOR ? 2.25F : 1.0F;
            float radius = MathHelper.lerp(progress, 0.055F, 0.018F) * widthMultiplier;
            RebounderRenderUtil.emitTubeSegment(
                    positionMatrix,
                    consumer,
                    trace.start,
                    trace.end,
                    radius,
                    radius * 0.55F,
                    red(trace.color),
                    green(trace.color),
                    blue(trace.color),
                    alpha,
                    Math.max(0, alpha - 60),
                    TRACE_SIDES
            );
        }
    }

    private static void renderImpactSparks(Matrix4f positionMatrix, VertexConsumer consumer, float tickDelta) {
        for (ImpactSpark spark : ACTIVE_SPARKS) {
            float progress = spark.age / (float) spark.lifetime;
            int alpha = Math.round(MathHelper.lerp(1.0F - progress, 20.0F, 225.0F));
            float radius = MathHelper.lerp(progress, spark.radius, spark.radius * 0.38F);
            Vec3d currentPosition = spark.previousPosition.lerp(spark.position, tickDelta);
            Vec3d tailPosition = currentPosition.subtract(spark.velocity.multiply(1.6D));
            RebounderRenderUtil.emitTubeSegment(
                    positionMatrix,
                    consumer,
                    tailPosition,
                    currentPosition,
                    radius * 0.45F,
                    radius,
                    red(spark.color),
                    green(spark.color),
                    blue(spark.color),
                    Math.max(0, alpha - 120),
                    alpha,
                    BURST_SIDES
            );
        }
    }

    private static Vec3d normalize(Vec3d vector, Vec3d fallback) {
        return vector.lengthSquared() < 1.0E-6D ? fallback : vector.normalize();
    }

    private static Vec3d perpendicular(Vec3d normal) {
        Vec3d axis = Math.abs(normal.y) < 0.95D ? new Vec3d(0.0D, 1.0D, 0.0D) : new Vec3d(1.0D, 0.0D, 0.0D);
        return normalize(normal.crossProduct(axis), new Vec3d(1.0D, 0.0D, 0.0D));
    }

    private static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    private static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    private static final class Trace {
        private final Vec3d start;
        private final Vec3d end;
        private final int color;
        private int age;

        private Trace(Vec3d start, Vec3d end, int color) {
            this.start = start;
            this.end = end;
            this.color = color;
        }
    }

    private static final class ImpactSpark {
        private Vec3d position;
        private Vec3d previousPosition;
        private Vec3d velocity;
        private final int lifetime;
        private final float radius;
        private final int color;
        private int age;

        private ImpactSpark(Vec3d position, Vec3d velocity, int lifetime, float radius, int color) {
            this.position = position;
            this.previousPosition = position;
            this.velocity = velocity;
            this.lifetime = lifetime;
            this.radius = radius;
            this.color = color;
        }
    }
}
