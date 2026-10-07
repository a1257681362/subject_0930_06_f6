package com.evops.service;

import com.evops.entity.IcingModelParam;
import org.springframework.stereotype.Component;

/**
 * 叶片结冰风险计算器（无状态）。
 *
 * 四个分项各按 0~100 给分，再按模型参数权重加权：
 * - 温度：温度不高于结冰敏感上限才给分，温度越低分越高（固定 20℃ 量程线性）；
 * - 湿度：在 [湿度起算, 湿度饱和] 区间内线性给分；
 * - 风速：在 [风速起算, 风速饱和] 区间内线性给分；
 * - 历史振动：振动幅值在 [起算, 异常] 区间内线性给分。
 * 风险分 = round(Σ 权重 × 分项)。
 */
@Component
public class IcingRiskCalculator {

    /** 温度分项的线性量程（℃）：cutoff 以下 20℃ 给满分 */
    private static final double TEMP_SPAN = 20.0;

    public Scores evaluate(IcingModelParam p, double temperature, double humidity,
                           double windSpeed, double vibration) {
        Scores s = new Scores();
        s.tempScore = tempScore(p, temperature);
        s.humidityScore = linear(humidity, p.getHumidityLowPct(), p.getHumidityHighPct());
        s.windScore = linear(windSpeed, p.getWindLowMs(), p.getWindHighMs());
        s.vibrationScore = linear(vibration, p.getVibrationLow(), p.getVibrationHigh());
        double total = p.getTempWeight() * s.tempScore
                + p.getHumidityWeight() * s.humidityScore
                + p.getWindWeight() * s.windScore
                + p.getVibrationWeight() * s.vibrationScore;
        s.totalScore = (int) Math.round(total);
        return s;
    }

    private int tempScore(IcingModelParam p, double temperature) {
        if (temperature > p.getTempCutoffC()) {
            return 0;
        }
        // 温度越低分越高：cutoff 处 0 分，cutoff - TEMP_SPAN 处满分
        return linear(p.getTempCutoffC() - temperature, 0.0, TEMP_SPAN);
    }

    /** 区间线性给分并截断到 [0,100] */
    private int linear(double value, double low, double high) {
        if (value <= low) {
            return 0;
        }
        if (value >= high) {
            return 100;
        }
        return (int) Math.round((value - low) / (high - low) * 100.0);
    }

    /** 分项与总分 */
    public static class Scores {
        private int totalScore;
        private int tempScore;
        private int humidityScore;
        private int windScore;
        private int vibrationScore;

        public int getTotalScore() { return totalScore; }
        public int getTempScore() { return tempScore; }
        public int getHumidityScore() { return humidityScore; }
        public int getWindScore() { return windScore; }
        public int getVibrationScore() { return vibrationScore; }
    }
}
