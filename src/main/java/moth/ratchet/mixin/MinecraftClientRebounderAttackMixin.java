package moth.ratchet.mixin;

import moth.ratchet.client.LatchetRebounderInputHandler;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientRebounderAttackMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void ratchet$consumeLatchetRebounderAttack(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        PlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }

        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof LatchetRebounderItem)) {
            return;
        }

        cir.setReturnValue(true);
        LatchetRebounderItem item = (LatchetRebounderItem) stack.getItem();
        if (ModEnchantments.hasRapidFire(stack) || client.currentScreen != null || item.isCoolingDown(stack, client.world.getTime())) {
            return;
        }

        if (item.usesStoredCharge(stack) && item.getCurrentCharge(stack) <= 0) {
            return;
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeEnumConstant(Hand.MAIN_HAND);
        buf.writeEnumConstant(RatchetNetworking.RebounderAction.FIRE);
        ClientPlayNetworking.send(RatchetNetworking.LATCHET_REBOUNDER_FIRE_ID, buf);
        player.swingHand(Hand.MAIN_HAND);
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void ratchet$preventVanillaRebounderUse(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        PlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }

        if (LatchetRebounderInputHandler.shouldSuppressVanillaUse(client)) {
            LatchetRebounderInputHandler.fireCapturedHandsNow(client);
            ci.cancel();
        }
    }
}
