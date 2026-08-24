package com.sky.service;

import com.sky.dto.CouponDTO;
import com.sky.entity.Coupon;
import com.sky.enums.CouponStatus;
import com.sky.exception.CouponBusinessException;
import com.sky.mapper.CouponMapper;
import com.sky.service.impl.CouponServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端优惠券状态机与服务端字段归属测试。
 */
class CouponAdminServiceTest {

    private CouponMapper couponMapper;
    private CouponServiceImpl couponService;

    @BeforeEach
    void setUp() {
        couponMapper = mock(CouponMapper.class);
        couponService = new CouponServiceImpl();
        ReflectionTestUtils.setField(couponService, "couponMapper", couponMapper);
        ReflectionTestUtils.setField(couponService, "userCouponService", mock(UserCouponService.class));
    }

    @Test
    void createShouldOwnStatusRemainingStockAndAuditTimes() {
        when(couponMapper.insert(any(Coupon.class))).thenReturn(1);

        couponService.createCoupon(validDTO());

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponMapper).insert(captor.capture());
        Coupon saved = captor.getValue();
        assertEquals(CouponStatus.DRAFT, saved.getStatus());
        assertEquals(saved.getTotalStock(), saved.getStock());
        assertEquals(100, saved.getStock());
        assertNotNull(saved.getCreateTime());
        assertEquals(saved.getCreateTime(), saved.getUpdateTime());
    }

    @Test
    void createShouldRejectInvalidValidityWindowBeforeWriting() {
        CouponDTO couponDTO = validDTO();
        couponDTO.setValidEndTime(couponDTO.getReceiveEndTime().minusMinutes(1));

        assertThrows(CouponBusinessException.class, () -> couponService.createCoupon(couponDTO));
        verify(couponMapper, never()).insert(any(Coupon.class));
    }

    @Test
    void createShouldRejectMoneyThatDatabaseCannotStoreExactly() {
        CouponDTO subCentDiscount = validDTO();
        subCentDiscount.setDiscountAmount(new BigDecimal("0.001"));
        assertThrows(CouponBusinessException.class,
                () -> couponService.createCoupon(subCentDiscount));

        CouponDTO overflowingThreshold = validDTO();
        overflowingThreshold.setThresholdAmount(new BigDecimal("100000000.00"));
        assertThrows(CouponBusinessException.class,
                () -> couponService.createCoupon(overflowingThreshold));

        verify(couponMapper, never()).insert(any(Coupon.class));
    }

    @Test
    void updateShouldOnlyWriteAStillDraftCoupon() {
        Coupon current = validCoupon(CouponStatus.DRAFT);
        when(couponMapper.selectById(10L)).thenReturn(current);
        when(couponMapper.updateDraft(eq(10L), any(Coupon.class))).thenReturn(1);

        couponService.updateDraft(10L, validDTO());

        verify(couponMapper).updateDraft(eq(10L), any(Coupon.class));
    }

    @Test
    void updateShouldRejectDistributingCoupon() {
        when(couponMapper.selectById(11L)).thenReturn(validCoupon(CouponStatus.DISTRIBUTING));

        assertThrows(CouponBusinessException.class,
                () -> couponService.updateDraft(11L, validDTO()));
        verify(couponMapper, never()).updateDraft(eq(11L), any(Coupon.class));
    }

    @Test
    void startShouldUseAtomicDraftTransition() {
        when(couponMapper.selectById(12L)).thenReturn(validCoupon(CouponStatus.DRAFT));
        when(couponMapper.startDistribution(eq(12L), any(LocalDateTime.class))).thenReturn(1);

        couponService.startDistribution(12L);

        verify(couponMapper).startDistribution(eq(12L), any(LocalDateTime.class));
    }

    @Test
    void startShouldReportConcurrentStateChange() {
        when(couponMapper.selectById(13L)).thenReturn(validCoupon(CouponStatus.DRAFT));
        when(couponMapper.startDistribution(eq(13L), any(LocalDateTime.class))).thenReturn(0);

        assertThrows(CouponBusinessException.class,
                () -> couponService.startDistribution(13L));
    }

    @Test
    void stopAndDeleteShouldEnforceTheirSourceStates() {
        when(couponMapper.selectById(14L)).thenReturn(validCoupon(CouponStatus.DISTRIBUTING));
        when(couponMapper.stopDistribution(eq(14L), any(LocalDateTime.class))).thenReturn(1);
        couponService.stopDistribution(14L);

        when(couponMapper.selectById(15L)).thenReturn(validCoupon(CouponStatus.DRAFT));
        when(couponMapper.deleteDraft(15L)).thenReturn(1);
        couponService.deleteDraft(15L);

        when(couponMapper.selectById(16L)).thenReturn(validCoupon(CouponStatus.DISABLED));
        assertThrows(CouponBusinessException.class, () -> couponService.deleteDraft(16L));
        verify(couponMapper, never()).deleteDraft(16L);
    }

    private CouponDTO validDTO() {
        LocalDateTime receiveStart = LocalDateTime.now().plusHours(1);
        CouponDTO couponDTO = new CouponDTO();
        couponDTO.setName("测试满减券");
        couponDTO.setThresholdAmount(new BigDecimal("30.00"));
        couponDTO.setDiscountAmount(new BigDecimal("5.00"));
        couponDTO.setTotalStock(100);
        couponDTO.setReceiveStartTime(receiveStart);
        couponDTO.setReceiveEndTime(receiveStart.plusDays(2));
        couponDTO.setValidStartTime(receiveStart);
        couponDTO.setValidEndTime(receiveStart.plusDays(7));
        return couponDTO;
    }

    private Coupon validCoupon(CouponStatus status) {
        CouponDTO couponDTO = validDTO();
        return new Coupon()
                .setId(1L)
                .setName(couponDTO.getName())
                .setThresholdAmount(couponDTO.getThresholdAmount())
                .setDiscountAmount(couponDTO.getDiscountAmount())
                .setTotalStock(couponDTO.getTotalStock())
                .setStock(couponDTO.getTotalStock())
                .setReceiveStartTime(couponDTO.getReceiveStartTime())
                .setReceiveEndTime(couponDTO.getReceiveEndTime())
                .setValidStartTime(couponDTO.getValidStartTime())
                .setValidEndTime(couponDTO.getValidEndTime())
                .setStatus(status);
    }
}
