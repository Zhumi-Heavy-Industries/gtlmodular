package com.gtlmodular.client.gui.terminal;

import com.gtlmodular.GTLModular;
import com.gtlmodular.common.item.ShanhaiUltimateTerminalConfig;
import com.gtlmodular.network.GTLModularNetwork;
import com.gtlmodular.network.UltimateTerminalConfigPacket;
import com.gtlmodular.network.UltimateTerminalConfigPacket.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 哦鲸鲸终端 · 设置屏（仿 eco / Neo ECO AE 的浅色终端风格）。
 *
 * <p>布局也按 eco 的终端语言重排：整块浅灰紫面板 + 分区细框 + 4×2 竖直拨杆开关 +
 * 物品槽 + 小号按钮；金色只用于标题分隔线、焦点和选中项。
 */
public final class WhaleTerminalScreen extends Screen {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 278;
    private static final int REPEAT_MAX = 648;
    private static final ResourceLocation WHALE_LOGO =
            new ResourceLocation(GTLModular.MOD_ID, "textures/gui/whale_logo.png");

    private static final Field[] TOGGLE_FIELDS = {
            Field.MIRROR, Field.REPLACE, Field.ABSOLUTE_REPLACE, Field.AE_MODE,
            Field.ORDER_MODE, Field.NO_CHAMBER, Field.DISMANTLE, Field.MODULE_CHECK
    };
    private static final String[] TOGGLE_KEYS = {
            "mirror", "replace", "absolute_replace", "ae_mode",
            "order_mode", "no_chamber", "dismantle", "module_check"
    };

    private final InteractionHand hand;
    private final boolean[] flags = new boolean[TOGGLE_FIELDS.length];
    private int repeat;
    private String family = "";
    private int tier;
    private Component bound = Component.empty();
    private Component boundEco = Component.empty();
    private boolean hasBoundEco;
    private boolean stateLoaded;

    private int left;
    private int top;
    private int repeatSlotX;
    private int repeatSlotY;
    private int minusX;
    private int plusX;
    private int pickerX;
    private int pickerY;
    private int closeX;
    private int closeY;
    private int ecoBtnX;
    private int ecoBtnY;
    private EditBox repeatBox;
    private boolean syncing;

    public WhaleTerminalScreen(InteractionHand hand) {
        super(Component.translatable("gui.gtlmodular.whale_terminal.title"));
        this.hand = hand;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.gtlmodular.whale_terminal." + key, args);
    }

    @Override
    protected void init() {
        super.init();
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;

        if (!stateLoaded) {
            ItemStack held = Minecraft.getInstance().player == null
                    ? ItemStack.EMPTY : Minecraft.getInstance().player.getItemInHand(hand);
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
            bound = pos == null
                    ? Component.translatable("tooltip.gtlmodular.whale_terminal.unbound")
                    : Component.translatable("tooltip.gtlmodular.whale_terminal.bound",
                            pos.dimension().location().toString(), pos.pos().toShortString());
            GlobalPos eco = ShanhaiUltimateTerminalConfig.getBoundEco(copy);
            hasBoundEco = eco != null;
            boundEco = tr(eco == null ? "eco_state_unbound" : "eco_state_bound");
            stateLoaded = true;
        }

        repeatSlotX = left + 120;
        repeatSlotY = top + 156;
        minusX = left + 180;
        plusX = left + 202;
        pickerX = left + 318;
        pickerY = top + 182;
        closeX = left + PANEL_W - 98;
        closeY = top + PANEL_H - 24;
        ecoBtnX = left + PANEL_W - 100;
        ecoBtnY = top + 9;

        repeatBox = new EditBox(font, repeatSlotX + 6, repeatSlotY + 5, 44, 10, tr("repeat"));
        repeatBox.setBordered(false);
        repeatBox.setTextColor(EcoStyle.TEXT_DARK);
        repeatBox.setMaxLength(3);
        repeatBox.setFilter(value -> value.chars().allMatch(Character::isDigit));
        repeatBox.setValue(String.valueOf(repeat));
        repeatBox.setResponder(value -> {
            if (syncing || value.isEmpty()) return;
            int parsed;
            try {
                parsed = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return;
            }
            parsed = Math.max(0, Math.min(REPEAT_MAX, parsed));
            if (parsed != repeat) {
                repeat = parsed;
                send(Field.REPEAT, repeat, "");
            }
        });
        addRenderableWidget(repeatBox);
    }

    // ---------- 状态 ----------
    void applyReplacement(String newFamily, int newTier) {
        family = newFamily;
        tier = Math.max(0, newTier);
        send(Field.REPLACEMENT, tier, family);
    }

    private void setRepeat(int value) {
        int clamped = Math.max(0, Math.min(REPEAT_MAX, value));
        if (clamped == repeat) return;
        repeat = clamped;
        syncing = true;
        try {
            repeatBox.setValue(String.valueOf(clamped));
        } finally {
            syncing = false;
        }
        send(Field.REPEAT, clamped, "");
    }

    private void toggle(int index) {
        flags[index] = !flags[index];
        send(TOGGLE_FIELDS[index], flags[index] ? 1 : 0, "");
    }

    private void send(Field field, int value, String text) {
        GTLModularNetwork.CHANNEL.sendToServer(
                new UltimateTerminalConfigPacket(hand, field, value, text));
    }

    private static Block[] tiersOf(String key) {
        var lazy = key == null ? null : org.gtlcore.gtlcore.common.block.BlockMap.tierBlockMap.get(key);
        return lazy == null ? new Block[0] : lazy.get();
    }

    private void openTierPicker() {
        Minecraft.getInstance().setScreen(new TierPickerScreen(this, family, tier));
    }

    private Component replacementLabel() {
        Block[] blocks = tiersOf(family);
        if (blocks.length == 0) return tr("replacement_none");
        int safe = Math.min(tier, blocks.length - 1);
        return blocks[safe].getName();
    }

    private ItemStack replacementIcon() {
        Block[] blocks = tiersOf(family);
        if (blocks.length == 0) return ItemStack.EMPTY;
        return new ItemStack(blocks[Math.min(tier, blocks.length - 1)]);
    }

    // ---------- 渲染 ----------
    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xB0101018);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        EcoStyle.panel(graphics, left, top, left + PANEL_W, top + PANEL_H);
        drawHeader(graphics, mouseX, mouseY);
        drawSwitches(graphics, mouseX, mouseY);
        drawBottomBox(graphics, mouseX, mouseY);
        drawFooter(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        EcoStyle.slot(graphics, left + 10, top + 8, 30, 30, false);
        graphics.blit(WHALE_LOGO, left + 11, top + 9, 0, 0, 28, 28, 32, 32);
        EcoStyle.title(graphics, font, getTitle(), left + 50, top + 10);
        graphics.drawString(font, tr("subtitle"), left + 50, top + 24, EcoStyle.LIGHT, true);
        boolean hoverEco = EcoStyle.hit(mouseX, mouseY, ecoBtnX, ecoBtnY, 92, 18);
        EcoStyle.button(graphics, ecoBtnX, ecoBtnY, 92, 18, hoverEco, true);
        EcoStyle.centeredShadow(graphics, font, tr(hasBoundEco ? "subsystem.remote" : "subsystem.open"),
                ecoBtnX, 92, ecoBtnY + 5, EcoStyle.TEXT);
        graphics.fill(left + 8, top + 44, left + PANEL_W - 8, top + 45, EcoStyle.GOLD);
        graphics.fill(left + 8, top + 45, left + PANEL_W - 8, top + 46, EcoStyle.PANEL_DARK);
    }

    private void drawSwitches(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, tr("section_switches"), left + 10, top + 49, EcoStyle.TEXT, true);
        EcoStyle.box(graphics, left + 10, top + 58, 400, 82);
        for (int i = 0; i < TOGGLE_FIELDS.length; i++) {
            int col = i % 4;
            int row = i / 4;
            int x = left + 16 + col * 96;
            int y = top + 62 + row * 38;
            boolean hover = EcoStyle.hit(mouseX, mouseY, x, y, 96, 36);
            if (hover) graphics.fill(x, y, x + 96, y + 36, 0x22FFFFFF);
            EcoStyle.centeredShadow(graphics, font, tr(TOGGLE_KEYS[i]), x, 96, y + 3, EcoStyle.TEXT);
            if (flags[i]) graphics.fill(x + 24, y + 13, x + 72, y + 14, EcoStyle.GOLD);
            EcoStyle.verticalSwitch(graphics, x + 41, y + 15, flags[i]);
        }
    }

    private void drawBottomBox(GuiGraphics graphics, int mouseX, int mouseY) {
        EcoStyle.box(graphics, left + 10, top + 148, 400, 68);

        graphics.drawString(font, tr("repeat"), left + 16, repeatSlotY + 5, EcoStyle.TEXT, true);
        EcoStyle.slot(graphics, repeatSlotX, repeatSlotY, 56, 18, false);
        smallButton(graphics, minusX, repeatSlotY, 18, 18, "-", mouseX, mouseY);
        smallButton(graphics, plusX, repeatSlotY, 18, 18, "+", mouseX, mouseY);
        graphics.drawString(font, tr("repeat_hint", REPEAT_MAX), left + 228, repeatSlotY + 5,
                EcoStyle.TEXT_DIM, false);

        Component tierLabel = tr("replacement");
        graphics.drawString(font, tierLabel, left + 16, pickerY + 5, EcoStyle.TEXT, true);
        graphics.drawString(font, shorten(replacementLabel(), 168),
                left + 120, pickerY + 5, EcoStyle.TEXT, true);
        EcoStyle.slot(graphics, left + 294, pickerY, 18, 18, false);
        ItemStack icon = replacementIcon();
        if (!icon.isEmpty()) graphics.renderItem(icon, left + 295, pickerY + 1);
        boolean hover = EcoStyle.hit(mouseX, mouseY, pickerX, pickerY, 84, 18);
        EcoStyle.button(graphics, pickerX, pickerY, 84, 18, hover, true);
        EcoStyle.centeredShadow(graphics, font, tr("tier_picker.open"), pickerX, 84, pickerY + 5, EcoStyle.TEXT);
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, shorten(bound, 160), left + 10, top + 224, EcoStyle.TEXT_DIM, false);
        graphics.drawString(font, boundEco, left + 180, top + 224, EcoStyle.TEXT_DIM, false);
        graphics.drawString(font, tr("hint.0"), left + 10, top + 238, EcoStyle.TEXT_DIM, false);
        graphics.drawString(font, tr("hint.1"), left + 10, top + 250, EcoStyle.TEXT_DIM, false);
        boolean hover = EcoStyle.hit(mouseX, mouseY, closeX, closeY, 88, 20);
        EcoStyle.button(graphics, closeX, closeY, 88, 20, hover, true);
        EcoStyle.centeredShadow(graphics, font, Component.translatable("gui.gtlmodular.whale_terminal.close"),
                closeX, 88, closeY + 6, EcoStyle.TEXT);
    }

    private String shorten(Component text, int width) {
        var lines = font.split(text, Math.max(1, width));
        if (lines.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        lines.get(0).accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }

    private void smallButton(GuiGraphics graphics, int x, int y, int w, int h, String label,
                             int mouseX, int mouseY) {
        boolean hover = EcoStyle.hit(mouseX, mouseY, x, y, w, h);
        EcoStyle.button(graphics, x, y, w, h, hover, true);
        graphics.drawString(font, label, x + (w - font.width(label)) / 2, y + 5, EcoStyle.TEXT, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < TOGGLE_FIELDS.length; i++) {
                int col = i % 4;
                int row = i / 4;
                int x = left + 16 + col * 96;
                int y = top + 62 + row * 38;
                if (EcoStyle.hit(mouseX, mouseY, x, y, 96, 36)) {
                    toggle(i);
                    return true;
                }
            }
            if (EcoStyle.hit(mouseX, mouseY, minusX, repeatSlotY, 18, 18)) {
                setRepeat(repeat - 1);
                return true;
            }
            if (EcoStyle.hit(mouseX, mouseY, plusX, repeatSlotY, 18, 18)) {
                setRepeat(repeat + 1);
                return true;
            }
            if (EcoStyle.hit(mouseX, mouseY, left + 16, pickerY - 3, 276, 24)
                    || EcoStyle.hit(mouseX, mouseY, pickerX, pickerY, 84, 18)) {
                openTierPicker();
                return true;
            }
            if (EcoStyle.hit(mouseX, mouseY, ecoBtnX, ecoBtnY, 92, 18)) {
                GTLModularNetwork.CHANNEL.sendToServer(new com.gtlmodular.network.OpenEcoSubsystemPacket(hand));
                onClose();
                return true;
            }
            if (EcoStyle.hit(mouseX, mouseY, closeX, closeY, 88, 20)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
