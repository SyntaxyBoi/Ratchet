package moth.ratchet.mixin;

import moth.ratchet.item.LatchetRebounderItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerRebounderMixin {
    @Shadow @Final private MinecraftClient client;

    @Shadow
    public abstract void cancelBlockBreaking();

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void ratchet$preventRebounderBlockAttack(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (isHoldingLatchetRebounder()) {
            cancelBlockBreaking();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void ratchet$preventRebounderBlockBreaking(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (isHoldingLatchetRebounder()) {
            cancelBlockBreaking();
            cir.setReturnValue(false);
        }
    }

    private boolean isHoldingLatchetRebounder() {
        PlayerEntity player = this.client.player;
        if (player == null) {
            return false;
        }

        ItemStack stack = player.getMainHandStack();
        return stack.getItem() instanceof LatchetRebounderItem;
    }
}
