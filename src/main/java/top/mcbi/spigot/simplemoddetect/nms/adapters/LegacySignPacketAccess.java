package top.mcbi.spigot.simplemoddetect.nms.adapters;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * 兼容 26.2 / 26.1.x 的木牌更新包访问。
 * 这些版本仍使用 isFrontText/getPos/getLines，而编译目标 26.3 已改为 slot/pos/lines。
 */
final class LegacySignPacketAccess {
    private static volatile Method isFrontTextMethod;
    private static volatile Method getPosMethod;
    private static volatile Method getLinesMethod;

    private LegacySignPacketAccess() {
    }

    static boolean isFrontText(ServerboundSignUpdatePacket packet) {
        try {
            return (boolean) method("isFrontText", () -> isFrontTextMethod, value -> isFrontTextMethod = value)
                .invoke(packet);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法读取木牌正面标记", exception);
        }
    }

    static BlockPos getPos(ServerboundSignUpdatePacket packet) {
        try {
            return (BlockPos) method("getPos", () -> getPosMethod, value -> getPosMethod = value)
                .invoke(packet);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法读取木牌坐标", exception);
        }
    }

    static List<String> getLines(ServerboundSignUpdatePacket packet) {
        try {
            Object lines = method("getLines", () -> getLinesMethod, value -> getLinesMethod = value)
                .invoke(packet);
            if (lines instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
            if (lines instanceof String[] array) {
                return Arrays.asList(array);
            }
            throw new IllegalStateException("未知木牌文本类型: " + (lines == null ? "null" : lines.getClass()));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法读取木牌文本", exception);
        }
    }

    private static Method method(String name, java.util.function.Supplier<Method> getter, java.util.function.Consumer<Method> setter) {
        Method cached = getter.get();
        if (cached != null) {
            return cached;
        }
        synchronized (LegacySignPacketAccess.class) {
            cached = getter.get();
            if (cached != null) {
                return cached;
            }
            try {
                Method resolved = ServerboundSignUpdatePacket.class.getMethod(name);
                setter.accept(resolved);
                return resolved;
            } catch (NoSuchMethodException exception) {
                throw new IllegalStateException("当前运行时缺少木牌兼容方法: " + name, exception);
            }
        }
    }
}
