package moth.ratchet.network;

import moth.ratchet.RatchetMod;
import moth.ratchet.item.LatchetRebounderItem;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class RatchetNetworking {
    public static final Identifier LATCHET_REBOUNDER_FIRE_ID = RatchetMod.id("latchet_rebounder_fire");
    public static final Identifier LATCHET_REBOUNDER_TRACE_ID = RatchetMod.id("latchet_rebounder_trace");
    public static final Identifier LATCHET_REBOUNDER_IMPACT_ID = RatchetMod.id("latchet_rebounder_impact");
    private static final int DEFAULT_TRACE_COLOR = 0xFFD654;
    private static final double TRACE_BROADCAST_RANGE = 128.0D;
    private static final double IMPACT_BROADCAST_RANGE = 160.0D;

    private RatchetNetworking() {}

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(LATCHET_REBOUNDER_FIRE_ID, (server, player, handler, buf, responseSender) -> {
            Hand hand = buf.readEnumConstant(Hand.class);
            RebounderAction action = buf.readEnumConstant(RebounderAction.class);
            server.execute(() -> handleLatchetRebounderFire(player, hand, action));
        });
    }

    public static void sendHitscanTrace(ServerWorld world, Vec3d start, Vec3d end) {
        sendHitscanTrace(world, start, end, DEFAULT_TRACE_COLOR);
    }

    public static void sendHitscanTrace(ServerWorld world, Vec3d start, Vec3d end, int color) {
        Vec3d center = start.lerp(end, 0.5D);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getPos().squaredDistanceTo(center) > (TRACE_BROADCAST_RANGE * TRACE_BROADCAST_RANGE)) {
                continue;
            }

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeDouble(start.x);
            buf.writeDouble(start.y);
            buf.writeDouble(start.z);
            buf.writeDouble(end.x);
            buf.writeDouble(end.y);
            buf.writeDouble(end.z);
            buf.writeInt(color);
            ServerPlayNetworking.send(player, LATCHET_REBOUNDER_TRACE_ID, buf);
        }
    }

    public static void sendImpactBurst(ServerWorld world, Vec3d position, Vec3d normal, boolean finalBurst) {
        sendImpactBurst(world, position, normal, finalBurst, DEFAULT_TRACE_COLOR);
    }

    public static void sendImpactBurst(ServerWorld world, Vec3d position, Vec3d normal, boolean finalBurst, int color) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getPos().squaredDistanceTo(position) > (IMPACT_BROADCAST_RANGE * IMPACT_BROADCAST_RANGE)) {
                continue;
            }

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeDouble(position.x);
            buf.writeDouble(position.y);
            buf.writeDouble(position.z);
            buf.writeDouble(normal.x);
            buf.writeDouble(normal.y);
            buf.writeDouble(normal.z);
            buf.writeBoolean(finalBurst);
            buf.writeInt(color);
            ServerPlayNetworking.send(player, LATCHET_REBOUNDER_IMPACT_ID, buf);
        }
    }

    private static void handleLatchetRebounderFire(ServerPlayerEntity player, Hand hand, RebounderAction action) {
        if (player == null || !player.isAlive()) {
            return;
        }

        if (!(player.getStackInHand(hand).getItem() instanceof LatchetRebounderItem item)) {
            return;
        }

        if (action == RebounderAction.STOP) {
            item.stopFiring(player, hand);
            return;
        }

        item.tryFire(player, hand);
    }

    public enum RebounderAction {
        FIRE,
        STOP
    }
}
