package com.gtlmodular.client.gui.terminal;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.item.ShanhaiUltimateTerminalConfig;
import com.gtlmodular.network.GTLModularNetwork;
import com.gtlmodular.network.OpenEcoSubsystemPacket;
import com.gtlmodular.network.UltimateTerminalConfigPacket;
import com.gtlmodular.network.UltimateTerminalConfigPacket.Field;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * 哦鲸鲸终端 · 主界面（印刷图版版）。
 *
 * <p><b>做法</b>：静态排版（版面、框线、标题、开关名、按钮文字、页脚编号）全部预渲染进
 * {@code sheet_bg.png}；运行时只叠三样会变的东西——开关勾叉图标、ON/OFF 字样、数字与档位名。
 * 这样 5–6px 的微标签才可能又小又不糊，也不会被 Minecraft 的 8px 位图字体撑到重叠。
 *
 * <p>控件仍由 LDLib 提供（{@link WidgetGroup}/{@link ImageWidget}/{@link ButtonWidget}），
 * 但控件树以 client-side 方式挂在普通 {@link Screen} 上，不引入容器与服务端同步。
 */
public final class WhaleTerminalPlateScreen extends Screen {

    private static final int W = 560;
    private static final int H = 340;
    /** 整体缩放：560×340 在 GUI scale 2/3 下偏大，统一缩到屏幕观感合适的大小。 */
    private static final float UI_SCALE = 2f / 3f;

    private static final int INK = 0xFF1F2427;
    private static final int MUTED = 0xFF55605F;
    private static final int FAINT = 0xFF8B9795;
    private static final int COBALT = 0xFF3A5BC2;
    private static final int CLEAR = 0x00000000;

    /** 面板起始 y 与行距，必须与 sheet_bg 的烘焙位置一致。 */
    private static final int PANEL_1_Y = 40;
    private static final int PANEL_2_Y = 218;
    private static final int ROW_STEP = 11;
    private static final int ROW_OFFSET = 15;

    private final InteractionHand hand;
    private final boolean[] flags = new boolean[8];
    private static final Field[] FIELDS = {
            Field.MIRROR, Field.REPLACE, Field.ABSOLUTE_REPLACE, Field.AE_MODE,
            Field.ORDER_MODE, Field.NO_CHAMBER, Field.DISMANTLE, Field.MODULE_CHECK
    };

    private static final int REPEAT_MAX = 648;
    private int repeat;
    private String family = "";
    private int tier;
    private boolean stateLoaded;
    private boolean hasBoundAe;
    private boolean hasBoundEco;

    private ModularUI ui;
    private WidgetGroup root;
    private int left;
    private int top;
    private TextTexture repeatText;
    /** 可交互区域（逻辑坐标）：x, y, w, h、圆角无关的纯矩形。 */
    private final java.util.List<int[]> hitRects = new java.util.ArrayList<>();
    /** 当前悬停的矩形，-1 表示没有。 */
    private int hoverIndex = -1;
    /** 进场淡入起点。 */
    private long openTime;
    /** 点击反馈：哪一个矩形、到什么时候。 */
    private int flashIndex = -1;
    private long flashUntil;
    /** 打开界面时正对着的机器名（≤6 字），用于「扫描目标」。 */
    private String machineName = "未扫描";

    public WhaleTerminalPlateScreen(InteractionHand hand) {
        super(Component.literal("哦鲸鲸终端"));
        this.hand = hand;
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(GTLModular.MOD_ID, "textures/gui/terminal/" + name + ".png");
    }

    private static IGuiTexture image(String name) {
        return new ResourceTexture(tex(name), 0f, 0f, 1f, 1f);
    }

    @Override
    protected void init() {
        super.init();
        int w = Math.round(W * UI_SCALE);
        int h = Math.round(H * UI_SCALE);
        left = (width - w) / 2;
        top = (height - h) / 2;
        if (ui == null) {
            Minecraft mc = Minecraft.getInstance();
            ui = new ModularUI(W, H, IUIHolder.EMPTY, mc.player);
            root = ui.mainGroup;
            root.setClientSideWidget();
        }
        if (!stateLoaded) {
            loadState();
        }
        if (openTime == 0L) {
            openTime = System.currentTimeMillis();
        }
        build();
    }

    private void loadState() {
        Minecraft mc = Minecraft.getInstance();
        ItemStack held = mc.player == null ? ItemStack.EMPTY : mc.player.getItemInHand(hand);
        ItemStack copy = held.copy();
        repeat = ShanhaiUltimateTerminalConfig.getRepeatCount(copy);
        flags[0] = ShanhaiUltimateTerminalConfig.isMirrored(copy);
        flags[1] = ShanhaiUltimateTerminalConfig.isReplaceMode(copy);
        flags[2] = ShanhaiUltimateTerminalConfig.isAbsoluteReplaceMode(copy);
        flags[3] = ShanhaiUltimateTerminalConfig.isAeMode(copy);
        flags[4] = ShanhaiUltimateTerminalConfig.isOrderMode(copy);
        flags[5] = ShanhaiUltimateTerminalConfig.isNoChamberMode(copy);
        flags[6] = ShanhaiUltimateTerminalConfig.isDismantleMode(copy);
        flags[7] = ShanhaiUltimateTerminalConfig.isModuleCheckMode(copy);
        family = ShanhaiUltimateTerminalConfig.getReplacementFamily(copy);
        tier = ShanhaiUltimateTerminalConfig.getReplacementTier(copy, family);
        GlobalPos pos = ShanhaiUltimateTerminalConfig.getBoundAe(copy);
        hasBoundAe = pos != null;
        hasBoundEco = ShanhaiUltimateTerminalConfig.getBoundEco(copy) != null;
        machineName = latestScanMachineName();
        stateLoaded = true;
    }

    /**
     * 本次会话最近一次扫描到的多方块主机名（≤6 字）。
     *
     * <p>只认同一次游戏会话内的扫描报告，不读物品 NBT——离开世界会清空报告缓存，
     * 这样重进后不会残留上一次扫到的主机。
     */
    private static String latestScanMachineName() {
        Minecraft mc = Minecraft.getInstance();
        var scan = com.gtlmodular.client.ShanhaiTerminalReportClient.latestScan();
        if (scan == null || mc.level == null) {
            return "未扫描";
        }
        var pos = scan.target();
        if (pos == null || pos.equals(net.minecraft.core.BlockPos.ZERO)) {
            return "未扫描";
        }
        String name = mc.level.getBlockState(pos).getBlock().getName().getString();
        if (name.length() > 6) {
            name = name.substring(0, 6);
        }
        return name;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xB0151817);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        if (root == null) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        graphics.pose().scale(UI_SCALE, UI_SCALE, 1f);
        root.drawInBackground(graphics, (int) ((mouseX - left) / UI_SCALE), (int) ((mouseY - top) / UI_SCALE), partialTick);
        drawClickFlash(graphics);
        drawHoverBox(graphics, (mouseX - left) / UI_SCALE, (mouseY - top) / UI_SCALE);
        graphics.pose().popPose();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        graphics.pose().scale(UI_SCALE, UI_SCALE, 1f);
        root.drawInForeground(graphics, (int) ((mouseX - left) / UI_SCALE), (int) ((mouseY - top) / UI_SCALE), partialTick);
        graphics.pose().popPose();
        drawEnterFade(graphics);
    }

    // ─────────────────────────── 元件
    private void img(int x, int y, int w, int h, String name) {
        ImageWidget wd = new ImageWidget(x, y, w, h, image(name));
        root.addWidget(wd);
    }

    /** 运行时文字统一缩放到给定高度，保持与烘焙微标签同一字号。 */
    private ImageWidget text(int x, int y, int w, int h, String value, int color) {
        ImageWidget wd = new ImageWidget(x, y, w, h, new TextTexture(value).setColor(color));
        wd.setClientSideWidget();
        root.addWidget(wd);
        return wd;
    }

    private ImageWidget text(int x, int y, int w, int h, java.util.function.Supplier<String> value, int color) {
        ImageWidget wd = new ImageWidget(x, y, w, h, new TextTexture(value).setColor(color));
        wd.setClientSideWidget();
        root.addWidget(wd);
        return wd;
    }

    private void hit(int x, int y, int w, int h, Runnable action) {
        ButtonWidget b = new ButtonWidget(x, y, w, h, new ColorRectTexture(CLEAR), cd -> action.run());
        b.setClientSideWidget();
        root.addWidget(b);
        hitRects.add(new int[]{x, y, w, h});
    }

    /** 悬停选框：1px 钴蓝边框 + 四角小方块（对齐众生行记网页的选中表现）。 */
    private void drawHoverBox(GuiGraphics graphics, double mouseX, double mouseY) {
        hoverIndex = -1;
        for (int i = 0; i < hitRects.size(); i++) {
            int[] r = hitRects.get(i);
            if (mouseX >= r[0] - 2 && mouseX <= r[0] + r[2] + 2
                    && mouseY >= r[1] - 2 && mouseY <= r[1] + r[3] + 2) {
                hoverIndex = i;
                break;
            }
        }
        if (hoverIndex < 0) {
            return;
        }
        int[] r = hitRects.get(hoverIndex);
        int x0 = r[0] - 2;
        int y0 = r[1] - 2;
        int x1 = r[0] + r[2] + 1;
        int y1 = r[1] + r[3] + 1;
        int c = 0xFF3A5BC2;
        graphics.fill(x0, y0, x1, y0 + 1, c);          // 上
        graphics.fill(x0, y1 - 1, x1, y1, c);          // 下
        graphics.fill(x0, y0, x0 + 1, y1, c);          // 左
        graphics.fill(x1 - 1, y0, x1, y1, c);          // 右
        for (int[] corner : new int[][]{{x0, y0}, {x1 - 3, y0}, {x0, y1 - 3}, {x1 - 3, y1 - 3}}) {
            graphics.fill(corner[0], corner[1], corner[0] + 3, corner[1] + 3, c);
        }
    }

    /** 缩放绘制的文字（Minecraft 位图字体按整数倍放大，保证清晰不糊）。 */
    private static final class ScaledText extends com.lowdragmc.lowdraglib.gui.widget.Widget {

        private final java.util.function.Supplier<String> text;
        private final int color;
        private final float scale;
        private final boolean shadow;

        ScaledText(int x, int y, int w, int h, java.util.function.Supplier<String> text,
                   int color, float scale, boolean shadow) {
            super(x, y, w, h);
            this.text = text;
            this.color = color;
            this.scale = scale;
            this.shadow = shadow;
            setClientSideWidget();
        }

        @Override
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            String value = text.get();
            if (value == null || value.isEmpty()) {
                return;
            }
            var font = Minecraft.getInstance().font;
            graphics.pose().pushPose();
            graphics.pose().translate(getPositionX(), getPositionY(), 0);
            graphics.pose().scale(scale, scale, 1f);
            graphics.drawString(font, value, 0, 0, color, shadow);
            graphics.pose().popPose();
        }
    }

    private void scaled(int x, int y, int w, int h, java.util.function.Supplier<String> value,
                        int color, float scale) {
        root.addWidget(new ScaledText(x, y, w, h, value, color, scale, false));
    }

    // ─────────────────────────── 装配
    private void build() {
        root.clearAllWidgets();
        hitRects.clear();
        root.setBackground(new ColorRectTexture(0xFFEFEFF1));
        img(0, 0, W, H, "sheet_bg");

        // 左栏：玩家名（Georgia 斜体字形图集）+ 扫描绑定主机名（≤6 字）
        root.addWidget(new GlyphLabel(36, 56, 250, 120, this::playerName, 210));
        scaled(36, 128, 210, 18, this::machineName, INK, 2.0f);

        for (int i = 0; i < 8; i++) {
            switchRow(i);
        }

        // 重复次数（值活，减/加可点）
        repeatText = new TextTexture((java.util.function.Supplier<String>) () -> String.valueOf(repeat))
                .setColor(INK);
        ImageWidget value = new ImageWidget(382, 132, 12, 10, repeatText);
        value.setClientSideWidget();
        root.addWidget(value);
        hit(395, 131, 10, 10, () -> setRepeat(repeat - 1));
        scaled(396, 132, 8, 9, () -> "-", INK, 1.0f);
        hit(405, 131, 10, 10, () -> setRepeat(repeat + 1));
        scaled(406, 132, 8, 9, () -> "+", INK, 1.0f);

        // 替换档位名（活）
        scaled(386, 142, 56, 9, this::familyLabel, MUTED, 1.0f);

        // 三个按钮的点击区（边框与文字已在底图里）
        hit(356, 141, 92, 12, this::openTierPicker);
        hit(400, 304, 74, 16, () -> {
            GTLModularNetwork.CHANNEL.sendToServer(new OpenEcoSubsystemPacket(hand));
            onClose();
        });
        hit(482, 304, 56, 16, this::onClose);

        // 绑定状态（AE 与 ECO 各自独立）
        img(36, 186, 8, 8, hasBoundAe ? "sw_check" : "sw_cross");
        scaled(50, 186, 200, 10,
                () -> (hasBoundAe ? "AE 已绑定" : "AE 未绑定")
                        + " · " + (hasBoundEco ? "ECO 已绑定" : "ECO 未绑定"),
                MUTED, 1.0f);

        ui.initWidgets();
    }

    private String playerName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? "玩家" : mc.player.getName().getString();
    }

    private String machineName() {
        return machineName == null || machineName.isEmpty() ? "未扫描" : machineName;
    }

    /** 按像素宽裁剪到框内，超出补省略号。 */
    private static String clip(String text, int maxPx) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= maxPx) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String next = sb.toString() + text.charAt(i) + "…";
            if (font.width(next) > maxPx) {
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb + "…";
    }

    private String familyLabel() {
        if (family == null || family.isEmpty()) {
            return "未选择";
        }
        var lazy = org.gtlcore.gtlcore.common.block.BlockMap.tierBlockMap.get(family);
        net.minecraft.world.level.block.Block[] blocks = lazy == null ? null : lazy.get();
        if (blocks == null || blocks.length == 0) {
            return family;
        }
        return clip(blocks[Math.min(Math.max(tier, 0), blocks.length - 1)].getName().getString(), 56);
    }

    private void switchRow(int index) {
        int panelY = index < 4 ? PANEL_1_Y : PANEL_2_Y;
        int row = index % 4;
        int y = panelY + ROW_OFFSET + row * ROW_STEP;
        boolean on = flags[index];

        img(254, y, 7, 7, on ? "sw_check" : "sw_cross");
        if (on) { img(328, y - 1, 18, 9, "state_on"); } else { img(321, y - 1, 25, 9, "state_off"); }
        hit(250, y - 3, 100, ROW_STEP, () -> toggle(index));
    }

    // ─────────────────────────── 交互
    private void toggle(int index) {
        flags[index] = !flags[index];
        send(FIELDS[index], flags[index] ? 1 : 0, "");
        flashIndex = index;
        flashUntil = System.currentTimeMillis() + 220L;
        build();
    }

    /** 点击后在该行压一次淡出的钴蓝，模拟套印落墨。 */
    private void drawClickFlash(GuiGraphics graphics) {
        if (flashIndex < 0 || System.currentTimeMillis() > flashUntil) {
            return;
        }
        float k = (flashUntil - System.currentTimeMillis()) / 220f;
        int alpha = (int) (0x55 * Math.max(0f, Math.min(1f, k)));
        if (alpha <= 0) {
            return;
        }
        int panelY = flashIndex < 4 ? PANEL_1_Y : PANEL_2_Y;
        int y = panelY + ROW_OFFSET + (flashIndex % 4) * ROW_STEP;
        graphics.fill(250, y - 2, 350, y + ROW_STEP - 2, (alpha << 24) | 0x3A5BC2);
    }

    /** 打开时整版面从纸色淡入。 */
    private void drawEnterFade(GuiGraphics graphics) {
        long dt = System.currentTimeMillis() - openTime;
        if (dt >= 260L) {
            return;
        }
        float k = 1f - dt / 260f;
        int alpha = (int) (255 * Math.max(0f, Math.min(1f, k)));
        int w = Math.round(W * UI_SCALE);
        int h = Math.round(H * UI_SCALE);
        graphics.fill(left, top, left + w, top + h, (alpha << 24) | 0xEFF1EF);
    }

    private void setRepeat(int value) {
        int clamped = Math.max(0, Math.min(REPEAT_MAX, value));
        if (clamped == repeat) {
            return;
        }
        repeat = clamped;
        send(Field.REPEAT, clamped, "");
    }

    /** 选档返回：记下选择并通知服务端（复用既有 REPLACEMENT 校验，不新造协议）。 */
    void applyReplacement(String newFamily, int newTier) {
        family = newFamily;
        tier = Math.max(0, newTier);
        send(Field.REPLACEMENT, tier, family);
        build();
    }

    private void openTierPicker() {
        Minecraft.getInstance().setScreen(new TierPickerScreen(this, family, tier, this::applyReplacement));
    }

    private void send(Field field, int value, String text) {
        GTLModularNetwork.CHANNEL.sendToServer(new UltimateTerminalConfigPacket(hand, field, value, text));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (root != null && root.mouseClicked((mouseX - left) / UI_SCALE, (mouseY - top) / UI_SCALE, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (root != null && root.mouseReleased((mouseX - left) / UI_SCALE, (mouseY - top) / UI_SCALE, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (root != null && root.mouseWheelMove((mouseX - left) / UI_SCALE, (mouseY - top) / UI_SCALE, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
