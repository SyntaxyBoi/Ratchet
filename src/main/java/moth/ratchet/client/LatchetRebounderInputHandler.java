package moth.ratchet.client;

import moth.ratchet.enchant.ModEnchantments;
import moth.ratchet.item.LatchetRebounderItem;
import moth.ratchet.network.RatchetNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Hand;

public final class LatchetRebounderInputHandler {
    private static boolean mainHandRapidActive;
    private static boolean offHandRapidActive;

    private LatchetRebounderInputHandler() {}

    public static void tick(MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player == null || client.world == null) {
            mainHandRapidActive = false;
            offHandRapidActive = false;
            return;
        }

        boolean usePressed = client.currentScreen == null && client.options.useKey.isPressed();
        boolean mainHandCaptures = usePressed && LatchetRebounderItem.shouldCaptureRapidUse(player, client.world, Hand.MAIN_HAND, client.crosshairTarget);
        boolean offHandCaptures = usePressed
                && !mainHandCaptures
                && LatchetRebounderItem.shouldCaptureRapidUse(player, client.world, Hand.OFF_HAND, client.crosshairTarget);

        mainHandRapidActive = syncRapidHand(player, Hand.MAIN_HAND, mainHandCaptures, mainHandRapidActive);
        offHandRapidActive = syncRapidHand(player, Hand.OFF_HAND, offHandCaptures, offHandRapidActive);
    }

    public static boolean fireCapturedHandsNow(MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return false;
        }

        boolean mainHandCaptures = LatchetRebounderItem.shouldCaptureRapidUse(player, client.world, Hand.MAIN_HAND, client.crosshairTarget);
        boolean offHandCaptures = !mainHandCaptures
                && LatchetRebounderItem.shouldCaptureRapidUse(player, client.world, Hand.OFF_HAND, client.crosshairTarget);

        if (mainHandCaptures) {
            sendInput(Hand.MAIN_HAND, RatchetNetworking.RebounderAction.FIRE);
            if (!mainHandRapidActive) {
                player.swingHand(Hand.MAIN_HAND);
            }
        }

        if (offHandCaptures) {
            sendInput(Hand.OFF_HAND, RatchetNetworking.RebounderAction.FIRE);
            if (!offHandRapidActive) {
                player.swingHand(Hand.OFF_HAND);
            }
        }

        return mainHandCaptures || offHandCaptures;
    }

    public static boolean shouldSuppressVanillaUse(MinecraftClient client) {
        PlayerEntity player = client.player;
        return player != null
                && client.world != null
                && client.currentScreen == null
                && client.options.useKey.isPressed()
                && (LatchetRebounderItem.shouldCaptureRapidUse(player, client.world, Hand.MAIN_HAND, client.crosshairTarget)
                || LatchetRebounderItem.shouldCaptureRapidUse(player, client.world, Hand.OFF_HAND, client.crosshairTarget));
    }

    private static boolean syncRapidHand(PlayerEntity player, Hand hand, boolean shouldFire, boolean rapidActive) {
        if (!shouldFire) {
            releaseRapid(hand, rapidActive);
            return false;
        }

        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof LatchetRebounderItem) || !ModEnchantments.hasRapidFire(stack)) {
            releaseRapid(hand, rapidActive);
            return false;
        }

        sendInput(hand, RatchetNetworking.RebounderAction.FIRE);
        if (!rapidActive) {
            player.swingHand(hand);
        }
        return true;
    }

    private static void releaseRapid(Hand hand, boolean rapidActive) {
        if (rapidActive) {
            sendInput(hand, RatchetNetworking.RebounderAction.STOP);
        }
    }

    private static void sendInput(Hand hand, RatchetNetworking.RebounderAction action) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeEnumConstant(hand);
        buf.writeEnumConstant(action);
        ClientPlayNetworking.send(RatchetNetworking.LATCHET_REBOUNDER_FIRE_ID, buf);
    }
}
