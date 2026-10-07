package com.evops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.entity.Defect;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DefectMapper extends BaseMapper<Defect> {
}
