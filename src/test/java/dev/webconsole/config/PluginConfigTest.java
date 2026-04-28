package dev.webconsole.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PluginConfigTest {

    @Test
    void readsNewCommandAliasSectionAndLegacyAliases() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("commands.aliases.day", "time set day");
        config.set("aliases.legacy", "list");

        PluginConfig pluginConfig = new PluginConfig(config);

        assertEquals("time set day", pluginConfig.getAliases().get("day"));
        assertEquals("list", pluginConfig.getAliases().get("legacy"));
    }

    @Test
    void clampsInvalidNumericValuesToSafeBounds() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("web.port", 70000);
        config.set("security.session-timeout-minutes", -1);
        config.set("security.command-rate-limit-per-minute", 0);

        PluginConfig pluginConfig = new PluginConfig(config);

        assertEquals(4242, pluginConfig.getPort());
        assertEquals(60, pluginConfig.getSessionTimeout());
        assertEquals(30, pluginConfig.getCommandRateLimit());
    }

    @Test
    void blocksConfiguredCommandsByRootLabel() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("commands.blocked", java.util.List.of("stop", "minecraft:stop"));

        PluginConfig pluginConfig = new PluginConfig(config);

        assertTrue(pluginConfig.isCommandBlocked("stop"));
        assertTrue(pluginConfig.isCommandBlocked("/minecraft:stop now"));
        assertFalse(pluginConfig.isCommandBlocked("say hello"));
    }
}
