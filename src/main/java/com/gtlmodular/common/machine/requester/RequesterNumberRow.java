package com.gtlmodular.common.machine.requester;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * 数字输入行：标签 + 科学计数法输入框。
 *
 * <h2>为什么不用 {@code IntInputWidget}</h2>
 * <ol>
 *   <li>它固定 20px 高（含 +/- 按钮），在本界面里太占地方；</li>
 *   <li>它只认 {@code int}，而目标量要能到 9.2e18（远超 int）；</li>
 *   <li>显示走 {@code String.valueOf}，会出现一长串零。</li>
 * </ol>
 * <p>这里用 {@code TextFieldWidget} + {@link RequesterNumbers}：
 * 输入接受 {@code 64000} / {@code 1.5e6} / {@code 9.2e18}，
 * 显示超过 10 万自动切科学计数法（{@code 1.28e5}）。
 */
public class RequesterNumberRow extends WidgetGroup {

    private static final int HEIGHT = 14;

    public RequesterNumberRow(int x, int y, int width, String label,
                              IntSupplier getter, Consumer<Long> setter) {
        super(x, y, width, HEIGHT);
        addWidget(new LabelWidget(0, 3, label));
        int fieldX = 46;
        int fieldWidth = width - fieldX;
        addWidget(new ImageWidget(fieldX, 0, fieldWidth, 12, GuiTextures.DISPLAY));
        addWidget(new TextFieldWidget(fieldX + 2, 1, fieldWidth - 4, 10,
                () -> RequesterNumbers.display(getter.getAsInt()),
                raw -> {
                    long parsed = RequesterNumbers.parse(raw);
                    if (parsed >= 0L) {
                        setter.accept(parsed);
                    }
                })
                .setMaxStringLength(20)
                .setBordered(false));
    }

    /** 长（long）版本：目标量可能超过 int。 */
    public static class LongRow extends WidgetGroup {

        public LongRow(int x, int y, int width, String label,
                       java.util.function.LongSupplier getter, Consumer<Long> setter) {
            super(x, y, width, HEIGHT);
            addWidget(new LabelWidget(0, 3, label));
            int fieldX = 46;
            int fieldWidth = width - fieldX;
            addWidget(new ImageWidget(fieldX, 0, fieldWidth, 12, GuiTextures.DISPLAY));
            addWidget(new TextFieldWidget(fieldX + 2, 1, fieldWidth - 4, 10,
                    () -> RequesterNumbers.display(getter.getAsLong()),
                    raw -> {
                        long parsed = RequesterNumbers.parse(raw);
                        if (parsed >= 0L) {
                            setter.accept(parsed);
                        }
                    })
                    .setMaxStringLength(20)
                    .setBordered(false));
        }
    }
}
