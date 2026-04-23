package moth.ratchet.client.render;

import moth.ratchet.item.ModItems;
import moth.ratchet.item.RatchetIndexerItem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RatchetIndexerAnchorRenderer {
    private static final double BOX_HALF_SIZE = 0.0625D;
    private static final double TRAIL_LENGTH = 0.92D;
    private static final double PULSE_LENGTH = 0.18D;
    private static final float EDGE_RADIUS = 0.0105F;
    private static final float TRAIL_RADIUS = 0.018F;
    private static final float PULSE_RADIUS = 0.024F;
    private static final int TUBE_SIDES = 6;

    private static final int UNTUNED_RED = 255;
    private static final int UNTUNED_GREEN = 214;
    private static final int UNTUNED_BLUE = 84;

    private static final int TUNED_RED = 92;
    private static final int TUNED_GREEN = 244;
    private static final int TUNED_BLUE = 218;

    private RatchetIndexerAnchorRenderer() {}

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null
                || client.world == null
                || context.matrixStack() == null
                || context.consumers() == null) {
            return;
        }

        Map<BlockPos, Boolean> anchors = collectVisibleAnchors(client.player.getInventory());
        if (anchors.isEmpty()) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        Vec3d cameraPos = context.camera().getPos();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        VertexConsumer consumer = context.consumers().getBuffer(RenderLayer.getLightning());
        double time = client.world.getTime() + context.tickDelta();

        for (Map.Entry<BlockPos, Boolean> entry : anchors.entrySet()) {
            renderAnchor(positionMatrix, consumer, entry.getKey(), entry.getValue(), time);
        }

        matrices.pop();
    }

    private static Map<BlockPos, Boolean> collectVisibleAnchors(PlayerInventory inventory) {
        Map<BlockPos, Boolean> anchors = new LinkedHashMap<>();

        for (ItemStack stack : inventory.main) {
            collectAnchor(stack, inventory, anchors);
        }

        for (ItemStack stack : inventory.offHand) {
            collectAnchor(stack, inventory, anchors);
        }

        return anchors;
    }

    private static void collectAnchor(ItemStack stack, PlayerInventory inventory, Map<BlockPos, Boolean> anchors) {
        if (!stack.isOf(ModItems.RATCHET_INDEXER) || !RatchetIndexerItem.shouldRenderAnchorFor(stack, inventory.player)) {
            return;
        }

        BlockPos anchorPos = RatchetIndexerItem.getStoredAnchorPos(stack);
        if (anchorPos == null) {
            return;
        }

        anchors.merge(anchorPos.toImmutable(), RatchetIndexerItem.isTuned(stack), (existing, incoming) -> existing || incoming);
    }

    private static void renderAnchor(Matrix4f positionMatrix, VertexConsumer consumer, BlockPos anchorPos, boolean tuned, double time) {
        int red = tuned ? TUNED_RED : UNTUNED_RED;
        int green = tuned ? TUNED_GREEN : UNTUNED_GREEN;
        int blue = tuned ? TUNED_BLUE : UNTUNED_BLUE;

        Vec3d center = Vec3d.ofCenter(anchorPos);
        float pulse = 0.65F + (0.35F * (0.5F + 0.5F * MathHelper.sin((float) (time * 0.16D))));
        float edgeAlpha = Math.round(MathHelper.lerp(pulse, 110.0F, 205.0F));
        float trailAlpha = Math.round(MathHelper.lerp(pulse, 45.0F, 125.0F));

        renderBox(positionMatrix, consumer, center, red, green, blue, Math.round(edgeAlpha), EDGE_RADIUS * (0.92F + (0.16F * pulse)));
        renderTrails(positionMatrix, consumer, center, red, green, blue, Math.round(trailAlpha), Math.round(edgeAlpha), time);
    }

    private static void renderBox(Matrix4f positionMatrix, VertexConsumer consumer, Vec3d center,
                                  int red, int green, int blue, int alpha, float radius) {
        double minX = center.x - BOX_HALF_SIZE;
        double maxX = center.x + BOX_HALF_SIZE;
        double minY = center.y - BOX_HALF_SIZE;
        double maxY = center.y + BOX_HALF_SIZE;
        double minZ = center.z - BOX_HALF_SIZE;
        double maxZ = center.z + BOX_HALF_SIZE;

        Vec3d bottomNorthWest = new Vec3d(minX, minY, minZ);
        Vec3d bottomNorthEast = new Vec3d(maxX, minY, minZ);
        Vec3d bottomSouthWest = new Vec3d(minX, minY, maxZ);
        Vec3d bottomSouthEast = new Vec3d(maxX, minY, maxZ);
        Vec3d topNorthWest = new Vec3d(minX, maxY, minZ);
        Vec3d topNorthEast = new Vec3d(maxX, maxY, minZ);
        Vec3d topSouthWest = new Vec3d(minX, maxY, maxZ);
        Vec3d topSouthEast = new Vec3d(maxX, maxY, maxZ);

        emitEdge(positionMatrix, consumer, bottomNorthWest, bottomNorthEast, radius, red, green, blue, alpha);
        emitEdge(positionMatrix, consumer, bottomNorthEast, bottomSouthEast, radius, red, green, blue, alpha);
        emitEdge(positionMatrix, consumer, bottomSouthEast, bottomSouthWest, radius, red, green, blue, alpha);
        emitEdge(positionMatrix, consumer, bottomSouthWest, bottomNorthWest, radius, red, green, blue, alpha);

        emitEdge(positionMatrix, consumer, topNorthWest, topNorthEast, radius, red, green, blue, alpha);
        emitEdge(positionMatrix, consumer, topNorthEast, topSouthEast, radius, red, green, blue, alpha);
        emitEdge(positionMatrix, consumer, topSouthEast, topSouthWest, radius, red, green, blue, alpha);
        emitEdge(positionMatrix, consumer, topSouthWest, topNorthWest, radius, red, green, blue, alpha);

        emitEdge(positionMatrix, consumer, bottomNorthWest, topNorthWest, radius, red, green, blue, alpha);
        emitEdge(positionMatrix, consumer, bottomNorthEast, topNorthEast, radius, red, green, blue, alpha);
        emitEdge(positionMatrix, consumer, bottomSouthEast, topSouthEast, radius, red, green, blue, alpha);
        emitEdge(positionMatrix, consumer, bottomSouthWest, topSouthWest, radius, red, green, blue, alpha);
    }

    private static void renderTrails(Matrix4f positionMatrix, VertexConsumer consumer, Vec3d center,
                                     int red, int green, int blue, int trailAlpha, int pulseAlpha, double time) {
        Vec3d topStart = center.add(0.0D, BOX_HALF_SIZE, 0.0D);
        Vec3d topEnd = center.add(0.0D, BOX_HALF_SIZE + TRAIL_LENGTH, 0.0D);
        Vec3d bottomStart = center.add(0.0D, -BOX_HALF_SIZE, 0.0D);
        Vec3d bottomEnd = center.add(0.0D, -(BOX_HALF_SIZE + TRAIL_LENGTH), 0.0D);

        RebounderRenderUtil.emitTubeSegment(
                positionMatrix,
                consumer,
                topStart,
                topEnd,
                TRAIL_RADIUS * 0.95F,
                TRAIL_RADIUS * 0.45F,
                red,
                green,
                blue,
                Math.max(0, trailAlpha),
                Math.max(0, trailAlpha - 55),
                TUBE_SIDES
        );
        RebounderRenderUtil.emitTubeSegment(
                positionMatrix,
                consumer,
                bottomStart,
                bottomEnd,
                TRAIL_RADIUS * 0.95F,
                TRAIL_RADIUS * 0.45F,
                red,
                green,
                blue,
                Math.max(0, trailAlpha),
                Math.max(0, trailAlpha - 55),
                TUBE_SIDES
        );

        double cycle = time * 0.045D;
        renderPulse(positionMatrix, consumer, center, true, cycle, red, green, blue, pulseAlpha);
        renderPulse(positionMatrix, consumer, center, false, cycle, red, green, blue, pulseAlpha);
    }

    private static void renderPulse(Matrix4f positionMatrix, VertexConsumer consumer, Vec3d center, boolean upward,
                                    double cycle, int red, int green, int blue, int alpha) {
        double offset = cycle - Math.floor(cycle);
        double distance = BOX_HALF_SIZE + (offset * TRAIL_LENGTH);
        Vec3d head = center.add(0.0D, upward ? distance : -distance, 0.0D);
        Vec3d tail = head.add(0.0D, upward ? -PULSE_LENGTH : PULSE_LENGTH, 0.0D);

        RebounderRenderUtil.emitTubeSegment(
                positionMatrix,
                consumer,
                tail,
                head,
                PULSE_RADIUS * 0.45F,
                PULSE_RADIUS,
                red,
                green,
                blue,
                Math.max(0, alpha - 130),
                Math.min(255, alpha + 20),
                TUBE_SIDES
        );
    }

    private static void emitEdge(Matrix4f positionMatrix, VertexConsumer consumer, Vec3d start, Vec3d end,
                                 float radius, int red, int green, int blue, int alpha) {
        RebounderRenderUtil.emitTubeSegment(
                positionMatrix,
                consumer,
                start,
                end,
                radius,
                radius,
                red,
                green,
                blue,
                alpha,
                alpha,
                TUBE_SIDES
        );
    }
}
