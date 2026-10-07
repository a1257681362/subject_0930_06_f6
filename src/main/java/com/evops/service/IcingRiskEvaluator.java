package com.evops.service;

import com.evops.dto.IcingScore;
import com.evops.entity.IcingModelVersion;
import com.evops.enums.IcingRiskLevel;
import org.springframework.stereotype.Component;

/**
 * 叶片结冰风险评分引擎。
 * 按 温度、湿度、风速、历史振动 四个因子加权打分（总分 0~100），
 * 再按模型版本的告警阈值/危急阈值划分风险等级。
 *
 * 算法只依赖传入的模型版本参数：历史告警固化产生时的版本，同一读数用旧版本评分结果不变，
 * 从根本上保证"历史告警不随新参数重算"。
 */
@Component
public class IcingRiskEvaluator {

    /**
     * 按指定模型版本评分。四个入参均不可为 null（缺失数据应先标记待补，不进入评分）。
     */
    public IcingScore score(IcingModelVersion model, double temperature, double humidityPct,
                            double windSpeed, double vibration) {
        IcingScore result = new IcingScore();

        // 温度：低于阈值起算，越低越高（满分带宽内线性折算，封顶）
        int tempScore = 0;
        if (temperature <= model.getTempThreshold()) {
            double ratio = (model.getTempThreshold() - temperature) / model.getTempFullBandC();
            tempScore = ratioScore(ratio, model.getTempScoreMax());
        }
        result.setTempScore(tempScore);

        // 湿度：高于阈值起算，越高越高（线性折算，封顶）
        int humidityScore = 0;
        if (humidityPct >= model.getHumidityThreshold()) {
            double ratio = (humidityPct - model.getHumidityThreshold())
                    / model.getHumidityFullBandPct();
            humidityScore = ratioScore(ratio, model.getHumidityScoreMax());
        }
        result.setHumidityScore(humidityScore);

        // 风速：静稳（低于下限）或强风携冻雨（高于上限）均判为易结冰，取风速满分；区间内 0 分
        int windScore = (windSpeed < model.getWindSpeedMin() || windSpeed > model.getWindSpeedMax())
                ? model.getWindScoreMax() : 0;
        result.setWindScore(windScore);

        // 历史振动：相对基线的偏离比例达到异常比例计满分，之间线性折算
        int vibrationScore = 0;
        double baseline = model.getVibrationBaseline();
        if (baseline > 0) {
            double deviationRatio = Math.abs(vibration - baseline) / baseline;
            if (deviationRatio > 0) {
                double ratio = deviationRatio / model.getVibrationAbnormalRatio();
                vibrationScore = ratioScore(ratio, model.getVibrationScoreMax());
            }
        }
        result.setVibrationScore(vibrationScore);

        int total = tempScore + humidityScore + windScore + vibrationScore;
        result.setTotal(total);

        if (total >= model.getCriticalThreshold()) {
            result.setLevel(IcingRiskLevel.CRITICAL);
        } else if (total >= model.getAlertThreshold()) {
            result.setLevel(IcingRiskLevel.WARNING);
        } else {
            result.setLevel(IcingRiskLevel.NORMAL);
        }
        return result;
    }

    private int ratioScore(double ratio, int maxScore) {
        if (ratio <= 0) {
            return 0;
        }
        if (ratio >= 1) {
            return maxScore;
        }
        return (int) Math.round(maxScore * ratio);
    }
}
