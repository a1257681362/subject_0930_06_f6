package com.evops.dto;

import com.evops.enums.ZoneType;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** 禁飞区划设入参 */
@Data
public class NoFlyZoneRequest {
    /** 禁飞区编号；为空时自动生成 */
    private String zoneNo;
    @NotBlank(message = "禁飞区名称不能为空")
    private String name;
    @NotNull(message = "禁飞区类型不能为空")
    private ZoneType zoneType;
    @NotEmpty(message = "禁飞区多边形不能为空")
    @Size(min = 3, message = "多边形至少需要 3 个顶点")
    @Valid
    private List<GeoPointDto> polygon;
    /** 限飞上限（米）；为空表示全高度禁飞 */
    private Double maxAltitudeM;
    @NotNull(message = "生效时刻不能为空")
    private LocalDateTime effectiveFrom;
    /** 失效时刻；为空表示长期有效 */
    private LocalDateTime effectiveTo;
    /** 划定原因 */
    private String reason;
}
