package com.tomorimc;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class LinkManager {
    // Discord ID -> Minecraft UUID
    private static Map<String, String> linkedAccounts = new HashMap<>();
    
    // PIN -> Minecraft UUID
    private static Map<String, String> pendingPins = new HashMap<>();
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File getLinksFile() {
        return new File(System.getProperty("user.dir"), "config/tomorimc_links.json");
    }

    public static void loadLinks() {
        File file = getLinksFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                linkedAccounts = GSON.fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
                if (linkedAccounts == null) linkedAccounts = new HashMap<>();
            } catch (IOException e) {
                Constants.LOG.error("Erro ao carregar os links de contas", e);
            }
        }
    }

    public static void saveLinks() {
        File file = getLinksFile();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(linkedAccounts, writer);
        } catch (IOException e) {
            Constants.LOG.error("Erro ao salvar os links de contas", e);
        }
    }

    public static String generatePinFor(String uuid) {
        Random rand = new Random();
        String pin;
        do {
            pin = String.format("%04d", rand.nextInt(10000));
        } while (pendingPins.containsKey(pin));
        
        pendingPins.put(pin, uuid);
        return pin;
    }

    public static String getUuidByPin(String pin) {
        return pendingPins.get(pin);
    }

    public static void confirmLink(String pin, String discordId, String uuid) {
        pendingPins.remove(pin);
        linkedAccounts.put(discordId, uuid);
        saveLinks();
    }

    public static String getDiscordId(String uuid) {
        for (Map.Entry<String, String> entry : linkedAccounts.entrySet()) {
            if (entry.getValue().equals(uuid)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static String getMinecraftUuid(String discordId) {
        return linkedAccounts.get(discordId);
    }
}
