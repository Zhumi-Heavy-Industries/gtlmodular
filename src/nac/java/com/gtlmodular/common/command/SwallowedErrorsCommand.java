package com.gtlmodular.common.command;

import com.gtlmodular.common.diag.SwallowedErrors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;

final class SwallowedErrorsCommand {

    private SwallowedErrorsCommand() {
    }

    static int execute(CommandSourceStack source) {
        List<SwallowedErrors.SiteSnapshot> sites = SwallowedErrors.snapshot().stream()
                .sorted(Comparator.comparing(SwallowedErrors.SiteSnapshot::site))
                .toList();
        if (sites.isEmpty()) {
            source.sendSuccess(() -> Component.literal("当前服务器进程尚未记录被捕获异常。"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(
                "=== 异常诊断（当前服务器进程，" + sites.size() + " 个捕获点）==="), false);
        for (SwallowedErrors.SiteSnapshot site : sites) {
            source.sendSuccess(() -> Component.literal(
                    "[" + (site.debug() ? "DEBUG" : "WARN") + "] " + site.site()
                            + " | 累计 " + site.total()
                            + " | 最近 " + site.latestType() + ": " + site.latestMessage()), false);
        }
        return 1;
    }
}
