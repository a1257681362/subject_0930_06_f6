package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/** 航线航点：序号缺省时按提交顺序编号 */
@Data
public class WaypointDto {
    private Integer seq;
    @NotNull(message = "航点纬度不能为空")
    private Double lat;
    @NotNull(message = "航点经度不能为空")
    private Double lng;
    /** 航点高度（米） */
    private Double altitudeM;
    /** 航点动作：SHOOT 拍摄 / HOVER 悬停 / PASS 途经 */
    private String action;
}
