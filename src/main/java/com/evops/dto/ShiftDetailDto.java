package com.evops.dto;

import com.evops.entity.ShiftAssignment;
import com.evops.entity.ShiftNotification;
import com.evops.entity.ShiftPostpone;
import com.evops.entity.WeatherConfirmation;
import com.evops.entity.WorkShift;
import lombok.Data;

import java.util.List;

/** 班次详情：班次 + 人员装备分配 + 二次确认 + 延期记录 + 通知回执 */
@Data
public class ShiftDetailDto {
    private WorkShift shift;
    private List<ShiftAssignment> assignments;
    private List<WeatherConfirmation> confirmations;
    private List<ShiftPostpone> postpones;
    private List<ShiftNotification> notifications;
}
