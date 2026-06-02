package com.tomorimc;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.UUID;

public class TomoreConfig {
    private static String serverId = null;
    
    public static void loadConfig() {
        try {
            File configDir = new File("config");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            
            File configFile = new File(configDir, "tomorimc.json");
            if (!configFile.exists()) {
                serverId = UUID.randomUUID().toString();
                String json = "{\n  \"server_id\": \"" + serverId + "\"\n}";
                try (FileWriter writer = new FileWriter(configFile)) {
                    writer.write(json);
                }
                Constants.LOG.info("=========================================");
                Constants.LOG.info("TomoriMC Config Gerada!");
                Constants.LOG.info("O ID DESTE SERVIDOR É: " + serverId);
                Constants.LOG.info("Vá no Discord e digite: /tomorimc link " + serverId + " #seu-canal");
                Constants.LOG.info("=========================================");
            } else {
                String content = Files.readString(configFile.toPath());
                // Parsing de string básico (sem GSON para garantir compatibilidade máxima sem shading)
                if (content.contains("\"server_id\"")) {
                    String[] parts = content.split("\"server_id\"");
                    if (parts.length > 1) {
                        String idPart = parts[1].split("\"")[1];
                        // Pode ter espaços e dois pontos na frente, então vamos ser seguros
                        // Exemplo: "server_id": "abc"
                        String[] quotes = parts[1].split("\"");
                        if (quotes.length >= 2) {
                            // quotes[0] = : , quotes[1] = o ID, ou algo parecido.
                            // Mas na verdade: split("\"") no ": " vai dar: [0]=": ", [1]="id", [2]="\n..."
                            serverId = quotes[1];
                            if (serverId.equals(": ") || serverId.trim().equals(":")) {
                                serverId = quotes[2];
                            } else if (serverId.trim().isEmpty() && quotes.length >= 3) {
                                serverId = quotes[3];
                            }
                        }
                    }
                }
                
                if (serverId == null || serverId.trim().isEmpty()) {
                    serverId = UUID.randomUUID().toString();
                }
                Constants.LOG.info("TomoriMC Config carregada. Server ID: " + serverId);
            }
        } catch (Exception e) {
            Constants.LOG.error("Erro ao ler/escrever o arquivo config/tomorimc.json", e);
            if (serverId == null) serverId = "fallback_" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
    
    public static String getServerId() {
        return serverId;
    }
}
