package com.gtlmodular.client.keybind;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.config.GTLModularConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

/**
 * 奈亚中央黑日的游戏内总开关（2026-11-02，0.3.328）。
 *
 * <p>一次按键做三件事：
 * <ol>
 *   <li>翻转 {@code nyarlathotepFx.enable}（默认 {@code false}）；</li>
 *   <li>立即写回 {@code config/gtlmodular-common.toml}
 *       （{@code ConfigValue#set} + {@code ForgeConfigSpec#save()}），重进游戏保持；</li>
 *   <li>聊天栏反馈「奈亚中央黑日：已开启 / 已关闭」。</li>
 * </ol>
 *
 * <p>默认键 <b>F10</b>，分类 {@code key.categories.gtlmodular}；玩家可在按键设置里改。
 * 0.3.328 起<b>没有 F9 换面相</b>（五个面相已移除）。
 *
 * <p>只在游戏内响应（无界面、有玩家）；失败只记一次 error，不影响客户端 tick。
 */
@OnlyIn(Dist.CLIENT)
public final class InstabilityToggleKeybind {

    /** 按键名（lang：{@code key.gtlmodular.toggle_instability}）。 */
    public static final String KEY_NAME = "key.gtlmodular.toggle_instability";
    /** 按键分类（lang：{@code key.categories.gtlmodular}）。 */
    public static final String KEY_CATEGORY = "key.categories.gtlmodular";

    /** 默认键位：F10（原版未占用；玩家可在按键设置里改）。 */
    public static final int DEFAULT_KEY = GLFW.GLFW_KEY_F10;

    private static final String MSG_TOGGLE = "message.gtlmodular.instability.toggle";
    private static final String MSG_ENABLED = "message.gtlmodular.instability.enabled";
    private static final String MSG_DISABLED = "message.gtlmodular.instability.disabled";
    private static final String MSG_SAVE_FAILED = "message.gtlmodular.instability.save_failed";

    private static KeyMapping toggleKey;
    private static boolean initialized;
    private static boolean loggedFailure;

    private InstabilityToggleKeybind() {
    }

    /** 注册按键 + 挂 ClientTick 轮询。重复调用只注册一次。 */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            KeyMapping mapping = new KeyMapping(KEY_NAME, InputConstants.Type.KEYSYM, DEFAULT_KEY, KEY_CATEGORY);
            toggleKey = mapping;
            FMLJavaModLoadingContext.get().getModEventBus()
                    .addListener((RegisterKeyMappingsEvent event) -> event.register(mapping));
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase == TickEvent.Phase.END) {
                    onClientTick();
                }
            });
            GTLModular.LOGGER.info("[gtlmodular] 奈亚中央黑日：游戏内开关已注册（默认键 F10，可在按键设置里改；"
                    + "默认状态 = {}）", GTLModularConfig.isNyarlathotepFxEnabled() ? "开" : "关");
        } catch (Throwable throwable) {
            toggleKey = null;
            if (!loggedFailure) {
                loggedFailure = true;
                GTLModular.LOGGER.error("[gtlmodular] 奈亚中央黑日：游戏内开关注册失败（改用配置文件控制）", throwable);
            }
        }
    }

    private static void onClientTick() {
        KeyMapping key = toggleKey;
        if (key == null) {
            return;
        }
        boolean pressed = false;
        while (key.consumeClick()) {
            pressed = true;
        }
        if (!pressed) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // 聊天栏打字 / 任何界面打开时按 F10 不算。
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        try {
            toggleAndReport(minecraft);
        } catch (Throwable throwable) {
            GTLModular.LOGGER.warn("[gtlmodular] 奈亚中央黑日：按键切换失败（已忽略）", throwable);
        }
    }

    private static void toggleAndReport(Minecraft minecraft) {
        boolean enabled = !GTLModularConfig.isNyarlathotepFxEnabled();
        boolean persisted = GTLModularConfig.setNyarlathotepFxEnabled(enabled);

        MutableComponent line = Component.translatable(MSG_TOGGLE,
                Component.translatable(enabled ? MSG_ENABLED : MSG_DISABLED));
        if (!persisted) {
            line.append(Component.translatable(MSG_SAVE_FAILED));
        }
        minecraft.player.displayClientMessage(line, false);
        GTLModular.LOGGER.info("[gtlmodular] 奈亚中央黑日：总开关 {}（{}）",
                enabled ? "已开启" : "已关闭",
                persisted ? "已写回 config/gtlmodular-common.toml" : "写回失败，仅本次会话有效");
    }

    /** 自检用：按键是否注册成功。 */
    public static boolean registered() {
        return toggleKey != null;
    }
}
