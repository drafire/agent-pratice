package com.drafire.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 功能开关配置。通过 application.yml 动态控制功能启用/禁用，无需重新部署。
 *
 * 使用方式：
 *   - 注入 FeatureFlags，调用 isXxx() 判断
 *   - 可在运行时通过 @RefreshScope + 配置中心实现热更新
 */
@Component
@ConfigurationProperties(prefix = "app.feature")
public class FeatureFlags {

    /**
     * 是否启用 Graph 模式（StateGraph 工作流）。
     * 关闭后前端回退到 Function Calling 模式。
     */
    private boolean graphMode = true;

    /**
     * 是否启用天气查询功能。
     */
    private boolean weatherEnabled = true;

    /**
     * 是否启用输入治理（注入检测 + 超长截断）。
     */
    private boolean inputGuardEnabled = true;

    /**
     * 是否启用输出治理（ResponseGuard 净化 + 验证）。
     */
    private boolean outputGuardEnabled = true;

    public boolean isGraphMode() {
        return graphMode;
    }

    public void setGraphMode(boolean graphMode) {
        this.graphMode = graphMode;
    }

    public boolean isWeatherEnabled() {
        return weatherEnabled;
    }

    public void setWeatherEnabled(boolean weatherEnabled) {
        this.weatherEnabled = weatherEnabled;
    }

    public boolean isInputGuardEnabled() {
        return inputGuardEnabled;
    }

    public void setInputGuardEnabled(boolean inputGuardEnabled) {
        this.inputGuardEnabled = inputGuardEnabled;
    }

    public boolean isOutputGuardEnabled() {
        return outputGuardEnabled;
    }

    public void setOutputGuardEnabled(boolean outputGuardEnabled) {
        this.outputGuardEnabled = outputGuardEnabled;
    }
}