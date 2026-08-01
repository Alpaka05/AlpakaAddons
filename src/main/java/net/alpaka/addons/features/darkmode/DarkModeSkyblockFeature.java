package net.alpaka.addons.features.darkmode;

import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.List;

public class DarkModeSkyblockFeature {

    public static void register() {
        FabricLoader.getInstance().getModContainer("alpaka").ifPresent(modContainer -> {
            ResourceManagerHelper.registerBuiltinResourcePack(
                Identifier.fromNamespaceAndPath("alpaka", "dark_skyblock"),
                modContainer,
                Component.literal("Dark Mode Skyblock"),
                ResourcePackActivationType.NORMAL
            );
        });
    }

    public static void applyState(boolean enabled) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getResourcePackRepository() == null) {
            return;
        }

        PackRepository repo = client.getResourcePackRepository();
        repo.reload();

        String targetPackId = null;
        for (String id : repo.getAvailableIds()) {
            if (id.contains("dark_skyblock")) {
                targetPackId = id;
                break;
            }
        }

        if (targetPackId == null) {
            return;
        }

        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        boolean changed = false;

        if (enabled) {
            if (!selected.contains(targetPackId)) {
                selected.add(targetPackId);
                changed = true;
            }
        } else {
            if (selected.contains(targetPackId)) {
                selected.remove(targetPackId);
                changed = true;
            }
        }

        if (changed) {
            repo.setSelected(selected);
            client.options.resourcePacks = selected;
            client.options.save();
            client.reloadResourcePacks();
        }
    }
}
