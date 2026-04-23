package moth.ratchet.client.render;

import moth.ratchet.RatchetMod;
import moth.ratchet.entity.LatchetRebounderProjectileEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LatchetRebounderProjectileRenderer extends EntityRenderer<LatchetRebounderProjectileEntity> {
    private static final Identifier DUMMY_TEXTURE = RatchetMod.id("textures/item/latchet_rebounder.png");
    private static final int TRAIL_SIDES = 6;
    private static final int TRAIL_RED = 255;
    private static final int TRAIL_GREEN = 214;
    private static final int TRAIL_BLUE = 84;
    private static final int CORE_RED = 255;
    private static final int CORE_GREEN = 236;
    private static final int CORE_BLUE = 132;

    public LatchetRebounderProjectileRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(LatchetRebounderProjectileEntity entity, Frustum frustum, double x, double y, double z) {
        if (super.shouldRender(entity, frustum, x, y, z)) {
            return true;
        }

        Box visibilityBox = entity.getBoundingBox().expand(0.75D);
        for (Vec3d point : entity.getTrailHistory()) {
            visibilityBox = visibilityBox.union(new Box(point, point).expand(0.22D));
        }
        return frustum.isVisible(visibilityBox);
    }

    @Override
    public void render(LatchetRebounderProjectileEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        renderTrail(entity, tickDelta, matrices, vertexConsumers);

        matrices.push();
        RebounderRenderUtil.applyFacingRotation(matrices, entity.getVisualDirection(tickDelta));
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getLightning());
        renderCore(positionMatrix, consumer);
        matrices.pop();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }

    @Override
    public Identifier getTexture(LatchetRebounderProjectileEntity entity) {
        return DUMMY_TEXTURE;
    }

    private void renderTrail(LatchetRebounderProjectileEntity entity, float tickDelta, MatrixStack matrices,
                             VertexConsumerProvider vertexConsumers) {
        List<Vec3d> history = entity.getTrailHistory();
        if (history.size() < 2) {
            return;
        }

        List<Vec3d> controlPoints = new ArrayList<>(history);
        Collections.reverse(controlPoints);
        List<Vec3d> splinePoints = RebounderRenderUtil.buildCatmullRomTrail(controlPoints, 3);
        if (splinePoints.size() < 2) {
            return;
        }

        Vec3d renderOrigin = entity.getLerpedPos(tickDelta);
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getLightning());
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        int lastIndex = splinePoints.size() - 1;

        for (int index = 0; index < lastIndex; index++) {
            float startProgress = index / (float) lastIndex;
            float endProgress = (index + 1) / (float) lastIndex;
            Vec3d start = splinePoints.get(index).subtract(renderOrigin);
            Vec3d end = splinePoints.get(index + 1).subtract(renderOrigin);
            float startRadius = MathHelper.lerp(startProgress, 0.024F, 0.072F);
            float endRadius = MathHelper.lerp(endProgress, 0.024F, 0.072F);
            int startAlpha = Math.round(MathHelper.lerp(startProgress, 30.0F, 185.0F));
            int endAlpha = Math.round(MathHelper.lerp(endProgress, 30.0F, 220.0F));

            RebounderRenderUtil.emitTubeSegment(
                    positionMatrix,
                    consumer,
                    start,
                    end,
                    startRadius,
                    endRadius,
                    TRAIL_RED,
                    TRAIL_GREEN,
                    TRAIL_BLUE,
                    startAlpha,
                    endAlpha,
                    TRAIL_SIDES
            );
        }
    }

    private void renderCore(Matrix4f positionMatrix, VertexConsumer consumer) {
        Vec3d top = new Vec3d(0.0D, 0.13D, 0.0D);
        Vec3d bottom = new Vec3d(0.0D, -0.13D, 0.0D);
        Vec3d east = new Vec3d(0.09D, 0.0D, 0.0D);
        Vec3d west = new Vec3d(-0.09D, 0.0D, 0.0D);
        Vec3d north = new Vec3d(0.0D, 0.0D, -0.19D);
        Vec3d south = new Vec3d(0.0D, 0.0D, 0.19D);

        RebounderRenderUtil.emitTriangle(positionMatrix, consumer, top, east, south, CORE_RED, CORE_GREEN, CORE_BLUE, 210);
        RebounderRenderUtil.emitTriangle(positionMatrix, consumer, top, south, west, CORE_RED, CORE_GREEN, CORE_BLUE, 210);
        RebounderRenderUtil.emitTriangle(positionMatrix, consumer, top, west, north, CORE_RED, CORE_GREEN, CORE_BLUE, 210);
        RebounderRenderUtil.emitTriangle(positionMatrix, consumer, top, north, east, CORE_RED, CORE_GREEN, CORE_BLUE, 210);
        RebounderRenderUtil.emitTriangle(positionMatrix, consumer, bottom, south, east, CORE_RED, CORE_GREEN, CORE_BLUE, 165);
        RebounderRenderUtil.emitTriangle(positionMatrix, consumer, bottom, west, south, CORE_RED, CORE_GREEN, CORE_BLUE, 165);
        RebounderRenderUtil.emitTriangle(positionMatrix, consumer, bottom, north, west, CORE_RED, CORE_GREEN, CORE_BLUE, 165);
        RebounderRenderUtil.emitTriangle(positionMatrix, consumer, bottom, east, north, CORE_RED, CORE_GREEN, CORE_BLUE, 165);
    }
}
