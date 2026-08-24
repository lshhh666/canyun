package com.sky.service;

import com.sky.dto.CouponDTO;
import com.sky.dto.CouponPageQueryDTO;
import com.sky.entity.Coupon;
import com.sky.enums.CouponStatus;
import com.sky.exception.CouponBusinessException;
import com.sky.mapper.CouponMapper;
import com.sky.result.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 管理端优惠券真实数据库状态流转测试。
 * 每个测试完成后自动回滚，不保留测试优惠券。
 */
@SpringBootTest(properties = "sky.websocket.enabled=false")
@Transactional
class CouponAdminIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponMapper couponMapper;

    @Test
    void shouldCreateEditStartAndStopThroughRealMapper() {
        CouponDTO createDTO = validDTO(uniqueName());
        couponService.createCoupon(createDTO);
        Coupon coupon = findByName(createDTO.getName());

        assertNotNull(coupon.getId());
        assertEquals(CouponStatus.DRAFT, coupon.getStatus());
        assertEquals(100, coupon.getTotalStock());
        assertEquals(100, coupon.getStock());

        CouponDTO updateDTO = validDTO(createDTO.getName() + "-修改");
        updateDTO.setTotalStock(80);
        couponService.updateDraft(coupon.getId(), updateDTO);
        Coupon updated = couponMapper.selectById(coupon.getId());
        assertEquals(80, updated.getTotalStock());
        assertEquals(80, updated.getStock());

        couponService.startDistribution(coupon.getId());
        assertEquals(CouponStatus.DISTRIBUTING,
                couponMapper.selectById(coupon.getId()).getStatus());
        assertThrows(CouponBusinessException.class,
                () -> couponService.updateDraft(coupon.getId(), validDTO("不能修改")));

        couponService.stopDistribution(coupon.getId());
        assertEquals(CouponStatus.DISABLED,
                couponMapper.selectById(coupon.getId()).getStatus());
        assertThrows(CouponBusinessException.class,
                () -> couponService.deleteDraft(coupon.getId()));
    }

    @Test
    void shouldPageAndDeleteDraftThroughRealMapper() {
        CouponDTO couponDTO = validDTO(uniqueName());
        couponService.createCoupon(couponDTO);
        Coupon coupon = findByName(couponDTO.getName());

        CouponPageQueryDTO queryDTO = new CouponPageQueryDTO();
        queryDTO.setPage(1);
        queryDTO.setPageSize(10);
        queryDTO.setName(couponDTO.getName());
        queryDTO.setStatus(CouponStatus.DRAFT.getValue());
        PageResult page = couponService.pageQuery(queryDTO);

        assertEquals(1L, page.getTotal());
        assertEquals(coupon.getId(), ((Coupon) page.getRecords().get(0)).getId());

        couponService.deleteDraft(coupon.getId());
        assertNull(couponMapper.selectById(coupon.getId()));
    }

    @Test
    void shouldAllowOnlyOneAtomicStartTransition() {
        CouponDTO couponDTO = validDTO(uniqueName());
        couponService.createCoupon(couponDTO);
        Coupon coupon = findByName(couponDTO.getName());
        LocalDateTime now = LocalDateTime.now();

        assertEquals(1, couponMapper.startDistribution(coupon.getId(), now));
        assertEquals(0, couponMapper.startDistribution(coupon.getId(), now.plusSeconds(1)));
    }

    private Coupon findByName(String name) {
        return couponService.lambdaQuery().eq(Coupon::getName, name).one();
    }

    private CouponDTO validDTO(String name) {
        LocalDateTime receiveStart = LocalDateTime.now().plusHours(1).withNano(0);
        CouponDTO couponDTO = new CouponDTO();
        couponDTO.setName(name);
        couponDTO.setThresholdAmount(new BigDecimal("30.00"));
        couponDTO.setDiscountAmount(new BigDecimal("5.00"));
        couponDTO.setTotalStock(100);
        couponDTO.setReceiveStartTime(receiveStart);
        couponDTO.setReceiveEndTime(receiveStart.plusDays(2));
        couponDTO.setValidStartTime(receiveStart);
        couponDTO.setValidEndTime(receiveStart.plusDays(7));
        return couponDTO;
    }

    private String uniqueName() {
        return "管理端测试券-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
