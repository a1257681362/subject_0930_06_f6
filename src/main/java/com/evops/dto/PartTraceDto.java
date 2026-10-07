package com.evops.dto;

import com.evops.entity.PartInbound;
import com.evops.entity.PartInstallation;
import com.evops.entity.PartReplacement;
import com.evops.entity.PartRequisition;
import com.evops.entity.PartReturn;
import com.evops.entity.SparePart;
import lombok.Data;

import java.util.List;

/** 序列追踪结果：从入库到装机/退役的全生命周期 */
@Data
public class PartTraceDto {
    private SparePart part;
    private PartInbound inbound;
    private List<PartRequisition> requisitions;
    private List<PartInstallation> installations;
    /** 作为新件的换件记录 */
    private List<PartReplacement> replacementsAsNew;
    /** 作为旧件的换件记录 */
    private List<PartReplacement> replacementsAsOld;
    private List<PartReturn> returns;
}
