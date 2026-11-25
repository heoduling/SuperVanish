package de.myzelyam.supervanish.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LocalizationRegression {
    private static YamlConfiguration read(String name) throws Exception {
        var config = new YamlConfiguration(); config.options().parseComments(true);
        try (var input = new InputStreamReader(Objects.requireNonNull(LocalizationRegression.class.getResourceAsStream("/" + name)), StandardCharsets.UTF_8)) { config.load(input); }
        return config;
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        var messages = read("migration/messages.yml"); var legacy = read("migration/messages.yml"); var zh = read("messages.yml");
        messages.set("Messages.OnVanish", "&a服主自定义提示");
        messages.set("Messages.OnReappear", "");
        messages.setComments("Messages.OnVanish", List.of("保留服主注释"));
        messages.set("Custom", "无关内容");
        messages.setComments("Messages", legacy.getComments("Messages").stream()
                .map(line -> line == null ? null : line.replace("6.3.0-3", "6.3.0-2")).toList());
        require(ConfigurableFile.localizeDefaults(messages, legacy, zh, true), "upgrade required");
        require(messages.getString("Messages.NoPermission").contains("没有权限"), "default translated");
        require(messages.getString("Messages.OnVanish").equals("&a服主自定义提示"), "custom message preserved");
        require(messages.getString("Messages.OnReappear").isEmpty(), "empty message preserved");
        require(messages.getComments("Messages.OnVanish").equals(List.of("保留服主注释")), "custom comment preserved");
        require(messages.getString("Custom").equals("无关内容"), "unrelated key preserved");
        require(messages.getComments("Messages").equals(zh.getComments("Messages")), "stock comments with older version translated");
        require(messages.getString("Messages.PlaceholderIsVanishedYes").equals("Yes"), "machine-readable placeholder preserved");
        require(!ConfigurableFile.localizeDefaults(messages, legacy, zh, true), "idempotent migration");
        var settings=read("migration/config.yml");var oldSettings=read("migration/config.yml");var newSettings=read("config.yml");
        settings.set("InvisibilityFeatures.DisablePush",true);
        settings.setComments("InvisibilityFeatures.DisablePush", List.of());
        settings.setComments("InvisibilityFeatures.DisableDamage",List.of("自定义注释保留"));
        settings.options().setHeader(List.of("服主自定义文件头"));
        require(ConfigurableFile.localizeDefaults(settings,oldSettings,newSettings,false),"settings translated");
        require(settings.getBoolean("InvisibilityFeatures.DisablePush"),"custom boolean preserved");
        require(settings.getComments("InvisibilityFeatures.DisablePush").getFirst().contains("碰撞"),"stale scoreboard comment corrected");
        require(settings.getComments("InvisibilityFeatures.DisableDamage").equals(List.of("自定义注释保留")),"custom settings comment preserved");
        require(settings.options().getHeader().equals(List.of("服主自定义文件头")),"custom header preserved");
        var saved=new YamlConfiguration();saved.options().parseComments(true);saved.loadFromString(settings.saveToString());
        require(saved.getBoolean("InvisibilityFeatures.DisablePush"),"roundtrip preserves values");
        saved.set("MiscellaneousOptions.UpdateChecker.Enable", true);
        saved.set("MiscellaneousOptions.UpdateChecker.NotifyAdmins", true);
        saved.set("MiscellaneousOptions.OwnerSetting", "保留");
        require(ConfigurableFile.removeUpstreamUpdateSettings(saved, false), "remove old updater configuration");
        require(!saved.contains("MiscellaneousOptions.UpdateChecker"), "updater options removed");
        require(saved.getString("MiscellaneousOptions.OwnerSetting").equals("保留"), "unrelated section values preserved");
        require(!ConfigurableFile.removeUpstreamUpdateSettings(saved, false), "removal is idempotent");
        var onlyUpdater = new YamlConfiguration();
        onlyUpdater.set("MiscellaneousOptions.UpdateChecker.Enable", false);
        ConfigurableFile.removeUpstreamUpdateSettings(onlyUpdater, false);
        require(!onlyUpdater.contains("MiscellaneousOptions"), "empty parent removed");
        messages.set("Messages.PluginOutdated", "旧更新提醒");
        require(ConfigurableFile.removeUpstreamUpdateSettings(messages, true), "old update message removed");
        require(!messages.contains("Messages.PluginOutdated") && messages.getString("Messages.OnVanish").equals("&a服主自定义提示"), "other messages preserved");
        System.out.println("LOCALIZATION_REGRESSION_PASS default messages, custom/empty values, comments, headers, settings, idempotence and YAML roundtrip");
    }
}
