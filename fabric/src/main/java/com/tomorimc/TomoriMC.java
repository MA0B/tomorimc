package com.tomorimc;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

public class TomoriMC implements ModInitializer {
    
    @Override
    public void onInitialize() {
        Constants.LOG.info("Inicializando TomoriMC no Fabric!");
        CommonClass.init();
        
        // 1. Escutando o Chat + confirmação sutil para o remetente
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            DiscordWebSocket.getInstance().sendChat(sender.getGameProfile().getName(), message.signedContent());
            // Confirmação sutil só para quem mandou a mensagem
            sender.sendSystemMessage(
                Component.literal("  §8§o✓ Discord").withStyle(Style.EMPTY
                    .withColor(net.minecraft.network.chat.TextColor.parseColor("#555555").getOrThrow())
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                        Component.literal("§7Sua mensagem foi enviada para o canal do Discord.")))),
                true // overlay = true → aparece na action bar (acima do hotbar), super sutil
            );
        });

        // 2. Escutando quando o jogador entra
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            String playerName = handler.player.getGameProfile().getName();
            String playerUuid = handler.player.getUUID().toString();
            boolean isOp = server.getPlayerList().isOp(handler.player.getGameProfile());
            DiscordWebSocket.getInstance().sendJoin(playerName);
            // Sincroniza status de OP com o Discord para o cargo MineOperator
            String discordId = LinkManager.getDiscordId(playerUuid);
            if (discordId != null) {
                String opJson = String.format("{\"event\":\"op_sync\", \"discord_id\":\"%s\", \"is_op\":%s, \"player\":\"%s\"}", discordId, isOp, playerName);
                DiscordWebSocket.getInstance().sendMessage(opJson);
            }
        });

        // 3. Escutando quando o jogador sai
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            DiscordWebSocket.getInstance().sendQuit(handler.player.getGameProfile().getName());
        });

        // 3.5. Servidor Iniciado / Parando
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            DiscordWebSocket.getInstance().setServer(server);
            DiscordWebSocket.getInstance().sendMessage("{\"event\":\"server_status\", \"status\":\"started\"}");
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DiscordWebSocket.getInstance().sendMessage("{\"event\":\"server_status\", \"status\":\"stopped\"}");
            DiscordWebSocket.getInstance().setServer(null);
        });

        // 4. Comando In-Game: /tomorimc
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(net.minecraft.commands.Commands.literal("tomorimc")
                .executes(context -> {
                    Component msg = Component.literal("\n§9============== §b✨ TomoriMC ✨ §9==============\n")
                            .append(Component.literal("§7Bem-vindo ao sistema de integração TomoriMC!\n\n"))
                            .append(Component.literal("§fComandos disponíveis:\n"))
                            .append(Component.literal("§b/tomorimc link-account §8- §7Gere um PIN para vincular sua conta.\n")
                                .withStyle(Style.EMPTY
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§aClique para rodar /tomorimc link-account")))
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tomorimc link-account"))))
                            .append(Component.literal("§b/tomorimc link §8- §7Gera o comando para vincular este servidor ao Discord.\n")
                                .withStyle(Style.EMPTY
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§aClique para rodar /tomorimc link")))
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tomorimc link"))))
                            .append(Component.literal("§b/tomorimc vote-trigger <jogador> <site> §8- §7[ADMIN] Dispara um anúncio de voto no Discord.\n"))
                            .append(Component.literal("§b/tomorimc status §8- §7Verifica a conexão com o bot do Discord.\n")
                                .withStyle(Style.EMPTY
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§aClique para rodar /tomorimc status")))
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tomorimc status"))))
                            .append(Component.literal("§b/tomorimc reload §8- §7Recarrega configurações e WebSocket.\n")
                                .withStyle(Style.EMPTY
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§aClique para rodar /tomorimc reload")))
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tomorimc reload"))))
                            .append(Component.literal("\n§9==========================================="));
                    context.getSource().sendSuccess(() -> msg, false);
                    return 1;
                })
                .then(net.minecraft.commands.Commands.literal("link").executes(context -> {
                    String serverId = TomoreConfig.getServerId();
                    String cmdToCopy = "/tomorimc link server_id:" + serverId + " canal:#seu-canal";
                    
                    Component msg = Component.literal("\n§9================================\n")
                            .append(Component.literal("§b✨ TomoriMC Link ✨\n\n"))
                            .append(Component.literal("§fPara conectar este servidor ao seu Discord, clique no botão abaixo para copiar o comando e cole no Discord:\n\n"))
                            .append(Component.literal("§a[CLIQUE AQUI PARA COPIAR]")
                                .withStyle(Style.EMPTY
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Clique para copiar!")))
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, cmdToCopy))
                                    .withBold(true)))
                            .append(Component.literal("\n\n§9================================"));

                    context.getSource().sendSuccess(() -> msg, false);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("link-account").executes(context -> {
                    try {
                        String uuid = context.getSource().getPlayerOrException().getUUID().toString();
                        String pin = LinkManager.generatePinFor(uuid);
                        
                        Component msg = Component.literal("\n§9================================\n")
                                .append(Component.literal("§b✨ Vínculo de Conta ✨\n\n"))
                                .append(Component.literal("§fSeu PIN de vinculação é: §e§l" + pin + "\n\n"))
                                .append(Component.literal("§fVá no Discord, no canal do servidor, e digite:\n"))
                                .append(Component.literal("§a/link pin:" + pin + "\n\n")
                                    .withStyle(Style.EMPTY
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Clique para copiar!")))
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, "/link pin:" + pin))))
                                .append(Component.literal("§7(Este PIN expira se o servidor reiniciar)\n§9================================"));

                        context.getSource().sendSuccess(() -> msg, false);
                    } catch (Exception e) {
                        context.getSource().sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
                    }
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("status").executes(context -> {
                    boolean connected = DiscordWebSocket.getInstance().isConnected();
                    if (connected) {
                        context.getSource().sendSuccess(() -> Component.literal("§9[TomoriMC] §fStatus: §aConectado ao Bot do Discord!"), false);
                    } else {
                        context.getSource().sendSuccess(() -> Component.literal("§9[TomoriMC] §fStatus: §cDesconectado do Bot (Tentando reconectar...)"), false);
                    }
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("vote-trigger")
                    .requires(source -> source.hasPermission(2))
                    .then(net.minecraft.commands.Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .then(net.minecraft.commands.Commands.argument("service", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                            .executes(context -> {
                                String player = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "player");
                                String service = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "service");
                                
                                String json = String.format("{\"event\":\"vote\", \"player\":\"%s\", \"service\":\"%s\"}", player, service.replace("\"", "\\\""));
                                DiscordWebSocket.getInstance().sendMessage(json);
                                
                                context.getSource().sendSuccess(() -> Component.literal("§a[TomoriMC] Evento de voto enviado para o Discord com sucesso!"), true);
                                return 1;
                            })
                        )
                    )
                )
                .then(net.minecraft.commands.Commands.literal("reload")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> {
                    TomoreConfig.loadConfig();
                    DiscordWebSocket.getInstance().connect();
                    context.getSource().sendSuccess(() -> Component.literal("§9[TomoriMC] §aConfigurações e WebSocket recarregados!"), false);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("execute")
                    .requires(source -> source.hasPermission(2))
                    .then(net.minecraft.commands.Commands.argument("command", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(context -> {
                            String cmd = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "command");
                            context.getSource().getServer().getCommands().performPrefixedCommand(context.getSource(), cmd);
                            context.getSource().sendSuccess(() -> Component.literal("§9[TomoriMC] §aComando executado."), true);
                            return 1;
                        })
                    )
                )
            );
        });
    }
}
