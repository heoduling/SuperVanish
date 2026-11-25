/*
 * Copyright © 2015, Leon Mangler and the SuperVanish contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package de.myzelyam.supervanish.commands;

import de.myzelyam.supervanish.SuperVanish;
import de.myzelyam.supervanish.utils.Validation;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public enum CommandAction {
    VANISH_SELF(
            "sv.use",
            false,
            "/sv [on|off]",
            "切换自己的隐身状态（on 隐身，off 显形）") {
        @Override
        public boolean checkPermission(CommandSender sender, SuperVanish plugin) {
            Validation.checkNotNull(plugin, "plugin cannot be null");
            return plugin.hasPermissionToVanish(sender);
        }
    },
    VANISH_OTHER(
            "sv.others",
            true,
            "/sv [on|off] <玩家名>",
            "设置指定玩家的隐身状态"),
    VANISHED_LIST(
            "sv.list",
            true,
            "/sv list",
            "查看隐身玩家列表"),
    BROADCAST_LOGIN(
            "sv.login",
            false,
            "/sv login",
            "广播自己的假进服消息"),
    BROADCAST_LOGOUT(
            "sv.logout",
            false,
            "/sv logout",
            "广播自己的假退服消息"),
    TOGGLE_ITEM_PICKUPS(
            "sv.toggleitems",
            false,
            "/sv tipu",
            "切换隐身时是否捡拾物品") {
        @Override
        public boolean checkPermission(CommandSender sender, SuperVanish plugin) {
            return sender.hasPermission(getMainPermission()) || sender.hasPermission("sv.toggleitempickups");
        }
    },
    RECREATE_FILES(
            "sv.recreatefiles",
            true,
            "/sv recreatefiles",
            "查看配置重建提示（confirm 确认，dismiss 切换提醒，force 强制重置）") {
        @Override
        public boolean checkPermission(CommandSender sender, SuperVanish plugin) {
            return sender.hasPermission(getMainPermission()) || sender.hasPermission("sv.updatecfg");
        }
    },
    RELOAD(
            "sv.reload",
            true,
            "/sv reload",
            "重载配置和消息"),
    PRINT_STACKTRACE(
            "sv.stacktrace",
            true,
            "/sv stacktrace",
            "向控制台输出诊断信息"),
    SHOW_HELP(
            "sv.help",
            true,
            "/sv help",
            "查看此帮助页");

    /**
     * Use {@link #checkPermission(CommandSender, SuperVanish)} to check whether a {@link CommandSender} has
     * permission to perform an action or not
     */
    private final String mainPermission;
    private final boolean console;
    private final String usage, description;

    CommandAction(String mainPermission, boolean console, String usage, String description) {
        this.mainPermission = mainPermission;
        this.console = console;
        this.usage = usage;
        this.description = description;
    }

    public String getMainPermission() {
        return mainPermission;
    }

    public String getUsage() {
        return usage;
    }

    public String getDescription() {
        return description;
    }

    static List<String> getAvailableFirstArguments(CommandSender sender, SuperVanish plugin) {
        Validation.checkNotNull(plugin, "plugin cannot be null");
        List<String> list = new ArrayList<>();
        if (SHOW_HELP.checkPermission(sender, plugin))
            list.add("help");
        if (RECREATE_FILES.checkPermission(sender, plugin))
            list.add("recreatefiles");
        if (RELOAD.checkPermission(sender, plugin))
            list.add("reload");
        if (VANISHED_LIST.checkPermission(sender, plugin))
            list.add("list");
        if (VANISH_SELF.checkPermission(sender, plugin))
            list.add("on");
        if (VANISH_SELF.checkPermission(sender, plugin))
            list.add("off");
        if (TOGGLE_ITEM_PICKUPS.checkPermission(sender, plugin))
            list.add("tipu");
        if (PRINT_STACKTRACE.checkPermission(sender, plugin))
            list.add("stacktrace");
        return list;
    }

    public static boolean hasAnyCmdPermission(CommandSender sender, SuperVanish plugin) {
        for (CommandAction action : CommandAction.values())
            if (action.checkPermission(sender, plugin))
                return true;
        return false;
    }

    public boolean checkPermission(CommandSender sender, SuperVanish plugin) {
        return sender.hasPermission(getMainPermission());
    }

    public boolean usableByConsole() {
        return console;
    }
}