package com.gtlmodular.common.data.nanochip;

import net.minecraftforge.forgespi.language.ModFileScanData;

import java.util.List;

final class NanochipProductIdentity {

    private NanochipProductIdentity() {
    }

    // JEI 在模组构造期读取 UID，所属文件的元数据此时已齐全，注册器和配置却可能尚未绑定。
    static String fromScanData(List<ModFileScanData> scans) {
        String owner = NanochipModContext.class.getName();
        var files = scans.stream()
                .filter(scan -> scan.getClasses().stream().anyMatch(type -> owner.equals(type.clazz().getClassName())))
                .toList();
        if (files.size() != 1) {
            throw new IllegalStateException("Expected one NAC owning mod file, found " + files.size());
        }
        var mods = files.get(0).getIModInfoData().stream().flatMap(file -> file.getMods().stream()).toList();
        if (mods.size() != 1) {
            throw new IllegalStateException("Expected one mod in the NAC owning file, found " + mods.size());
        }
        return mods.get(0).getModId();
    }
}
