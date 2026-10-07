package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/** 经纬度点（禁飞区多边形顶点） */
@Data
public class GeoPointDto {
    @NotNull(message = "纬度不能为空")
    private Double lat;
    @NotNull(message = "经度不能为空")
    private Double lng;
}
