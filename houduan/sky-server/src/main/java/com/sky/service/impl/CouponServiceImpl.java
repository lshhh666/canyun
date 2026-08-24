package com.sky.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.context.BaseContext;
import com.sky.dto.CouponDTO;
import com.sky.dto.CouponPageQueryDTO;
import com.sky.entity.Coupon;
import com.sky.entity.UserCoupon;
import com.sky.enums.CouponStatus;
import com.sky.enums.UserCouponStatus;
import com.sky.exception.CouponBusinessException;
import com.sky.mapper.CouponMapper;
import com.sky.result.PageResult;
import com.sky.service.CouponService;
import com.sky.service.UserCouponService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;

import java.util.List;

@Service
public class CouponServiceImpl extends ServiceImpl<CouponMapper,Coupon> implements CouponService {
    @Autowired
    private CouponMapper couponMapper;
    @Autowired
    private UserCouponService userCouponService;

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal MAX_MONEY = new BigDecimal("99999999.99");
    @Override
    public List<Coupon> listAvailable() {
        LocalDateTime now = LocalDateTime.now();

        LambdaQueryWrapper<Coupon> queryWrapper =
                new LambdaQueryWrapper<Coupon>()
                        .eq(Coupon::getStatus, CouponStatus.DISTRIBUTING)
                        .gt(Coupon::getStock, 0)
                        .le(Coupon::getReceiveStartTime, now)
                        .ge(Coupon::getReceiveEndTime, now)
                        .orderByDesc(Coupon::getCreateTime);


        return couponMapper.selectList(queryWrapper);
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long couponId) {
        Long userId = BaseContext.getCurrentId();
        LocalDateTime now = LocalDateTime.now();

        // 友好提示；真正的并发防重还要依靠数据库唯一索引
        long count = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId)
                .count();

        if (count > 0) {
            throw new CouponBusinessException("你已经领取过该优惠券");
        }

        // 查询优惠券，用于生成用户领取时的快照
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new CouponBusinessException("优惠券不存在");
        }

        // 数据库原子判断活动状态、领取时间和库存并扣减
        int rows = couponMapper.decrementStock(couponId, now);
        if (rows == 0) {
            throw new CouponBusinessException("优惠券已停发、未到领取时间或库存不足");
        }

        UserCoupon userCoupon = new UserCoupon()
                .setUserId(userId)
                .setCouponId(couponId)
                .setCouponName(coupon.getName())
                .setThresholdAmount(coupon.getThresholdAmount())
                .setDiscountAmount(coupon.getDiscountAmount())
                .setStatus(UserCouponStatus.AVAILABLE)
                .setReceiveTime(now)
                .setValidStartTime(coupon.getValidStartTime())
                .setValidEndTime(coupon.getValidEndTime())
                .setCreateTime(now)
                .setUpdateTime(now);

        try {
            boolean saved = userCouponService.save(userCoupon);
            if (!saved) {
                throw new CouponBusinessException("优惠券领取失败");
            }
        } catch (DuplicateKeyException ex) {
            // 并发请求可能同时通过前置查询，最终由唯一索引兜底。
            // 抛出业务异常后，本次事务中的库存扣减会一并回滚。
            throw new CouponBusinessException("你已经领取过该优惠券");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createCoupon(CouponDTO couponDTO) {
        validateCouponFields(couponDTO);
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = toDraftCoupon(couponDTO, now);
        if (couponMapper.insert(coupon) != 1) {
            throw new CouponBusinessException("优惠券创建失败");
        }
    }

    @Override
    public PageResult pageQuery(CouponPageQueryDTO queryDTO) {
        if (queryDTO == null || queryDTO.getPage() < 1 || queryDTO.getPageSize() < 1
                || queryDTO.getPageSize() > MAX_PAGE_SIZE) {
            throw new CouponBusinessException("分页参数不合法");
        }
        if (queryDTO.getStatus() != null
                && (queryDTO.getStatus() < CouponStatus.DRAFT.getValue()
                || queryDTO.getStatus() > CouponStatus.DISABLED.getValue())) {
            throw new CouponBusinessException("优惠券状态不合法");
        }
        PageHelper.startPage(queryDTO.getPage(), queryDTO.getPageSize());
        Page<Coupon> page = couponMapper.pageQuery(queryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    public Coupon getCouponById(Long id) {
        return requireCoupon(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(Long id, CouponDTO couponDTO) {
        Coupon current = requireCoupon(id);
        if (current.getStatus() != CouponStatus.DRAFT) {
            throw new CouponBusinessException("只有草稿优惠券可以修改");
        }
        validateCouponFields(couponDTO);
        Coupon coupon = toDraftCoupon(couponDTO, LocalDateTime.now());
        if (couponMapper.updateDraft(id, coupon) != 1) {
            throw new CouponBusinessException("优惠券状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startDistribution(Long id) {
        Coupon coupon = requireCoupon(id);
        if (coupon.getStatus() != CouponStatus.DRAFT) {
            throw new CouponBusinessException("只有草稿优惠券可以开始发放");
        }
        validateCouponEntity(coupon);
        LocalDateTime now = LocalDateTime.now();
        if (!coupon.getReceiveEndTime().isAfter(now)) {
            throw new CouponBusinessException("领取结束时间已过，不能开始发放");
        }
        if (couponMapper.startDistribution(id, now) != 1) {
            throw new CouponBusinessException("优惠券状态已变化或发放条件不满足，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stopDistribution(Long id) {
        Coupon coupon = requireCoupon(id);
        if (coupon.getStatus() != CouponStatus.DISTRIBUTING) {
            throw new CouponBusinessException("只有发放中的优惠券可以停发");
        }
        if (couponMapper.stopDistribution(id, LocalDateTime.now()) != 1) {
            throw new CouponBusinessException("优惠券状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDraft(Long id) {
        Coupon coupon = requireCoupon(id);
        if (coupon.getStatus() != CouponStatus.DRAFT) {
            throw new CouponBusinessException("只有草稿优惠券可以删除");
        }
        if (couponMapper.deleteDraft(id) != 1) {
            throw new CouponBusinessException("优惠券状态已变化，请刷新后重试");
        }
    }

    private Coupon requireCoupon(Long id) {
        if (id == null) {
            throw new CouponBusinessException("优惠券ID不能为空");
        }
        Coupon coupon = couponMapper.selectById(id);
        if (coupon == null) {
            throw new CouponBusinessException("优惠券不存在");
        }
        return coupon;
    }

    private Coupon toDraftCoupon(CouponDTO couponDTO, LocalDateTime now) {
        return new Coupon()
                .setName(couponDTO.getName().trim())
                .setThresholdAmount(couponDTO.getThresholdAmount())
                .setDiscountAmount(couponDTO.getDiscountAmount())
                .setTotalStock(couponDTO.getTotalStock())
                .setStock(couponDTO.getTotalStock())
                .setReceiveStartTime(couponDTO.getReceiveStartTime())
                .setReceiveEndTime(couponDTO.getReceiveEndTime())
                .setValidStartTime(couponDTO.getValidStartTime())
                .setValidEndTime(couponDTO.getValidEndTime())
                .setStatus(CouponStatus.DRAFT)
                .setCreateTime(now)
                .setUpdateTime(now);
    }

    private void validateCouponEntity(Coupon coupon) {
        CouponDTO couponDTO = new CouponDTO();
        couponDTO.setName(coupon.getName());
        couponDTO.setThresholdAmount(coupon.getThresholdAmount());
        couponDTO.setDiscountAmount(coupon.getDiscountAmount());
        couponDTO.setTotalStock(coupon.getTotalStock());
        couponDTO.setReceiveStartTime(coupon.getReceiveStartTime());
        couponDTO.setReceiveEndTime(coupon.getReceiveEndTime());
        couponDTO.setValidStartTime(coupon.getValidStartTime());
        couponDTO.setValidEndTime(coupon.getValidEndTime());
        validateCouponFields(couponDTO);
        if (coupon.getStock() == null || coupon.getStock() <= 0
                || coupon.getStock() > coupon.getTotalStock()) {
            throw new CouponBusinessException("优惠券剩余库存不合法");
        }
    }

    private void validateCouponFields(CouponDTO couponDTO) {
        if (couponDTO == null) {
            throw new CouponBusinessException("优惠券信息不能为空");
        }
        String name = couponDTO.getName();
        if (name == null || name.trim().isEmpty() || name.trim().length() > 64) {
            throw new CouponBusinessException("优惠券名称长度必须为1到64个字符");
        }
        BigDecimal threshold = couponDTO.getThresholdAmount();
        BigDecimal discount = couponDTO.getDiscountAmount();
        if (threshold == null || threshold.compareTo(BigDecimal.ZERO) < 0) {
            throw new CouponBusinessException("使用门槛不能小于0");
        }
        if (discount == null || discount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CouponBusinessException("优惠金额必须大于0");
        }
        validateMoney("使用门槛", threshold);
        validateMoney("优惠金额", discount);
        if (threshold.compareTo(BigDecimal.ZERO) > 0 && discount.compareTo(threshold) > 0) {
            throw new CouponBusinessException("优惠金额不能大于使用门槛");
        }
        if (couponDTO.getTotalStock() == null || couponDTO.getTotalStock() <= 0) {
            throw new CouponBusinessException("发行总量必须大于0");
        }
        LocalDateTime receiveStart = couponDTO.getReceiveStartTime();
        LocalDateTime receiveEnd = couponDTO.getReceiveEndTime();
        LocalDateTime validStart = couponDTO.getValidStartTime();
        LocalDateTime validEnd = couponDTO.getValidEndTime();
        if (receiveStart == null || receiveEnd == null || validStart == null || validEnd == null) {
            throw new CouponBusinessException("领取时间和使用时间不能为空");
        }
        if (!receiveStart.isBefore(receiveEnd)) {
            throw new CouponBusinessException("领取开始时间必须早于领取结束时间");
        }
        if (!validStart.isBefore(validEnd)) {
            throw new CouponBusinessException("使用开始时间必须早于使用截止时间");
        }
        if (receiveEnd.isAfter(validEnd)) {
            throw new CouponBusinessException("领取结束时间不能晚于使用截止时间");
        }
    }

    /**
     * coupon 表金额字段为 DECIMAL(10,2)，在业务层提前拒绝精度不兼容的数据，
     * 避免数据库静默四舍五入后出现“优惠金额变成 0”或超出字段范围。
     */
    private void validateMoney(String fieldName, BigDecimal value) {
        if (value.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new CouponBusinessException(fieldName + "最多保留2位小数");
        }
        if (value.compareTo(MAX_MONEY) > 0) {
            throw new CouponBusinessException(fieldName + "不能超过99999999.99元");
        }
    }
}
