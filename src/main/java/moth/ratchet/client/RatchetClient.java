package moth.ratchet.client;

import moth.ratchet.RebounderVisualState;
import moth.ratchet.RatchetMod;
import moth.ratchet.client.render.LatchetRebounderProjectileRenderer;
import moth.ratchet.client.render.LatchetRebounderTraceRenderer;
import moth.ratchet.client.render.RatchetIndexerAnchorRenderer;
import moth.ratchet.entity.ModEntities;
import moth.ratchet.item.ModItems;
import moth.ratchet.item.RatchetIndexerItem;
import moth.ratchet.network.RatchetNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.util.math.Vec3d;

public class RatchetClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModelPredicateProviderRegistry.register(
                ModItems.RATCHET_INDEXER,
                RatchetMod.id("tuned"),
                (stack, world, entity, seed) -> RatchetIndexerItem.isTuned(stack) ? 1.0F : 0.0F
        );
        EntityRendererRegistry.register(ModEntities.LATCHET_REBOUNDER_PROJECTILE, LatchetRebounderProjectileRenderer::new);
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.world == null) {
                RebounderVisualState.clearClientWorldTime();
            } else {
                RebounderVisualState.setClientWorldTime(client.world.getTime());
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(LatchetRebounderInputHandler::tick);
        ClientTickEvents.END_CLIENT_TICK.register(LatchetRebounderTraceRenderer::tick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(LatchetRebounderTraceRenderer::render);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(RatchetIndexerAnchorRenderer::render);
        ClientPlayNetworking.registerGlobalReceiver(RatchetNetworking.LATCHET_REBOUNDER_TRACE_ID, (client, handler, buf, responseSender) -> {
            Vec3d start = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3d end = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            int color = buf.readInt();
            client.execute(() -> LatchetRebounderTraceRenderer.addTrace(start, end, color));
        });
        ClientPlayNetworking.registerGlobalReceiver(RatchetNetworking.LATCHET_REBOUNDER_IMPACT_ID, (client, handler, buf, responseSender) -> {
            Vec3d position = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3d normal = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            boolean finalBurst = buf.readBoolean();
            int color = buf.readInt();
            client.execute(() -> LatchetRebounderTraceRenderer.addImpactBurst(position, normal, finalBurst, color));
        });
    }
}
