package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.dto.AnnotationDto;
import com.evops.dto.DefectRegisterRequest;
import com.evops.dto.DefectTraceDto;
import com.evops.dto.ReviewRequest;
import com.evops.dto.DefectNoteRequest;
import com.evops.entity.Blade;
import com.evops.entity.Defect;
import com.evops.entity.DefectAnnotation;
import com.evops.entity.DefectHandlingRule;
import com.evops.entity.DefectLevelChange;
import com.evops.entity.DefectNote;
import com.evops.entity.InspectionOrder;
import com.evops.enums.DefectLevel;
import com.evops.enums.DefectStatus;
import com.evops.enums.OrderStatus;
import com.evops.enums.ReviewDecision;
import com.evops.mapper.DefectAnnotationMapper;
import com.evops.mapper.DefectLevelChangeMapper;
import com.evops.mapper.DefectMapper;
import com.evops.mapper.DefectNoteMapper;
import com.evops.mapper.InspectionOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 缺陷全生命周期（F3）：
 * 登记即按 类型+等级 命中处置时限并生成首版维修建议/责任人；
 * 专家复核可升级/降级但不覆盖初判，风险等级变化重新生成维修建议并记录责任人；
 * 已关闭缺陷只能追加说明；影像标注可反查原单、处理人与异常原因。
 */
@Service
public class DefectLifecycleService {

    private final DefectMapper defectMapper;
    private final DefectAnnotationMapper annotationMapper;
    private final DefectLevelChangeMapper levelChangeMapper;
    private final DefectNoteMapper noteMapper;
    private final InspectionOrderMapper orderMapper;
    private final BladeService bladeService;
    private final HandlingRuleService handlingRuleService;
    private final AuditService auditService;

    public DefectLifecycleService(DefectMapper defectMapper,
                                  DefectAnnotationMapper annotationMapper,
                                  DefectLevelChangeMapper levelChangeMapper,
                                  DefectNoteMapper noteMapper,
                                  InspectionOrderMapper orderMapper,
                                  BladeService bladeService,
                                  HandlingRuleService handlingRuleService,
                                  AuditService auditService) {
        this.defectMapper = defectMapper;
        this.annotationMapper = annotationMapper;
        this.levelChangeMapper = levelChangeMapper;
        this.noteMapper = noteMapper;
        this.orderMapper = orderMapper;
        this.bladeService = bladeService;
        this.handlingRuleService = handlingRuleService;
        this.auditService = auditService;
    }

    /** 缺陷登记：初判分类/等级 + 影像标注，按规则生成处置时限、维修建议与责任人 */
    @Transactional
    public Defect register(Long orderId, DefectRegisterRequest req) {
        InspectionOrder order = mustGetOrder(orderId);
        if (order.getStatus() != OrderStatus.INSPECTED
                && order.getStatus() != OrderStatus.DEFECT_REGISTERED) {
            throw new IllegalArgumentException("工单状态不允许登记缺陷: " + order.getStatus());
        }
        Blade blade = bladeService.mustGet(req.getBladeId());
        if (!blade.getTurbineId().equals(order.getTurbineId())) {
            throw new IllegalArgumentException("缺陷叶片不属于工单风机: " + req.getBladeId());
        }

        DefectLevel level = req.getDefectLevel();
        DefectHandlingRule rule = handlingRuleService.mustGetRule(req.getDefectType(), level);

        Defect defect = new Defect();
        defect.setOrderId(order.getId());
        defect.setBladeId(req.getBladeId());
        defect.setDefectType(req.getDefectType());
        defect.setDescription(req.getDescription());
        // 初判与当前等级分离：复核只改当前等级，初判永久保留
        defect.setInitialLevel(level);
        defect.setDefectLevel(level);
        defect.setInspector(req.getInspector());
        defect.setStatus(DefectStatus.OPEN);
        defect.setRuleId(rule.getId());
        defect.setRepairAdvice(rule.getAdviceTemplate());
        defect.setResponsiblePerson(rule.getDefaultResponsible());
        defectMapper.insert(defect);

        // 处置时限以持久化的登记时刻（createTime）为锚点，与复核重算保持同一口径
        LocalDateTime anchor = defect.getCreateTime() != null
                ? defect.getCreateTime() : LocalDateTime.now();
        defect.setDeadline(anchor.plusHours(rule.getDeadlineHours()));
        defectMapper.updateById(defect);

        // 巡检员在影像上标注位置、尺寸与截图
        for (AnnotationDto a : req.getAnnotations()) {
            DefectAnnotation annotation = new DefectAnnotation();
            annotation.setDefectId(defect.getId());
            annotation.setOrderId(order.getId());
            annotation.setImageUrl(a.getImageUrl());
            annotation.setPositionX(a.getPositionX());
            annotation.setPositionY(a.getPositionY());
            annotation.setLengthMm(a.getLengthMm());
            annotation.setWidthMm(a.getWidthMm());
            annotation.setScreenshotUrl(a.getScreenshotUrl());
            annotation.setInspector(req.getInspector());
            annotation.setRemark(a.getRemark());
            annotationMapper.insert(annotation);
        }

        order.setStatus(OrderStatus.DEFECT_REGISTERED);
        orderMapper.updateById(order);

        auditService.record("DEFECT", defect.getId(), "REGISTER",
                req.getDefectType() + "/" + level + " 处置时限=" + rule.getDeadlineHours()
                        + "h 责任人=" + rule.getDefaultResponsible(),
                req.getInspector());
        return defect;
    }

    /** 专家复核：升级/降级/维持。初判不覆盖；等级变化重新生成维修建议并记录责任人 */
    @Transactional
    public Defect review(Long defectId, ReviewRequest req) {
        Defect defect = mustGet(defectId);
        if (defect.getStatus() == DefectStatus.ACCEPTED) {
            throw new IllegalArgumentException("缺陷已关闭，只能追加说明，不能复核: " + defectId);
        }
        DefectLevel from = defect.getDefectLevel();
        DefectLevel to = req.getReviewedLevel();

        defect.setReviewer(req.getReviewer());
        defect.setReviewedAt(LocalDateTime.now());
        defect.setReviewOpinion(req.getOpinion());

        if (from != to) {
            ReviewDecision decision = to.ordinal() > from.ordinal()
                    ? ReviewDecision.UPGRADE : ReviewDecision.DOWNGRADE;
            DefectHandlingRule newRule = handlingRuleService.mustGetRule(defect.getDefectType(), to);

            // 风险等级变化：重新生成处置时限、维修建议与责任人，前后口径全部留痕
            DefectLevelChange change = new DefectLevelChange();
            change.setDefectId(defect.getId());
            change.setChangeType(decision);
            change.setFromLevel(from);
            change.setToLevel(to);
            change.setRuleId(newRule.getId());
            change.setDeadlineBefore(defect.getDeadline());
            change.setAdviceBefore(defect.getRepairAdvice());
            change.setResponsibleBefore(defect.getResponsiblePerson());
            change.setReviewer(req.getReviewer());
            change.setAnomalyReason(req.getAnomalyReason());
            change.setOpinion(req.getOpinion());
            change.setChangedAt(LocalDateTime.now());

            // 处置时限以登记时刻为锚点按新等级重算，保持全缺陷同一计时口径
            LocalDateTime anchor = defect.getCreateTime() != null
                    ? defect.getCreateTime() : change.getChangedAt();
            LocalDateTime newDeadline = anchor.plusHours(newRule.getDeadlineHours());
            change.setDeadlineAfter(newDeadline);
            change.setAdviceAfter(newRule.getAdviceTemplate());
            change.setResponsibleAfter(newRule.getDefaultResponsible());
            levelChangeMapper.insert(change);

            defect.setDefectLevel(to);
            defect.setRuleId(newRule.getId());
            defect.setDeadline(newDeadline);
            defect.setRepairAdvice(newRule.getAdviceTemplate());
            defect.setResponsiblePerson(newRule.getDefaultResponsible());
            // 初判与复核不一致：记录异常原因
            defect.setAnomalyReason(req.getAnomalyReason());

            auditService.record("DEFECT", defect.getId(), decision.name(),
                    from + "→" + to + " 责任人=" + newRule.getDefaultResponsible()
                            + (req.getAnomalyReason() != null ? " 异常原因=" + req.getAnomalyReason() : ""),
                    req.getReviewer());
        } else {
            auditService.record("DEFECT", defect.getId(), "CONFIRM",
                    "复核维持初判 " + from, req.getReviewer());
        }

        defectMapper.updateById(defect);
        return defect;
    }

    /** 追加说明：缺陷关闭后唯一允许的写入；处理中缺陷也可补充 */
    @Transactional
    public DefectNote addNote(Long defectId, DefectNoteRequest req) {
        mustGet(defectId);
        DefectNote note = new DefectNote();
        note.setDefectId(defectId);
        note.setNote(req.getNote());
        note.setAuthor(req.getAuthor());
        noteMapper.insert(note);
        auditService.record("DEFECT", defectId, "ADD_NOTE", req.getNote(), req.getAuthor());
        return note;
    }

    /** 影像标注反查：按标注所在影像地址反查原单、处理人与异常原因（一张影像可多条标注） */
    public List<DefectTraceDto> traceByImage(String imageUrl) {
        List<DefectAnnotation> annotations = annotationMapper.selectList(
                new QueryWrapper<DefectAnnotation>()
                        .eq("image_url", imageUrl)
                        .orderByAsc("id"));
        List<DefectTraceDto> result = new ArrayList<>();
        for (DefectAnnotation a : annotations) {
            result.add(buildTrace(a));
        }
        return result;
    }

    /** 影像标注反查：按标注 id 精确定位一条 */
    public DefectTraceDto traceByAnnotation(Long annotationId) {
        DefectAnnotation annotation = annotationMapper.selectById(annotationId);
        if (annotation == null) {
            throw new IllegalArgumentException("影像标注不存在: " + annotationId);
        }
        return buildTrace(annotation);
    }

    private DefectTraceDto buildTrace(DefectAnnotation a) {
        Defect defect = mustGet(a.getDefectId());
        InspectionOrder order = mustGetOrder(a.getOrderId());
        DefectTraceDto dto = new DefectTraceDto();
        dto.setAnnotationId(a.getId());
        dto.setImageUrl(a.getImageUrl());
        dto.setPositionX(a.getPositionX());
        dto.setPositionY(a.getPositionY());
        dto.setLengthMm(a.getLengthMm());
        dto.setWidthMm(a.getWidthMm());
        dto.setScreenshotUrl(a.getScreenshotUrl());
        dto.setAnnotationInspector(a.getInspector());
        dto.setAnnotationRemark(a.getRemark());

        dto.setDefectId(defect.getId());
        dto.setDefectType(defect.getDefectType() == null ? null : defect.getDefectType().name());
        dto.setInitialLevel(defect.getInitialLevel() == null ? null : defect.getInitialLevel().name());
        dto.setCurrentLevel(defect.getDefectLevel() == null ? null : defect.getDefectLevel().name());
        dto.setDefectStatus(defect.getStatus() == null ? null : defect.getStatus().name());
        dto.setAnomalyReason(defect.getAnomalyReason());

        // 处理人：初判巡检员 / 复核专家 / 当前维修责任人
        dto.setInspector(defect.getInspector());
        dto.setReviewer(defect.getReviewer());
        dto.setResponsiblePerson(defect.getResponsiblePerson());

        // 原巡检单
        dto.setOrderId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setTurbineId(order.getTurbineId());
        dto.setInspectTime(order.getInspectTime());
        dto.setOrderImageUrl(order.getDroneImageUrl());
        return dto;
    }

    public Defect mustGet(Long id) {
        Defect defect = defectMapper.selectById(id);
        if (defect == null) {
            throw new IllegalArgumentException("缺陷不存在: " + id);
        }
        return defect;
    }

    private InspectionOrder mustGetOrder(Long id) {
        InspectionOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("巡检工单不存在: " + id);
        }
        return order;
    }

    public List<DefectAnnotation> listAnnotations(Long defectId) {
        return annotationMapper.selectList(new QueryWrapper<DefectAnnotation>()
                .eq("defect_id", defectId).orderByAsc("id"));
    }

    public List<DefectLevelChange> listLevelChanges(Long defectId) {
        return levelChangeMapper.selectList(new QueryWrapper<DefectLevelChange>()
                .eq("defect_id", defectId).orderByAsc("id"));
    }

    public List<DefectNote> listNotes(Long defectId) {
        return noteMapper.selectList(new QueryWrapper<DefectNote>()
                .eq("defect_id", defectId).orderByAsc("id"));
    }
}
