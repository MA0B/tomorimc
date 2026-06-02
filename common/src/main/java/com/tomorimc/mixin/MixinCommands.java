package com.tomorimc.mixin;

import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tomorimc.DiscordWebSocket;

@Mixin(Commands.class)
public class MixinCommands {

    @Inject(method = "performPrefixedCommand", at = @At("HEAD"))
    public void onCommandExecuted(CommandSourceStack source, String command, CallbackInfoReturnable<Integer> cir) {
        if (source.getEntity() instanceof ServerPlayer player) {
            String playerName = player.getGameProfile().name();
            DiscordWebSocket.getInstance().sendCommand(playerName, command);
            
            // Send command log to Discord
            String escapedCommand = command.replace("\"", "\\\"");
            DiscordWebSocket.getInstance().sendMessage(
                String.format("{\"event\":\"command_log\", \"player\":\"%s\", \"command\":\"%s\"}", playerName, escapedCommand)
            );
        }
    }
}
