package com.gtlmodular.gametest;

import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;

import java.util.function.Consumer;

public final class KnownFailureTest extends TestFunction {
    private final String issue;

    public KnownFailureTest(String batch, String name, String template, int timeout, String issue,
                            Consumer<GameTestHelper> body) {
        super(batch, name, template, timeout, 0, false, body);
        this.issue = issue;
    }

    public String issue() { return issue; }

    public boolean matches(Throwable error) {
        return error instanceof Failure failure && issue.equals(failure.issue);
    }

    // 只有复现完整症状才豁免；同一用例的缺依赖、结构失败或其他断言仍须阻止验收。
    public static final class Failure extends GameTestAssertException {
        private final String issue;

        public Failure(String issue, String evidence) {
            super(issue + ": " + evidence);
            this.issue = issue;
        }
    }
}
