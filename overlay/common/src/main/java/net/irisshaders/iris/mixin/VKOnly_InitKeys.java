package net.irisshaders.iris.mixin;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.backend.IrisVulkanBootstrap;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class VKOnly_InitKeys {
    @Unique
    private static boolean iris$initialized;

    @Inject(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Options;<init>(Lnet/minecraft/client/Minecraft;Ljava/io/File;)V"))
    private void iris$beforeLoadOptions(CallbackInfo ci) {
        if (iris$initialized) {
            return;
        }

        iris$initialized = true;
        new Iris().onEarlyInitialize();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void iris$afterMinecraftInit(CallbackInfo ci) {
        IrisVulkanBootstrap.onMinecraftInitialized();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void iris$handleKeys(CallbackInfo ci) {
        Iris.handleKeybinds((Minecraft) (Object) this);
    }
}
