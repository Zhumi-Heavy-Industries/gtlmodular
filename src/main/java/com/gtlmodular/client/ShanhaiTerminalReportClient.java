// Ported from GT-Shanhai (https://github.com/dishanhai/gtl_shanhai-dishanhai),
// (c) 山海恒长在 (dishanhai), LGPL-3.0. Ported with the author's permission.
package com.gtlmodular.client;

import java.util.LinkedHashMap;
import java.util.Map;

import com.gtlmodular.client.gui.terminal.WhaleTerminalReportScreen;
import com.gtlmodular.common.item.terminal.ShanhaiTerminalReport;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** 客戶端保存最近幾份終端報告；聊天連結 {@link #COMMAND} 只在客戶端攔截處理，不會發給伺服器。 */
public final class ShanhaiTerminalReportClient {
    public static final String COMMAND = ShanhaiTerminalReport.CHAT_COMMAND;
    private static final int KEEP = 8;
    private static final Map<Long, ShanhaiTerminalReport> REPORTS = new LinkedHashMap<>();

    private ShanhaiTerminalReportClient() {}

    /** 聊天链接命中后打开报告；id 不存在时给过期提示。 */
    public static void open(long id) {
        Minecraft minecraft = Minecraft.getInstance();
        ShanhaiTerminalReport report = REPORTS.get(id);
        if (report == null) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("message.gtlmodular.whale_terminal.report_expired")
                                .withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        minecraft.setScreen(new WhaleTerminalReportScreen(report));
    }

    public static void store(ShanhaiTerminalReport report) {
        REPORTS.remove(report.id());
        REPORTS.put(report.id(), report);
        while (REPORTS.size() > KEEP) REPORTS.remove(REPORTS.keySet().iterator().next());
    }

    /** @return 該指令屬於本功能（無論成功與否都應攔截）。 */
    public static boolean run(String command) {
        if (command == null || !command.startsWith(COMMAND)) return false;
        Minecraft minecraft = Minecraft.getInstance();
        ShanhaiTerminalReport report = null;
        try {
            report = REPORTS.get(Long.parseLong(command.substring(COMMAND.length()).trim()));
        } catch (NumberFormatException ignored) {
            // 非法 id 與過期同樣處理
        }
        if (report == null) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("message.gtlmodular.whale_terminal.report_expired")
                                .withStyle(ChatFormatting.RED), true);
            }
            return true;
        }
        minecraft.setScreen(new WhaleTerminalReportScreen(report));
        return true;
    }
}
