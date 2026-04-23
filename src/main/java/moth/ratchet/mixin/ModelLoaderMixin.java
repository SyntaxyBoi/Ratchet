package moth.ratchet.mixin;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(ModelLoader.class)
public abstract class ModelLoaderMixin {
    @Shadow
    protected abstract void addModel(ModelIdentifier modelId);

    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/model/ModelLoader;addModel(Lnet/minecraft/client/util/ModelIdentifier;)V",
                    ordinal = 2
            )
    )
    private void ratchet$registerHandheldModels(BlockColors blockColors, Profiler profiler,
                                                Map<?, ?> jsonUnbakedModels, Map<?, ?> blockStates,
                                                CallbackInfo ci) {
        addModel(new ModelIdentifier("ratchet", "ratchet_driver_handheld", "inventory"));
        addModel(new ModelIdentifier("ratchet", "overcrank_ratchet_driver_handheld", "inventory"));
        addModel(new ModelIdentifier("ratchet", "heavy_ratchet_driver_handheld", "inventory"));
    }
}
