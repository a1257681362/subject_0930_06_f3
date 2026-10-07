package com.evops;

import com.evops.dto.AcceptRequest;
import com.evops.dto.AnnotationDto;
import com.evops.dto.BladeRequest;
import com.evops.dto.DefectNoteRequest;
import com.evops.dto.DefectRegisterRequest;
import com.evops.dto.DefectTraceDto;
import com.evops.dto.HandlingRuleRequest;
import com.evops.dto.InspectSubmitRequest;
import com.evops.dto.OrderCreateRequest;
import com.evops.dto.RepairRequest;
import com.evops.dto.ReviewRequest;
import com.evops.entity.Blade;
import com.evops.entity.Defect;
import com.evops.entity.DefectHandlingRule;
import com.evops.entity.DefectLevelChange;
import com.evops.entity.DefectNote;
import com.evops.entity.InspectionOrder;
import com.evops.entity.Turbine;
import com.evops.enums.BladePosition;
import com.evops.enums.DefectLevel;
import com.evops.enums.DefectStatus;
import com.evops.enums.DefectType;
import com.evops.enums.ReviewDecision;
import com.evops.service.BladeService;
import com.evops.service.DefectLifecycleService;
import com.evops.service.HandlingRuleService;
import com.evops.service.InspectionService;
import com.evops.service.TurbineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F3 叶片缺陷分级管理与影像标注反查：
 * 处置时限配置、影像标注登记、专家复核升降级不覆盖初判、风险变化重生成建议与责任人、
 * 已关闭只能追加说明、影像标注反查原单/处理人/异常原因。
 */
@SpringBootTest
@Transactional
class DefectLifecycleFlowTest {

    @Autowired
    private TurbineService turbineService;
    @Autowired
    private BladeService bladeService;
    @Autowired
    private InspectionService inspectionService;
    @Autowired
    private DefectLifecycleService defectLifecycleService;
    @Autowired
    private HandlingRuleService handlingRuleService;

    private static final String IMG = "oss://evops/drone/F40/20261007/a1.jpg";

    private BladeRequest blade(String no, String serial, BladePosition pos) {
        BladeRequest req = new BladeRequest();
        req.setBladeNo(no);
        req.setSerialNo(serial);
        req.setBladePosition(pos);
        req.setMaterial("玻璃纤维增强复合材料");
        return req;
    }

    private Turbine createTurbine(String no) {
        com.evops.dto.TurbineCreateRequest req = new com.evops.dto.TurbineCreateRequest();
        req.setTurbineNo(no);
        req.setSite("张北坝上风电场");
        req.setModel("WT2000-D110");
        req.setGridConnectedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        req.setBlades(Arrays.asList(
                blade(no + "-B1", "SN-" + no + "-1", BladePosition.A),
                blade(no + "-B2", "SN-" + no + "-2", BladePosition.B),
                blade(no + "-B3", "SN-" + no + "-3", BladePosition.C)));
        return turbineService.create(req);
    }

    private InspectionOrder inspectedOrder(String turbineNo) {
        Turbine turbine = createTurbine(turbineNo);
        OrderCreateRequest orderReq = new OrderCreateRequest();
        orderReq.setOrderNo("IO-" + turbineNo + "-001");
        orderReq.setTurbineId(turbine.getId());
        InspectionOrder order = inspectionService.createOrder(orderReq);

        InspectSubmitRequest inspect = new InspectSubmitRequest();
        inspect.setDroneImageUrl(IMG);
        inspect.setInspectTime(LocalDateTime.of(2026, 10, 7, 10, 0));
        return inspectionService.submitInspection(order.getId(), inspect);
    }

    private AnnotationDto annotation(Double x, Double y, Double length, Double width) {
        AnnotationDto a = new AnnotationDto();
        a.setImageUrl(IMG);
        a.setPositionX(x);
        a.setPositionY(y);
        a.setLengthMm(length);
        a.setWidthMm(width);
        a.setScreenshotUrl(IMG + ".crop.png");
        a.setRemark("裂纹沿前缘走向");
        return a;
    }

    private Defect register(InspectionOrder order, Blade blade, DefectType type,
                            DefectLevel level, String inspector) {
        DefectRegisterRequest req = new DefectRegisterRequest();
        req.setBladeId(blade.getId());
        req.setDefectType(type);
        req.setDefectLevel(level);
        req.setInspector(inspector);
        req.setDescription(type + " 缺陷");
        req.setAnnotations(Arrays.asList(annotation(320.0, 180.0, 120.0, 3.0)));
        return defectLifecycleService.register(order.getId(), req);
    }

    @Test
    void 处置时限规则_按类型等级_默认齐全() {
        List<DefectHandlingRule> rules = handlingRuleService.listRules(null);
        // 4 类缺陷 × 4 等级
        assertEquals(16, rules.size());

        DefectHandlingRule criticalCrack =
                handlingRuleService.mustGetRule(DefectType.CRACK, DefectLevel.CRITICAL);
        assertEquals(24, criticalCrack.getDeadlineHours());
        assertNotNull(criticalCrack.getDefaultResponsible());
        assertTrue(criticalCrack.getAdviceTemplate().contains("停机"));
    }

    @Test
    void 配置调整_只影响之后登记_不回改已有时限() {
        InspectionOrder order = inspectedOrder("F41");
        Blade bladeA = bladeService.findInService(order.getTurbineId(), BladePosition.A);
        LocalDateTime before = LocalDateTime.now();
        Defect first = register(order, bladeA, DefectType.CRACK, DefectLevel.GENERAL, "张工");
        // 默认一般裂纹 240h
        assertEquals(240, Duration.between(before, first.getDeadline()).toHours());

        // 调整规则：一般裂纹收紧到 120h 并更换责任班组
        HandlingRuleRequest ruleReq = new HandlingRuleRequest();
        ruleReq.setDefectType(DefectType.CRACK);
        ruleReq.setDefectLevel(DefectLevel.GENERAL);
        ruleReq.setDeadlineHours(120);
        ruleReq.setDefaultResponsible("应急抢修组");
        ruleReq.setAdviceTemplate("收紧后模板：5日内完成灌注修复");
        handlingRuleService.upsertRule(ruleReq);

        Defect second = register(order, bladeA, DefectType.CRACK, DefectLevel.GENERAL, "张工");
        assertEquals(120, Duration.between(before, second.getDeadline()).toHours());
        assertEquals("应急抢修组", second.getResponsiblePerson());

        // 已登记缺陷的时限/责任人不回改
        Defect firstReload = defectLifecycleService.mustGet(first.getId());
        assertEquals(240, Duration.between(firstReload.getCreateTime(), firstReload.getDeadline()).toHours());
        assertFalse("应急抢修组".equals(firstReload.getResponsiblePerson()));
    }

    @Test
    void 登记_命中时限_生成维修建议责任人_并保存影像标注() {
        InspectionOrder order = inspectedOrder("F42");
        Blade bladeA = bladeService.findInService(order.getTurbineId(), BladePosition.A);
        LocalDateTime before = LocalDateTime.now();

        Defect defect = register(order, bladeA, DefectType.CRACK, DefectLevel.CRITICAL, "巡检员-张工");

        assertEquals(DefectType.CRACK, defect.getDefectType());
        assertEquals(DefectStatus.OPEN, defect.getStatus());
        // 初判 = 当前等级
        assertEquals(DefectLevel.CRITICAL, defect.getInitialLevel());
        assertEquals(DefectLevel.CRITICAL, defect.getDefectLevel());
        // 处置时限 = 登记时刻 + 24h
        assertEquals(24, Duration.between(before, defect.getDeadline()).toHours());
        assertNotNull(defect.getRepairAdvice());
        assertEquals("结构损伤应急组", defect.getResponsiblePerson());
        assertNull(defect.getAnomalyReason());

        // 影像标注：位置、尺寸与截图均落库
        assertEquals(1, defectLifecycleService.listAnnotations(defect.getId()).size());
        assertEquals(Double.valueOf(120.0),
                defectLifecycleService.listAnnotations(defect.getId()).get(0).getLengthMm());
    }

    @Test
    void 专家复核_升级_不覆盖初判_重新生成建议与责任人() {
        InspectionOrder order = inspectedOrder("F43");
        Blade bladeA = bladeService.findInService(order.getTurbineId(), BladePosition.A);
        Defect defect = register(order, bladeA, DefectType.LIGHTNING, DefectLevel.MINOR, "张工");
        assertEquals("防雷检测组", defect.getResponsiblePerson());
        assertEquals(720, Duration.between(defect.getCreateTime(), defect.getDeadline()).toHours());

        ReviewRequest review = new ReviewRequest();
        review.setReviewedLevel(DefectLevel.SERIOUS);
        review.setReviewer("专家-王工");
        review.setAnomalyReason("巡检影像分辨率不足，漏判叶尖分层");
        review.setOpinion("升级为严重，停机检查防雷通道");
        defect = defectLifecycleService.review(defect.getId(), review);

        // 初判不被覆盖
        assertEquals(DefectLevel.MINOR, defect.getInitialLevel());
        assertEquals(DefectLevel.SERIOUS, defect.getDefectLevel());
        assertEquals("专家-王工", defect.getReviewer());
        assertNotNull(defect.getReviewedAt());
        // 风险变化：时限/建议/责任人重新生成
        assertEquals(48, Duration.between(defect.getCreateTime(), defect.getDeadline()).toHours());
        assertEquals("应急抢修组", defect.getResponsiblePerson());
        assertTrue(defect.getRepairAdvice().contains("48小时"));
        assertEquals("巡检影像分辨率不足，漏判叶尖分层", defect.getAnomalyReason());

        List<DefectLevelChange> changes = defectLifecycleService.listLevelChanges(defect.getId());
        assertEquals(1, changes.size());
        DefectLevelChange change = changes.get(0);
        assertEquals(ReviewDecision.UPGRADE, change.getChangeType());
        assertEquals(DefectLevel.MINOR, change.getFromLevel());
        assertEquals(DefectLevel.SERIOUS, change.getToLevel());
        assertEquals("防雷检测组", change.getResponsibleBefore());
        assertEquals("应急抢修组", change.getResponsibleAfter());
        assertNotNull(change.getAdviceBefore());
        assertNotNull(change.getAdviceAfter());
        assertNotNull(change.getDeadlineBefore());
        assertNotNull(change.getDeadlineAfter());
        assertEquals("巡检影像分辨率不足，漏判叶尖分层", change.getAnomalyReason());
    }

    @Test
    void 专家复核_降级() {
        InspectionOrder order = inspectedOrder("F44");
        Blade bladeA = bladeService.findInService(order.getTurbineId(), BladePosition.A);
        Defect defect = register(order, bladeA, DefectType.ICING, DefectLevel.CRITICAL, "张工");

        ReviewRequest review = new ReviewRequest();
        review.setReviewedLevel(DefectLevel.GENERAL);
        review.setReviewer("专家-王工");
        review.setAnomalyReason("覆冰实测厚度低于初判");
        defect = defectLifecycleService.review(defect.getId(), review);

        assertEquals(DefectLevel.CRITICAL, defect.getInitialLevel());
        assertEquals(DefectLevel.GENERAL, defect.getDefectLevel());
        assertEquals("场站运维班", defect.getResponsiblePerson());
        DefectLevelChange change = defectLifecycleService.listLevelChanges(defect.getId()).get(0);
        assertEquals(ReviewDecision.DOWNGRADE, change.getChangeType());
    }

    @Test
    void 复核维持初判_不留等级变化() {
        InspectionOrder order = inspectedOrder("F45");
        Blade bladeA = bladeService.findInService(order.getTurbineId(), BladePosition.A);
        Defect defect = register(order, bladeA, DefectType.COATING, DefectLevel.MINOR, "张工");

        ReviewRequest review = new ReviewRequest();
        review.setReviewedLevel(DefectLevel.MINOR);
        review.setReviewer("专家-王工");
        review.setOpinion("维持初判");
        defect = defectLifecycleService.review(defect.getId(), review);

        assertEquals(DefectLevel.MINOR, defect.getInitialLevel());
        assertEquals(DefectLevel.MINOR, defect.getDefectLevel());
        assertEquals("专家-王工", defect.getReviewer());
        assertTrue(defectLifecycleService.listLevelChanges(defect.getId()).isEmpty());
    }

    @Test
    void 关闭后_只能追加说明_不能复核或维修() {
        InspectionOrder order = inspectedOrder("F46");
        Blade bladeA = bladeService.findInService(order.getTurbineId(), BladePosition.A);
        Defect defect = register(order, bladeA, DefectType.COATING, DefectLevel.GENERAL, "张工");

        RepairRequest repair = new RepairRequest();
        repair.setRepairResult("清理打磨后补涂防护层");
        inspectionService.repair(defect.getId(), repair);
        AcceptRequest accept = new AcceptRequest();
        accept.setPass(true);
        accept.setRemark("复检合格");
        defect = inspectionService.accept(defect.getId(), accept);

        assertEquals(DefectStatus.ACCEPTED, defect.getStatus());
        assertNotNull(defect.getClosedAt());
        final Long closedDefectId = defect.getId();

        // 关闭后不能复核、不能再维修
        ReviewRequest review = new ReviewRequest();
        review.setReviewedLevel(DefectLevel.SERIOUS);
        review.setReviewer("专家-王工");
        assertThrows(IllegalArgumentException.class,
                () -> defectLifecycleService.review(closedDefectId, review));
        assertThrows(IllegalArgumentException.class,
                () -> inspectionService.repair(closedDefectId, repair));

        // 只能追加说明
        DefectNoteRequest noteReq = new DefectNoteRequest();
        noteReq.setNote("关闭后跟踪：补涂区无脱落");
        noteReq.setAuthor("场站-李工");
        DefectNote note = defectLifecycleService.addNote(closedDefectId, noteReq);
        assertNotNull(note.getId());
        List<DefectNote> notes = defectLifecycleService.listNotes(closedDefectId);
        assertEquals(1, notes.size());
        assertEquals("关闭后跟踪：补涂区无脱落", notes.get(0).getNote());
    }

    @Test
    void 影像标注反查_原单_处理人_异常原因() {
        InspectionOrder order = inspectedOrder("F47");
        Blade bladeA = bladeService.findInService(order.getTurbineId(), BladePosition.A);
        Defect defect = register(order, bladeA, DefectType.LIGHTNING, DefectLevel.GENERAL, "巡检员-赵工");

        // 专家升级并给出异常原因
        ReviewRequest review = new ReviewRequest();
        review.setReviewedLevel(DefectLevel.CRITICAL);
        review.setReviewer("专家-王工");
        review.setAnomalyReason("初判未识别接闪器烧蚀");
        defectLifecycleService.review(defect.getId(), review);

        // 从影像地址反查
        List<DefectTraceDto> traces = defectLifecycleService.traceByImage(IMG);
        assertEquals(1, traces.size());
        DefectTraceDto trace = traces.get(0);

        // 原单
        assertEquals(order.getId(), trace.getOrderId());
        assertEquals(order.getOrderNo(), trace.getOrderNo());
        assertEquals(order.getTurbineId(), trace.getTurbineId());
        assertEquals(IMG, trace.getOrderImageUrl());
        // 缺陷分类：初判 vs 当前
        assertEquals("LIGHTNING", trace.getDefectType());
        assertEquals("GENERAL", trace.getInitialLevel());
        assertEquals("CRITICAL", trace.getCurrentLevel());
        // 处理人：初判巡检员、复核专家、当前责任人
        assertEquals("巡检员-赵工", trace.getInspector());
        assertEquals("专家-王工", trace.getReviewer());
        assertEquals("应急抢修组", trace.getResponsiblePerson());
        // 异常原因
        assertEquals("初判未识别接闪器烧蚀", trace.getAnomalyReason());
        // 标注位置/尺寸/截图
        assertEquals(Double.valueOf(320.0), trace.getPositionX());
        assertEquals(Double.valueOf(3.0), trace.getWidthMm());
        assertEquals(IMG + ".crop.png", trace.getScreenshotUrl());

        // 按标注 id 精确反查
        Long annotationId = defectLifecycleService.listAnnotations(defect.getId()).get(0).getId();
        DefectTraceDto byId = defectLifecycleService.traceByAnnotation(annotationId);
        assertEquals(defect.getId(), byId.getDefectId());
        assertEquals(order.getOrderNo(), byId.getOrderNo());
    }

    @Test
    void 一张影像多条标注_反查返回全部缺陷() {
        InspectionOrder order = inspectedOrder("F48");
        Blade bladeA = bladeService.findInService(order.getTurbineId(), BladePosition.A);
        Blade bladeB = bladeService.findInService(order.getTurbineId(), BladePosition.B);
        register(order, bladeA, DefectType.CRACK, DefectLevel.MINOR, "张工");
        register(order, bladeB, DefectType.COATING, DefectLevel.GENERAL, "张工");

        List<DefectTraceDto> traces = defectLifecycleService.traceByImage(IMG);
        assertEquals(2, traces.size());
    }

    @Test
    void 缺少时限规则_登记被拦截() {
        // 删除一类规则制造配置缺口
        DefectHandlingRule rule =
                handlingRuleService.mustGetRule(DefectType.CRACK, DefectLevel.SERIOUS);
        handlingRuleService.removeById(rule.getId());

        InspectionOrder order = inspectedOrder("F49");
        Blade bladeA = bladeService.findInService(order.getTurbineId(), BladePosition.A);
        assertThrows(IllegalArgumentException.class,
                () -> register(order, bladeA, DefectType.CRACK, DefectLevel.SERIOUS, "张工"));
    }
}
