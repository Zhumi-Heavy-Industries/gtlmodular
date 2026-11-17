package com.gtlmodular.client.gui.terminal;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.Lazy;
import org.gtlcore.gtlcore.common.block.BlockMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

/** A client-side LDLib index: each selectable block is an actual ButtonWidget. */
public final class TierPickerScreen extends Screen {

    private static final int MAX_W = 480;
    private static final int MAX_H = 366;
    private static final int SLOT = 24;
    private static final int STEP = 27;
    private static final int FAMILY_HEAD = 29;
    private static final int FAMILY_GAP = 13;
    private static final int PAPER = 0xFFF1F2F0;
    private static final int INK = 0xFF252B2D;
    private static final int MUTED = 0xFF647171;
    private static final int RULE = 0xFFB9C1C0;
    private static final int COBALT = 0xFF3A5BC2;
    private static final int CLEAR = 0x00000000;

    private final Screen parent;
    private final BiConsumer<String, Integer> onPick;
    private final List<String> families;
    private final String currentFamily;
    private final int currentTier;
    private ModularUI ui;
    private WidgetGroup root;
    private WidgetGroup slots;
    private final List<TierCell> visibleCells = new ArrayList<>();
    private int panelW;
    private int panelH;
    private int left;
    private int top;
    private int cols;
    private int scroll;

    /** Existing screen callers retain their original replacement operation. */
    public TierPickerScreen(WhaleTerminalScreen parent, String currentFamily, int currentTier) {
        this(parent, currentFamily, currentTier, parent::applyReplacement);
    }

    /** Other parent screens provide their own replacement operation without depending on a particular UI. */
    public TierPickerScreen(Screen parent, String currentFamily, int currentTier,
            BiConsumer<String, Integer> onPick) {
        super(Component.translatable("gui.gtlmodular.whale_terminal.tier_picker.title"));
        this.parent = parent;
        this.onPick = onPick;
        this.currentFamily = currentFamily == null ? "" : currentFamily;
        this.currentTier = Math.max(0, currentTier);
        this.families = families();
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

    private static String englishName(String key) {
        return switch (key) {
            case "sc" -> "STELLAR THERMAL CONTAINER";
            case "sepm" -> "SPACE ELEVATOR MODULE";
            case "cal" -> "COMPONENT ASSEMBLY CASING";
            case "coil" -> "COIL";
            default -> key.toUpperCase();
        };
    }

    @Override
    protected void init() {
        super.init();
        // Remain legible at common Minecraft GUI scales without clipping off-screen.
        panelW = Math.min(MAX_W, Math.max(170, width - 16));
        panelH = Math.min(MAX_H, Math.max(125, height - 16));
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        cols = Math.max(1, (panelW - 44) / STEP);
        scroll = Math.min(scroll, maxScroll());

        ui = new ModularUI(panelW, panelH, IUIHolder.EMPTY, Minecraft.getInstance().player);
        root = ui.mainGroup;
        root.setClientSideWidget();
        root.setBackground(new ColorRectTexture(PAPER));
        slots = new WidgetGroup(0, 0, panelW, panelH);
        slots.setClientSideWidget();
        slots.setBackground(new ColorRectTexture(CLEAR));
        root.addWidget(slots);
        buildCells();
        button(12, panelH - 26, 60, 17, () -> Minecraft.getInstance().setScreen(parent));
        button(panelW - 124, panelH - 26, 52, 17, () -> scrollTo(scroll - viewHeight()));
        button(panelW - 66, panelH - 26, 54, 17, () -> scrollTo(scroll + viewHeight()));
        ui.initWidgets();
    }

    private int listTop() {
        return 62;
    }

    private int listBottom() {
        return panelH - 34;
    }

    private int viewHeight() {
        return Math.max(1, listBottom() - listTop());
    }

    private int contentHeight() {
        int sum = 0;
        for (String family : families) {
            int count = tiersOf(family).length;
            if (count > 0) sum += FAMILY_HEAD + ((count + cols - 1) / cols) * STEP + FAMILY_GAP;
        }
        return sum;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - viewHeight());
    }

    private void scrollTo(int value) {
        int next = Math.max(0, Math.min(maxScroll(), value));
        if (next != scroll) {
            scroll = next;
            buildCells();
        }
    }

    private void button(int x, int y, int w, int h, Runnable action) {
        ButtonWidget widget = new ButtonWidget(x, y, w, h,
                new GuiTextureGroup(new ColorRectTexture(0xFFF8F9F7), new ColorBorderTexture(1, RULE)),
                click -> action.run());
        widget.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(0xFFE6ECFA), new ColorBorderTexture(1, COBALT)));
        widget.setClientSideWidget();
        root.addWidget(widget);
    }

    private void buildCells() {
        if (slots == null) return;
        slots.clearAllWidgets();
        visibleCells.clear();
        int y = listTop() - scroll;
        for (String family : families) {
            Block[] blocks = tiersOf(family);
            if (blocks.length == 0) continue;
            for (int i = 0; i < blocks.length; i++) {
                int x = 18 + i % cols * STEP;
                int cy = y + FAMILY_HEAD + i / cols * STEP;
                // Do not create partially cropped hit targets: the header/footer remain untouchable.
                if (cy < listTop() || cy + SLOT > listBottom()) continue;
                final int index = i;
                boolean selected = family.equals(currentFamily) && i == currentTier;
                ButtonWidget cell = new ButtonWidget(x, cy, SLOT, SLOT,
                        new GuiTextureGroup(new ColorRectTexture(selected ? 0xFFE3E9F8 : 0xFFF8F9F7),
                                new ColorBorderTexture(selected ? 2 : 1, selected ? COBALT : RULE)),
                        click -> {
                            onPick.accept(family, index);
                            Minecraft.getInstance().setScreen(parent);
                        });
                cell.setHoverTexture(new GuiTextureGroup(
                        new ColorRectTexture(0xFFE3E9F8), new ColorBorderTexture(2, COBALT)));
                cell.setClientSideWidget();
                slots.addWidget(cell);
                visibleCells.add(new TierCell(x, cy, family, i, blocks[i]));
            }
            y += FAMILY_HEAD + ((blocks.length + cols - 1) / cols) * STEP + FAMILY_GAP;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xB0171D20);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        root.drawInBackground(graphics, mouseX - left, mouseY - top, partialTick);
        graphics.pose().popPose();

        // Minecraft's native font is 9 px; no microscaled texture-font labels are used.
        int x = left + 18;
        int y = top + 12;
        graphics.drawString(font, "FIG. 02", x, y, MUTED, false);
        graphics.drawString(font, Component.literal("档位选择 / TIER INDEX"), x, y + 14, INK, false);
        int familyCount = 0;
        int tierCount = 0;
        for (String family : families) {
            int length = tiersOf(family).length;
            if (length > 0) { familyCount++; tierCount += length; }
        }
        String summary = familyCount + " FAMILY · " + tierCount + " TIER";
        graphics.drawString(font, summary, left + panelW - 18 - font.width(summary), y + 1, MUTED, false);
        graphics.fill(left + 18, top + 41, left + panelW - 18, top + 42, RULE);

        graphics.enableScissor(left + 14, top + listTop(), left + panelW - 14, top + listBottom());
        int familyY = top + listTop() - scroll;
        for (String family : families) {
            Block[] blocks = tiersOf(family);
            if (blocks.length == 0) continue;
            if (familyY >= top + listTop() && familyY + FAMILY_HEAD < top + listBottom()) {
                graphics.drawString(font, familyName(family), x, familyY, INK, false);
                String english = englishName(family);
                graphics.drawString(font, english, x, familyY + 11, MUTED, false);
                String count = String.format("%02d", blocks.length);
                graphics.drawString(font, count, left + panelW - 28 - font.width(count), familyY + 2, MUTED, false);
                graphics.fill(x, familyY + 23, left + panelW - 24, familyY + 24, RULE);
            }
            familyY += FAMILY_HEAD + ((blocks.length + cols - 1) / cols) * STEP + FAMILY_GAP;
        }
        for (TierCell cell : visibleCells) {
            graphics.renderItem(new ItemStack(cell.block()), left + cell.x() + 4, top + cell.y() + 4);
        }
        graphics.disableScissor();

        if (maxScroll() > 0) {
            int barH = Math.max(12, viewHeight() * viewHeight() / contentHeight());
            int barY = top + listTop() + (viewHeight() - barH) * scroll / maxScroll();
            graphics.fill(left + panelW - 10, top + listTop(), left + panelW - 8, top + listBottom(), RULE);
            graphics.fill(left + panelW - 11, barY, left + panelW - 7, barY + barH, COBALT);
        }
        graphics.fill(left + 18, top + panelH - 32, left + panelW - 18, top + panelH - 31, RULE);
        graphics.drawString(font, Component.translatable("gui.gtlmodular.whale_terminal.tier_picker.back"),
                left + 21, top + panelH - 22, INK, false);
        String paging = (scroll / viewHeight() + 1) + " / " + (maxScroll() / viewHeight() + 1);
        graphics.drawString(font, paging, left + 80, top + panelH - 22, MUTED, false);
        graphics.drawString(font, "◀", left + panelW - 103, top + panelH - 22, INK, false);
        graphics.drawString(font, "▶", left + panelW - 44, top + panelH - 22, INK, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        root.drawInForeground(graphics, mouseX - left, mouseY - top, partialTick);
        graphics.pose().popPose();

        if (mouseY >= top + listTop() && mouseY < top + listBottom()) {
            for (TierCell cell : visibleCells) {
                if (mouseX >= left + cell.x() && mouseX < left + cell.x() + SLOT
                        && mouseY >= top + cell.y() && mouseY < top + cell.y() + SLOT) {
                    graphics.renderTooltip(font, Component.literal(cell.block().getName().getString()
                            + "  (" + (cell.index() + 1) + "/" + tiersOf(cell.family()).length + ")"),
                            mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && root != null && root.mouseClicked(mouseX - left, mouseY - top, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= left && mouseX < left + panelW
                && mouseY >= top + listTop() && mouseY < top + listBottom()) {
            scrollTo(scroll - (int) Math.round(delta * STEP * 2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record TierCell(int x, int y, String family, int index, Block block) {}
}
