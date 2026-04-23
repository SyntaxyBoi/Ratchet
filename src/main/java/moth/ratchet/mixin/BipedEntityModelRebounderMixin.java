package moth.ratchet.mixin;

import moth.ratchet.item.ModItems;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelRebounderMixin<T extends LivingEntity> {
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart head;

    @Inject(method = "positionRightArm", at = @At("TAIL"))
    private void ratchet$holdRebounderRightArm(T entity, CallbackInfo ci) {
        if (getRebounderHoldingArm(entity) == Arm.RIGHT) {
            holdRebounder(this.rightArm, this.leftArm, this.head, true);
        }
    }

    @Inject(method = "positionLeftArm", at = @At("TAIL"))
    private void ratchet$holdRebounderLeftArm(T entity, CallbackInfo ci) {
        if (getRebounderHoldingArm(entity) == Arm.LEFT) {
            holdRebounder(this.rightArm, this.leftArm, this.head, false);
        }
    }

    @Unique
    @Nullable
    private Arm getRebounderHoldingArm(T entity) {
        ItemStack mainHandStack = entity.getMainHandStack();
        if (mainHandStack.isOf(ModItems.LATCHET_REBOUNDER)) {
            return entity.getMainArm();
        }

        ItemStack offHandStack = entity.getOffHandStack();
        if (offHandStack.isOf(ModItems.LATCHET_REBOUNDER)) {
            return entity.getMainArm() == Arm.RIGHT ? Arm.LEFT : Arm.RIGHT;
        }

        return null;
    }

    @Unique
    private static void holdRebounder(ModelPart rightArm, ModelPart leftArm, ModelPart head, boolean rightArmed) {
        ModelPart holdingArm = rightArmed ? rightArm : leftArm;
        holdingArm.yaw = (rightArmed ? -0.3F : 0.3F) + head.yaw;
        holdingArm.pitch = (float) (-Math.PI / 2) + head.pitch + 0.1F;
    }
}
