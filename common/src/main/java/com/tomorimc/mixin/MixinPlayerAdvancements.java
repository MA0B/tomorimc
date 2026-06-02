package com.tomorimc.mixin;

import com.tomorimc.DiscordWebSocket;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.advancements.Advancement;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class MixinPlayerAdvancements {
    @Shadow private ServerPlayer player;

    @Inject(method = "award", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerAdvancements;markForVisibilityUpdate(Lnet/minecraft/advancements/Advancement;)V"))
    public void onAdvancementAwarded(Advancement advancement, String criterionKey, CallbackInfoReturnable<Boolean> cir) {
        if (advancement.getDisplay() != null && advancement.getDisplay().shouldAnnounceChat()) {
            DiscordWebSocket.getInstance().sendAdvancement(player.getGameProfile().getName(), advancement.getDisplay().getTitle().getString());
        }
    }
}
