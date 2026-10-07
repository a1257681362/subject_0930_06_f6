package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.dto.WorkerRequest;
import com.evops.entity.Worker;
import com.evops.enums.WorkerStatus;
import com.evops.mapper.WorkerMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 人员：高空作业资质登记；过期资质不能被排入任务 */
@Service
public class WorkerService extends ServiceImpl<WorkerMapper, Worker> {

    /** 人员登记：编号唯一，默认在职 */
    public Worker register(WorkerRequest req) {
        Long dup = count(new QueryWrapper<Worker>().eq("worker_no", req.getWorkerNo()));
        if (dup > 0) {
            throw new IllegalArgumentException("人员编号已存在: " + req.getWorkerNo());
        }
        Worker worker = new Worker();
        worker.setWorkerNo(req.getWorkerNo());
        worker.setName(req.getName());
        worker.setQualificationType(req.getQualificationType());
        worker.setQualificationValidUntil(req.getQualificationValidUntil());
        worker.setStatus(WorkerStatus.ACTIVE);
        save(worker);
        return worker;
    }

    public List<Worker> listAll() {
        return list(new QueryWrapper<Worker>().orderByAsc("id"));
    }

    public Worker mustGet(Long id) {
        Worker worker = getById(id);
        if (worker == null) {
            throw new IllegalArgumentException("人员不存在: " + id);
        }
        return worker;
    }
}
