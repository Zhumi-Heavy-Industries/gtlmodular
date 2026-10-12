package com.gtlmodular.common.machine.pattern.ui;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gtlmodular.GTLModular;
import com.gtlmodular.common.item.PatternArchiveItem;
import com.gtlmodular.common.machine.LazyPatternArchiveApi;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class PatternArchiveConfigurator implements IFancyConfigurator {

    public interface Host {
        Level getLevel();
        BlockPos getPos();
        boolean isRemote();
        boolean isArchiveIncludeSettings();
        void setArchiveIncludeSettings(boolean value);
        boolean isArchiveIncludeName();
        void setArchiveIncludeName(boolean value);
        boolean isArchiveAlignSlots();
        void setArchiveAlignSlots(boolean value);
        int[] archiveSummary();
    }

    @FunctionalInterface
    public interface GeometryLogger {
        void log(WidgetGroup root, ConfiguratorPanel panel, ConfiguratorPanel.Tab tab);
    }

    public enum Layout {
        LAZY(240, 134, 116, 124, 112, true, true, 86, 100, true, false,
                "[gtlmodular] 惰性猪咪归档页：", "没找到在场玩家"),
        MOLTEN(172, 84, 74, 82, 74, false, false, 64, 100, false, true,
                "[gtlmodular] 猪咪归档页：", "没找到在场玩家/工具");

        final int width;
        final int height;
        final int settingsWidth;
        final int nameX;
        final int nameWidth;
        final boolean showHeldText;
        final boolean showPlaceButton;
        final int machineLineY;
        final int toolLineY;
        final boolean showHint;
        final boolean searchInventory;
        final String logPrefix;
        final String missingMessage;

        Layout(int width, int height, int settingsWidth, int nameX, int nameWidth,
               boolean showHeldText, boolean showPlaceButton, int machineLineY, int toolLineY,
               boolean showHint, boolean searchInventory, String logPrefix, String missingMessage) {
            this.width = width;
            this.height = height;
            this.settingsWidth = settingsWidth;
            this.nameX = nameX;
            this.nameWidth = nameWidth;
            this.showHeldText = showHeldText;
            this.showPlaceButton = showPlaceButton;
            this.machineLineY = machineLineY;
            this.toolLineY = toolLineY;
            this.showHint = showHint;
            this.searchInventory = searchInventory;
            this.logPrefix = logPrefix;
            this.missingMessage = missingMessage;
        }
    }

    private static final double FIND_PLAYER_RADIUS = 8.0D;

    private final Host host;
    private final Layout layout;
    private final GeometryLogger geometryLogger;
    private WidgetGroup root;
    private ConfiguratorPanel panel;
    private ConfiguratorPanel.Tab tab;

    public PatternArchiveConfigurator(Host host, Layout layout, GeometryLogger geometryLogger) {
        this.host = host;
        this.layout = layout;
        this.geometryLogger = geometryLogger;
    }

    public void bindPanel(ConfiguratorPanel panel, ConfiguratorPanel.Tab tab) {
        this.panel = panel;
        this.tab = tab;
    }

    private void followExpanded() {
        if (root != null) {
            geometryLogger.log(root, panel, tab);
        }
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gtlmodular.pattern_assembly.archive.title");
    }

    @Override
    public IGuiTexture getIcon() {
        return new ResourceTexture("gtlcore:textures/item/me_pattern_buffer_copy.png");
    }

    @Override
    public List<Component> getTooltips() {
        return List.of(Component.translatable("gtlmodular.pattern_assembly.archive.title"));
    }

    @Override
    public Widget createConfigurator() {
        WidgetGroup group = geometryLogger == null
                ? new WidgetGroup(0, 0, layout.width, layout.height)
                : new WidgetGroup(0, 0, layout.width, layout.height) {
                    @Override
                    public void updateScreen() {
                        super.updateScreen();
                        followExpanded();
                    }

                    @Override
                    public void detectAndSendChanges() {
                        super.detectAndSendChanges();
                        followExpanded();
                    }
                };
        root = group;
        group.addWidget(actionButton(4, 4, "copy", this::doCopy));
        group.addWidget(actionButton(82, 4, "cut", this::doCut));
        if (layout.showHeldText) {
            group.addWidget(new LabelWidget(160, 8, this::heldText));
        }
        group.addWidget(actionButton(4, 24, "paste", this::doPaste));
        group.addWidget(actionButton(82, 24, "clear", this::doClearTool));
        group.addWidget(toggleButton(4, 46, layout.settingsWidth, this::settingsToggleText,
                "include_settings", this::toggleSettings));
        group.addWidget(toggleButton(layout.nameX, 46, layout.nameWidth, this::nameToggleText,
                "include_name", this::toggleName));
        if (layout.showPlaceButton) {
            group.addWidget(toggleButton(4, 66, 232, this::placeToggleText,
                    "place", this::togglePlaceMode));
        }
        group.addWidget(new LabelWidget(4, layout.machineLineY, this::machineLineText));
        group.addWidget(new LabelWidget(4, layout.toolLineY, this::toolLineText));
        if (layout.showHint) {
            group.addWidget(new LabelWidget(4, 114, () ->
                    Component.translatable("gtlmodular.pattern_assembly.archive.hint").getString()));
        }
        return group;
    }

    private static Widget actionButton(int x, int y, String action, Runnable run) {
        String key = "gtlmodular.pattern_assembly.archive." + action;
        return new ButtonWidget(x, y, 74, 16,
                new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(Component.translatable(key).getString())),
                data -> run.run()).setHoverTooltips(Component.translatable(key + ".tip"));
    }

    private static Widget toggleButton(int x, int y, int width, Supplier<String> text,
                                       String setting, Runnable run) {
        return new ButtonWidget(x, y, width, 16,
                new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture(text)),
                data -> run.run()).setHoverTooltips(
                        Component.translatable("gtlmodular.pattern_assembly.archive." + setting + ".tip"));
    }

    private void doCopy() {
        runArchiveAction("复制", ArchiveAction.COPY);
    }

    private void doCut() {
        runArchiveAction("剪切", ArchiveAction.CUT);
    }

    private void doPaste() {
        runArchiveAction("粘贴", ArchiveAction.PASTE);
    }

    private enum ArchiveAction { COPY, CUT, PASTE }

    private void runArchiveAction(String name, ArchiveAction action) {
        try {
            ServerPlayer player = findPlayer();
            ItemStack stack = heldTool(player);
            if (stack == null || player == null) {
                GTLModular.LOGGER.info(layout.logPrefix + name + "未执行 —— " + layout.missingMessage + " pos={}",
                        host.getPos());
                return;
            }
            // The archive API dispatches by the concrete machine class.
            switch (action) {
                case COPY -> LazyPatternArchiveApi.copyFrom(host, stack, player).tell(player);
                case CUT -> LazyPatternArchiveApi.cutFrom(host, stack, player).tell(player);
                case PASTE -> LazyPatternArchiveApi.pasteInto(host, stack, player).tell(player);
            }
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn(layout.logPrefix + name + "异常（已忽略）pos={} 原因={}",
                    host.getPos(), failure.toString());
        }
    }

    private void doClearTool() {
        try {
            ServerPlayer player = findPlayer();
            ItemStack stack = heldTool(player);
            if (stack == null || player == null) {
                return;
            }
            int before = LazyPatternArchiveApi.archivePatternCount(stack);
            LazyPatternArchiveApi.clearArchive(stack);
            LazyPatternArchiveApi.tellKey(player, "gtlmodular.pattern_assembly.archive.cleared", before);
        } catch (Throwable failure) {
            GTLModular.LOGGER.warn(layout.logPrefix + "清空工具异常（已忽略）pos={} 原因={}",
                    host.getPos(), failure.toString());
        }
    }

    private void toggleSettings() {
        if (host.isRemote()) {
            return;
        }
        host.setArchiveIncludeSettings(!host.isArchiveIncludeSettings());
    }

    private void toggleName() {
        if (host.isRemote()) {
            return;
        }
        host.setArchiveIncludeName(!host.isArchiveIncludeName());
    }

    private void togglePlaceMode() {
        if (!layout.showPlaceButton || host.isRemote()) {
            return;
        }
        host.setArchiveAlignSlots(!host.isArchiveAlignSlots());
    }

    private String heldText() {
        ItemStack stack = heldTool(findPlayer());
        if (stack == null) {
            return Component.translatable("gtlmodular.pattern_assembly.archive.no_tool").getString();
        }
        int count = LazyPatternArchiveApi.archivePatternCount(stack);
        if (count <= 0) {
            return Component.translatable("gtlmodular.pattern_assembly.archive.tool_empty").getString();
        }
        return Component.translatable("gtlmodular.pattern_assembly.archive.tool_count", count).getString();
    }

    private String settingsToggleText() {
        return Component.translatable("gtlmodular.pattern_assembly.archive.include_settings",
                onOff(host.isArchiveIncludeSettings())).getString();
    }

    private String nameToggleText() {
        return Component.translatable("gtlmodular.pattern_assembly.archive.include_name",
                onOff(host.isArchiveIncludeName())).getString();
    }

    private String placeToggleText() {
        return Component.translatable(host.isArchiveAlignSlots()
                ? "gtlmodular.pattern_assembly.archive.place.align"
                : "gtlmodular.pattern_assembly.archive.place.append").getString();
    }

    private static String onOff(boolean value) {
        return Component.translatable(value
                ? "gtlmodular.pattern_assembly.archive.on"
                : "gtlmodular.pattern_assembly.archive.off").getString();
    }

    private String machineLineText() {
        int[] summary = host.archiveSummary();
        if (summary[0] <= 0) {
            return Component.translatable("gtlmodular.pattern_assembly.archive.machine_empty").getString();
        }
        return Component.translatable("gtlmodular.pattern_assembly.archive.machine_line",
                summary[0], summary[1], summary[2]).getString();
    }

    private String toolLineText() {
        ItemStack stack = heldTool(findPlayer());
        if (stack == null) {
            return "";
        }
        int count = LazyPatternArchiveApi.archivePatternCount(stack);
        if (count <= 0) {
            return "";
        }
        String source = LazyPatternArchiveApi.archiveSource(stack);
        if (source.isEmpty()) {
            return Component.translatable("gtlmodular.pattern_assembly.archive.tool_count", count).getString();
        }
        return Component.translatable("gtlmodular.pattern_assembly.archive.tool_source", count, source).getString();
    }

    private ServerPlayer findPlayer() {
        try {
            Level level = host.getLevel();
            if (level == null || level.isClientSide || host.getPos() == null) {
                return null;
            }
            ServerLevel serverLevel = level.getServer() == null
                    ? null : level.getServer().getLevel(level.dimension());
            if (serverLevel == null) {
                return null;
            }
            Vec3 center = Vec3.atCenterOf(host.getPos());
            Player nearest = serverLevel.getNearestPlayer(
                    center.x, center.y, center.z, FIND_PLAYER_RADIUS, false);
            if (nearest instanceof ServerPlayer serverPlayer) {
                return serverPlayer;
            }
            List<ServerPlayer> players = level.getServer() == null
                    ? Collections.emptyList() : level.getServer().getPlayerList().getPlayers();
            return players.isEmpty() ? null : players.get(0);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ItemStack heldTool(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        try {
            if (!layout.searchInventory) {
                ItemStack stack = player.getMainHandItem();
                if (stack == null || stack.isEmpty()) {
                    return null;
                }
                if (!(stack.getItem() instanceof PatternArchiveItem)) {
                    return null;
                }
                return stack;
            }
            if (isArchiveTool(player.getMainHandItem())) {
                return player.getMainHandItem();
            }
            if (isArchiveTool(player.getOffhandItem())) {
                return player.getOffhandItem();
            }
            for (ItemStack stack : player.getInventory().items) {
                if (isArchiveTool(stack)) {
                    return stack;
                }
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isArchiveTool(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof PatternArchiveItem;
    }
}
