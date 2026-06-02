package com.tomorimc;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

@Mod(Constants.MOD_ID)
public class TomoriMC {
    
    public TomoriMC() {
        Constants.LOG.info("Inicializando TomoriMC no Forge!");
        CommonClass.init();
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        DiscordWebSocket.getInstance().sendChat(event.getPlayer().getGameProfile().getName(), event.getRawText());
        event.getPlayer().sendSystemMessage(
            Component.literal("  §8§o✓ Discord").withStyle(Style.EMPTY
                .withColor(net.minecraft.network.chat.TextColor.parseColor("#555555"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    Component.literal("§7Sua mensagem foi enviada para o canal do Discord.")))),
            true
        );
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String playerName = player.getGameProfile().getName();
            String playerUuid = player.getUUID().toString();
            boolean isOp = player.server.getPlayerList().isOp(player.getGameProfile());
            DiscordWebSocket.getInstance().sendJoin(playerName);
            
            String discordId = LinkManager.getDiscordId(playerUuid);
            if (discordId != null) {
                String opJson = String.format("{\"event\":\"op_sync\", \"discord_id\":\"%s\", \"is_op\":%s, \"player\":\"%s\"}", discordId, isOp, playerName);
                DiscordWebSocket.getInstance().sendMessage(opJson);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DiscordWebSocket.getInstance().sendQuit(player.getGameProfile().getName());
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        DiscordWebSocket.getInstance().setServer(event.getServer());
        DiscordWebSocket.getInstance().sendMessage("{\"event\":\"server_status\", \"status\":\"started\"}");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        DiscordWebSocket.getInstance().sendMessage("{\"event\":\"server_status\", \"status\":\"stopped\"}");
        DiscordWebSocket.getInstance().setServer(null);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        
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
    }
}