package com.gtlmodular.client.gui.terminal;

import com.gtlmodular.GTLModular;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.Lazy;
import org.gtlcore.gtlcore.common.block.BlockMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 功能部件等级选择子界面（仿终极终端的等级方块选择，但把每一档的方块图标直接铺出来）。
 *
 * <p>每个家族上方是中文家族名，下面按行铺该家族所有档位的方块图标；一行放不下会自动换行
 * （例如「线圈」家族最后一档的永恒线圈会落到下一行），内容超过面板高度时可用滚轮滚动。
 */
public final class TierPickerScreen extends Screen {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 290;
    private static final int SLOT = 22;
    private static final int GAP = 2;
    private static final int ROW = SLOT + GAP;
    private static final int COLS = 16;
    private static final int CONTENT_TOP = 52;
    private static final int CONTENT_BOTTOM_PAD = 38;
    private static final int FAMILY_LABEL_H = 13;
    private static final int FAMILY_GAP = 6;
    private static final ResourceLocation WHALE_LOGO =
            new ResourceLocation(GTLModular.MOD_ID, "textures/gui/whale_logo.png");

    private final WhaleTerminalScreen parent;
    private final List<String> families;
    private final String currentFamily;
    private final int currentTier;

    private int left;
    private int top;
    private double scroll;
    private String hoverName = "";
    private String hoverFamily = "";
    private int hoverTier;

    public TierPickerScreen(WhaleTerminalScreen parent, String currentFamily, int currentTier) {
        super(Component.translatable("gui.gtlmodular.whale_terminal.tier_picker.title"));
        this.parent = parent;
        this.currentFamily = currentFamily == null ? "" : currentFamily;
        this.currentTier = Math.max(0, currentTier);
        this.families = families();
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.gtlmodular.whale_terminal." + key, args);
    }

    private static List<String> families() {
        List<String> keys = new ArrayList<>(BlockMap.tierBlockMap.keySet());
        keys.sort(Comparator.comparingInt((String key) -> switch (key) {
            case "sc" -> 0;
            case "sepm" -> 1;
            case "cal" -> 2;
            case "coil" -> 3;
            default -> 4;
        }).thenComparing(Comparator.naturalOrder()));
        return keys;
    }

    private static Block[] tiersOf(String key) {
        Lazy<Block[]> lazy = key == null ? null : BlockMap.tierBlockMap.get(key);
        return lazy == null ? new Block[0] : lazy.get();
    }

    static Component familyName(String key) {
        return switch (key == null ? "" : key) {
            case "sc" -> Component.translatable("gui.gtlcore.stellar_thermal_container");
            case "sepm" -> Component.translatable("gui.gtlcore.space_elevator_module");
            case "cal" -> Component.translatable("gui.gtlcore.component_assembly_casing");
            case "coil" -> Component.translatable("gui.gtlcore.coil");
            default -> Component.literal(key == null ? "" : key.toUpperCase());
        };
    }

    @Override
    protected void init() {
        super.init();
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
    }

    private int contentHeight() {
        int height = 0;
        for (String family : families) {
            int count = tiersOf(family).length;
            if (count == 0) continue;
            int rows = (count + COLS - 1) / COLS;
            height += FAMILY_LABEL_H + rows * ROW + FAMILY_GAP;
        }
        return height;
    }

    private int listTop() {
        return top + CONTENT_TOP;
    }

    private int listBottom() {
        return top + PANEL_H - CONTENT_BOTTOM_PAD;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (listBottom() - listTop()));
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xB0101018);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        EcoStyle.panel(graphics, left, top, left + PANEL_W, top + PANEL_H);
        EcoStyle.slot(graphics, left + 10, top + 8, 30, 30, false);
        graphics.blit(WHALE_LOGO, left + 11, top + 9, 0, 0, 28, 28, 32, 32);
        EcoStyle.title(graphics, font, getTitle(), left + 50, top + 10);
        graphics.drawString(font, tr("tier_picker.subtitle"), left + 50, top + 24,
                EcoStyle.LIGHT, true);
        graphics.fill(left + 8, top + 44, left + PANEL_W - 8, top + 45, EcoStyle.GOLD);
        graphics.fill(left + 8, top + 45, left + PANEL_W - 8, top + 46, EcoStyle.PANEL_DARK);

        hoverName = "";
        hoverFamily = "";
        hoverTier = -1;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));

        int listTop = listTop();
        int listBottom = listBottom();
        graphics.enableScissor(left + 10, listTop, left + PANEL_W - 10, listBottom);
        int y = listTop - (int) scroll;
        for (String family : families) {
            Block[] blocks = tiersOf(family);
            if (blocks.length == 0) continue;
            int rows = (blocks.length + COLS - 1) / COLS;
            if (y + FAMILY_LABEL_H >= listTop && y <= listBottom) {
                graphics.drawString(font, familyName(family), left + 16, y, EcoStyle.TEXT, true);
            }
            for (int i = 0; i < blocks.length; i++) {
                int col = i % COLS;
                int row = i / COLS;
                int x = left + 16 + col * ROW;
                int sy = y + FAMILY_LABEL_H + row * ROW;
                if (sy + SLOT < listTop || sy > listBottom) continue;
                boolean hover = EcoStyle.hit(mouseX, mouseY, x, sy, SLOT, SLOT)
                        && mouseY >= listTop && mouseY < listBottom;
                EcoStyle.slot(graphics, x, sy, SLOT, SLOT, hover);
                graphics.renderItem(new ItemStack(blocks[i]), x + 3, sy + 3);
                if (family.equals(currentFamily) && i == currentTier) {
                    graphics.fill(x - 1, sy - 1, x + SLOT + 1, sy, EcoStyle.GOLD);
                    graphics.fill(x - 1, sy + SLOT, x + SLOT + 1, sy + SLOT + 1, EcoStyle.GOLD);
                    graphics.fill(x - 1, sy, x, sy + SLOT, EcoStyle.GOLD);
                    graphics.fill(x + SLOT, sy, x + SLOT + 1, sy + SLOT, EcoStyle.GOLD);
                }
                if (hover) {
                    hoverName = blocks[i].getName().getString();
                    hoverFamily = family;
                    hoverTier = i;
                }
            }
            y += FAMILY_LABEL_H + rows * ROW + FAMILY_GAP;
        }
        graphics.disableScissor();

        // 滚动条
        int content = contentHeight();
        int view = listBottom - listTop;
        if (content > view) {
            int barH = Math.max(16, view * view / content);
            int barY = listTop + (int) ((view - barH) * scroll / Math.max(1, content - view));
            graphics.fill(left + PANEL_W - 6, listTop, left + PANEL_W - 4, listBottom, EcoStyle.PANEL_DEEP);
            graphics.fill(left + PANEL_W - 6, barY, left + PANEL_W - 4, barY + barH, EcoStyle.GOLD);
        }

        int closeX = left + PANEL_W - 96;
        int closeY = top + PANEL_H - 28;
        boolean hoverClose = EcoStyle.hit(mouseX, mouseY, closeX, closeY, 86, 20);
        EcoStyle.button(graphics, closeX, closeY, 86, 20, hoverClose, true);
        EcoStyle.centeredShadow(graphics, font,
                Component.translatable("gui.gtlmodular.whale_terminal.tier_picker.back"),
                closeX, 86, closeY + 6, EcoStyle.TEXT);
        super.render(graphics, mouseX, mouseY, partialTick);

        if (!hoverName.isEmpty()) {
            int total = tiersOf(hoverFamily).length;
            graphics.renderTooltip(font,
                    Component.literal(hoverName + "  (" + (hoverTier + 1) + "/" + total + ")"),
                    mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int closeX = left + PANEL_W - 96;
            int closeY = top + PANEL_H - 28;
            if (EcoStyle.hit(mouseX, mouseY, closeX, closeY, 86, 20)) {
                Minecraft.getInstance().setScreen(parent);
                return true;
            }
            int listTop = listTop();
            int listBottom = listBottom();
            if (mouseY < listTop || mouseY >= listBottom) return super.mouseClicked(mouseX, mouseY, button);
            int y = listTop - (int) scroll;
            for (String family : families) {
                Block[] blocks = tiersOf(family);
                if (blocks.length == 0) continue;
                int rows = (blocks.length + COLS - 1) / COLS;
                for (int i = 0; i < blocks.length; i++) {
                    int col = i % COLS;
                    int row = i / COLS;
                    int x = left + 16 + col * ROW;
                    int sy = y + FAMILY_LABEL_H + row * ROW;
                    if (EcoStyle.hit(mouseX, mouseY, x, sy, SLOT, SLOT)) {
                        parent.applyReplacement(family, i);
                        Minecraft.getInstance().setScreen(parent);
                        return true;
                    }
                }
                y += FAMILY_LABEL_H + rows * ROW + FAMILY_GAP;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= left && mouseX < left + PANEL_W && mouseY >= listTop() && mouseY < listBottom()) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - delta * ROW * 2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
