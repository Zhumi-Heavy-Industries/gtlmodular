package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** 按钮挂在附加配置入口，保留机器的原生界面与进度控件。 */
public final class NanochipStructureRefresh {

    private NanochipStructureRefresh() {}

    public static final String LANG_BUTTON = "gtlmodular.nanochip.structure.refresh";

    public static final String LANG_TIP = "gtlmodular.nanochip.structure.refresh.tip";

    public static final String LANG_REQUESTED = "gtlmodular.nanochip.structure.refresh.requested";

    public static final String LANG_OK = "gtlmodular.nanochip.structure.refresh.ok";

    public static final String LANG_FAIL = "gtlmodular.nanochip.structure.refresh.fail";

    public static final String LANG_FAIL_OTHER = "gtlmodular.nanochip.structure.refresh.other";

    public static final String ICON = "gtceu:textures/block/machines/scanner/overlay_front.png";

    /** 默认保留手动刷新，便于玩家主动重查；它与自动恢复开关独立。 */
    public static final boolean MANUAL_REFRESH_ENABLED = true;

    public interface Refreshable {

        void requestStructureRefresh(ServerPlayer requester);
    }

    public static final class RefreshConfigurator implements IFancyConfiguratorButton {

        private final MetaMachine machine;

        public RefreshConfigurator(MetaMachine machine) {
            this.machine = machine;
        }

        /** IFancyConfiguratorButton#getTitle 的默认实现抛异常，标签构造仍可能调用，必须提供标题。 */
        @Override
        public Component getTitle() {
            return Component.translatable(LANG_BUTTON);
        }

        @Override
        public IGuiTexture getIcon() {
            return new ResourceTexture(ICON);
        }

        @Override
        public List<Component> getTooltips() {
            return List.of(Component.translatable(LANG_TIP));
        }

        /** 点击回调两侧都会触发，只在服务端执行，避免同一操作重复修改机器状态。 */
        @Override
        public void onClick(ClickData data) {
            if (data == null || data.isRemote) {
                return;
            }
            if (!MANUAL_REFRESH_ENABLED) {
                return;
            }
            if (this.machine instanceof Refreshable refreshable) {
                // 按钮回调不提供玩家实例，刷新入口必须容忍 null。
                refreshable.requestStructureRefresh(null);
            }
        }
    }
}
