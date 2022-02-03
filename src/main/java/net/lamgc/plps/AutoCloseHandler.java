package net.lamgc.plps;

import java.util.function.BooleanSupplier;

/**
 * 自动关闭事件处理器.
 * <p> 当服务端检测到登陆成功时, 将调用该方法, 通过返回值可控制是否立刻关闭服务端.
 */
public class AutoCloseHandler implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        // 返回 true 表示关闭服务端.
        return true;
    }
}
