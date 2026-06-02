package com.tomorimc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class DiscordWebSocket implements WebSocket.Listener {
    private WebSocket webSocket;
    private static DiscordWebSocket instance;
    private MinecraftServer server;

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public static DiscordWebSocket getInstance() {
        if (instance == null) {
            instance = new DiscordWebSocket();
        }
        return instance;
    }

    public boolean isConnected() {
        return this.webSocket != null;
    }

    public void connect() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:8081"), this)
                .thenAccept(ws -> {
                    this.webSocket = ws;
                    Constants.LOG.info("Conectado ao Bot do Discord (Tomori) via WebSocket.");
                    
                    // Envia autenticação
                    String serverId = TomoreConfig.getServerId();
                    sendMessage("{\"event\":\"auth\", \"server_id\":\"" + serverId + "\"}");
                })
                .exceptionally(ex -> {
                    Constants.LOG.error("Falha ao conectar no Discord WebSocket: " + ex.getMessage());
                    return null;
                });
        } catch (Exception e) {
            Constants.LOG.error("Falha ao inicializar o WebSocket", e);
        }
    }

    public void sendMessage(String json) {
        if (this.webSocket != null) {
            this.webSocket.sendText(json, true);
        }
    }

    public void sendChat(String player, String message) {
        // Escapa aspas para JSON
        String escapedMsg = message.replace("\"", "\\\"");
        String json = String.format("{\"event\":\"chat\", \"player\":\"%s\", \"msg\":\"%s\"}", player, escapedMsg);
        sendMessage(json);
    }

    public void sendCommand(String player, String command) {
        String escapedCmd = command.replace("\"", "\\\"");
        String json = String.format("{\"event\":\"command\", \"player\":\"%s\", \"command\":\"%s\"}", player, escapedCmd);
        sendMessage(json);
    }

    public void sendJoin(String player) {
        sendMessage(String.format("{\"event\":\"player_join\", \"player\":\"%s\"}", player));
    }

    public void sendQuit(String player) {
        sendMessage(String.format("{\"event\":\"player_quit\", \"player\":\"%s\"}", player));
    }

    public void sendDeath(String player, String deathMsg) {
        String escapedMsg = deathMsg.replace("\"", "\\\"");
        sendMessage(String.format("{\"event\":\"player_death\", \"player\":\"%s\", \"msg\":\"%s\"}", player, escapedMsg));
    }

    public void sendAdvancement(String player, String advancementName) {
        String escapedMsg = advancementName.replace("\"", "\\\"");
        sendMessage(String.format("{\"event\":\"advancement\", \"player\":\"%s\", \"msg\":\"%s\"}", player, escapedMsg));
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        WebSocket.Listener.super.onOpen(webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        Constants.LOG.info("[Discord recebido]: " + data.toString());
        try {
            JsonObject json = JsonParser.parseString(data.toString()).getAsJsonObject();
            if (json.has("event") && this.server != null) {
                String event = json.get("event").getAsString();
                if (event.equals("discord_chat")) {
                    String user = json.get("user").getAsString();
                    String msg = json.get("msg").getAsString();
                    
                    String colorHex = json.has("color") ? json.get("color").getAsString() : "#FFFFFF";
                    
                    StringBuilder rolesStr = new StringBuilder();
                    if (json.has("roles")) {
                        for (com.google.gson.JsonElement r : json.getAsJsonArray("roles")) {
                            rolesStr.append(r.getAsString()).append(", ");
                        }
                    }
                    String rolesTxt = rolesStr.toString();
                    if (rolesTxt.endsWith(", ")) rolesTxt = rolesTxt.substring(0, rolesTxt.length() - 2);
                    
                    Component hoverComponent = Component.literal("§b" + user)
                        .append(Component.literal("\n§7Cargos: §f" + (rolesTxt.isEmpty() ? "Nenhum" : rolesTxt)));

                    Component chat = Component.literal("§9[Discord] ")
                            .append(Component.literal(user).withStyle(net.minecraft.network.chat.Style.EMPTY
                                .withColor(net.minecraft.network.chat.TextColor.parseColor(colorHex).getOrThrow())
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(hoverComponent))))
                            .append(Component.literal("§f: " + msg));
                            
                            
                    this.server.getPlayerList().broadcastSystemMessage(chat, false);
                    
                    // Envia a mensagem também para o Dynmap Web Chat de forma silenciosa
                    this.server.execute(() -> {
                        String cleanMsg = msg.replace("\"", "'").replace("\n", " ");
                        net.minecraft.commands.CommandSource customSource = new net.minecraft.commands.CommandSource() {
                            @Override
                            public void sendSystemMessage(Component message) { }
                            @Override
                            public boolean acceptsSuccess() { return false; }
                            @Override
                            public boolean acceptsFailure() { return false; }
                            @Override
                            public boolean shouldInformAdmins() { return false; }
                        };
                        net.minecraft.commands.CommandSourceStack source = this.server.createCommandSourceStack().withSource(customSource);
                        this.server.getCommands().performPrefixedCommand(source, "dynmap sendtoweb [Discord] " + user + ": " + cleanMsg);
                    });
                } else if (event.equals("list_request")) {
                    int count = this.server.getPlayerCount();
                    StringBuilder players = new StringBuilder();
                    for (net.minecraft.server.level.ServerPlayer p : this.server.getPlayerList().getPlayers()) {
                        players.append(p.getGameProfile().name()).append(", ");
                    }
                    String playersStr = players.toString();
                    if (playersStr.endsWith(", ")) playersStr = playersStr.substring(0, playersStr.length() - 2);
                    
                    String listMsg = "Jogadores online (" + count + "/" + this.server.getMaxPlayers() + "): " + (count > 0 ? playersStr : "Nenhum.");
                    sendChat("Servidor", listMsg);
                } else if (event.equals("console_command")) {
                    String command = json.get("command").getAsString();
                    String discordId = json.has("discord_id") ? json.get("discord_id").getAsString() : null;
                    
                    // Verifica permissão: o usuário do Discord precisa estar vinculado E ter OP no Minecraft
                    if (discordId != null) {
                        String linkedUuid = LinkManager.getMinecraftUuid(discordId);
                        if (linkedUuid == null) {
                            sendMessage(String.format("{\"event\":\"command_result\", \"result\":\"[X] Acesso negado: sua conta do Discord nao esta vinculada a nenhuma conta do Minecraft. Use /tomorimc link-account no jogo primeiro.\"}"));
                            return WebSocket.Listener.super.onText(webSocket, data, last);
                        }
                        
                        boolean isOp = false;
                        java.util.UUID jUuid = java.util.UUID.fromString(linkedUuid);
                        com.mojang.authlib.GameProfile profile = this.server.services().profileResolver()
                            .fetchById(jUuid).orElse(null);
                        
                        if (profile != null) {
                            isOp = this.server.getPlayerList().isOp(new net.minecraft.server.players.NameAndId(profile));
                        } else {
                            net.minecraft.server.level.ServerPlayer linkedPlayer = this.server.getPlayerList().getPlayer(jUuid);
                            if (linkedPlayer != null) {
                                isOp = this.server.getPlayerList().isOp(new net.minecraft.server.players.NameAndId(linkedPlayer.getGameProfile()));
                            }
                        }    
                        if (!isOp) {
                            sendMessage(String.format("{\"event\":\"command_result\", \"result\":\"[X] Acesso negado: seu jogador vinculado (%s) nao tem permissao de Operador (OP) no servidor Minecraft.\"}", linkedUuid));
                            return WebSocket.Listener.super.onText(webSocket, data, last);
                        }
                    }
                    
                    this.server.execute(() -> {
                        net.minecraft.commands.CommandSourceStack baseSource = this.server.createCommandSourceStack();
                        net.minecraft.commands.CommandSource customSource = new net.minecraft.commands.CommandSource() {
                            @Override
                            public void sendSystemMessage(Component message) {
                                String text = message.getString().replace("\"", "\\\"").replace("\n", "\\n");
                                sendMessage(String.format("{\"event\":\"command_result\", \"result\":\"%s\"}", text));
                            }
                            @Override
                            public boolean acceptsSuccess() { return true; }
                            @Override
                            public boolean acceptsFailure() { return true; }
                            @Override
                            public boolean shouldInformAdmins() { return false; }
                        };
                        net.minecraft.commands.CommandSourceStack source = baseSource.withSource(customSource);
                        this.server.getCommands().performPrefixedCommand(source, command);
                    });
                } else if (event.equals("link_confirm")) {
                    String discordId = json.get("discord_id").getAsString();
                    String discordTag = json.get("discord_tag").getAsString();
                    String pin = json.get("pin").getAsString();

                    String uuid = LinkManager.getUuidByPin(pin);
                    if (uuid != null) {
                        LinkManager.confirmLink(pin, discordId, uuid);
                        
                        // Verifica se o jogador é OP
                        boolean isOp = false;
                        java.util.UUID jUuid = java.util.UUID.fromString(uuid);
                        net.minecraft.server.level.ServerPlayer player = this.server.getPlayerList().getPlayer(jUuid);
                        if (player != null) {
                            isOp = this.server.getPlayerList().isOp(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
                            Constants.LOG.info("TomoriMC - Discord is syncing roles for {}, isOp={}", player.getGameProfile().name(), isOp);
                        }
                        
                        // Envia sucesso pro discord (com status de OP)
                        sendMessage(String.format("{\"event\":\"link_success\", \"discord_id\":\"%s\", \"minecraft_uuid\":\"%s\", \"is_op\":%s}", discordId, uuid, isOp));
                        
                        // Avisa o jogador in-game se ele estiver online
                        if (player != null) {
                            player.sendSystemMessage(Component.literal("§a✅ Sua conta do Minecraft foi vinculada ao usuário do Discord §b" + discordTag + "§a!"));
                        }
                    } else {
                        sendMessage(String.format("{\"event\":\"link_fail\", \"discord_id\":\"%s\", \"reason\":\"PIN inválido ou expirado!\"}", discordId));
                    }
                }
            }
        } catch (Exception e) {
            Constants.LOG.error("Erro ao processar pacote do Discord", e);
        }
        return WebSocket.Listener.super.onText(webSocket, data, last);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        Constants.LOG.info("Conexão WebSocket fechada. Tentando reconectar em 5 segundos...");
        scheduleReconnect();
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        Constants.LOG.error("Erro no WebSocket. Tentando reconectar em 5 segundos...", error);
        scheduleReconnect();
        WebSocket.Listener.super.onError(webSocket, error);
    }

    private void scheduleReconnect() {
        this.webSocket = null;
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                connect();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
