package com.gtlmodular.client.jade;

/** provider 与 mixin 共用键定义，避免复制键名或把可选显示实现耦合进常量类。 */
public final class NanochipJadeKeys {

    /** 保留旧命名空间，避免拆分产物改变既有数据和文本标识。 */
    public static final String MODID = "gtlmodular";

    public static final String UID_PATH = "threaded_recipe_logic";

    /** 进度条限制提示框高度；产物明细另用 OUTPUT_LINES，不能因增加产物而扩大进度条数量。 */
    public static final int DISPLAY_LINES = 3;

    /** 服务端必须发送比进度条更多的明细才能显示后续产物，同时限制提示数据的网络载荷。 */
    public static final int OUTPUT_LINES = 6;

    public static final String KEY_THREAD_LIMIT = "gtlmodular_jade_thread_limit";

    public static final String KEY_THREAD_RUNNING = "gtlmodular_jade_thread_running";

    public static final String KEY_THREAD_RUNS = "gtlmodular_jade_thread_runs";

    public static final String KEY_THREAD_LIST = "gtlmodular_jade_thread_list";

    public static final String KEY_SLOT = "slot";

    public static final String KEY_PROGRESS = "progress";

    public static final String KEY_DURATION = "duration";

    public static final String KEY_PARALLELS = "parallels";

    /** 用镜像批次的稳定编号排除原生条已表示的线程，避免追加时重复显示。 */
    public static final String KEY_MIRRORED_SLOT = "gtlmodular_jade_mirrored_slot";

    public static final String KEY_MIRRORED_PARALLELS = "gtlmodular_jade_mirrored_parallels";

    public static final String KEY_INDEPENDENT_THREADS = "gtlmodular_jade_independent_threads";

    public static final String KEY_EU_PER_TICK = "euPerTick";

    public static final String KEY_STATE = "state";

    public static final String KEY_WAITING_REASON = "waitingReason";

    private NanochipJadeKeys() {}
}
