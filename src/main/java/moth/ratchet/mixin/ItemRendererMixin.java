package moth.ratchet.mixin;

import moth.ratchet.item.ModItems;
import net.minecraft.client.render.item.ItemModels;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    private static final ModelIdentifier RATCHET_DRIVER_HANDHELD =
            new ModelIdentifier("ratchet", "ratchet_driver_handheld", "inventory");
    private static final ModelIdentifier OVERCRANK_RATCHET_DRIVER_HANDHELD =
            new ModelIdentifier("ratchet", "overcrank_ratchet_driver_handheld", "inventory");
    private static final ModelIdentifier HEAVY_RATCHET_DRIVER_HANDHELD =
            new ModelIdentifier("ratchet", "heavy_ratchet_driver_handheld", "inventory");

    @Shadow @Final private ItemModels models;

    @Shadow
    public abstract BakedModel getModel(ItemStack stack, @Nullable World world, @Nullable LivingEntity entity, int seed);

    @ModifyVariable(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private BakedModel ratchet$swapHandheldModel(BakedModel value, ItemStack stack, ModelTransformationMode mode) {
        if (mode == ModelTransformationMode.GUI) {
            return value;
        }

        if (stack.isOf(ModItems.RATCHET_DRIVER)) {
            return getHandheldModelOrFallback(RATCHET_DRIVER_HANDHELD, value);
        }
        if (stack.isOf(ModItems.OVERCRANK_RATCHET_DRIVER)) {
            return getHandheldModelOrFallback(OVERCRANK_RATCHET_DRIVER_HANDHELD, value);
        }
        if (stack.isOf(ModItems.HEAVY_RATCHET_DRIVER)) {
            return getHandheldModelOrFallback(HEAVY_RATCHET_DRIVER_HANDHELD, value);
        }
        return value;
    }

    private BakedModel getHandheldModelOrFallback(ModelIdentifier id, BakedModel fallback) {
        BakedModel handheld = models.getModelManager().getModel(id);
        if (handheld != null && handheld != models.getModelManager().getMissingModel()) {
            return handheld;
        }
        return fallback;
    }
}
