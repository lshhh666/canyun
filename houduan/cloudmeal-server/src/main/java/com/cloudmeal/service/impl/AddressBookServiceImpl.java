package com.cloudmeal.service.impl;

import com.cloudmeal.context.BaseContext;
import com.cloudmeal.entity.AddressBook;
import com.cloudmeal.exception.AddressBookBusinessException;
import com.cloudmeal.mapper.AddressBookMapper;
import com.cloudmeal.service.AddressBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Slf4j
public class AddressBookServiceImpl implements AddressBookService {
    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 条件查询
     *
     * @param addressBook
     * @return
     */
    public List<AddressBook> list(AddressBook addressBook) {
        if (addressBook == null) {
            addressBook = new AddressBook();
        }
        addressBook.setUserId(requireUserId());
        return addressBookMapper.list(addressBook);
    }

    /**
     * 新增地址
     *
     * @param addressBook
     */
    public void save(AddressBook addressBook) {
        requireInput(addressBook);
        addressBook.setUserId(requireUserId());
        addressBook.setIsDefault(0);
        addressBookMapper.insert(addressBook);
    }

    /**
     * 根据id查询
     *
     * @param id
     * @return
     */
    public AddressBook getById(Long id) {
        return requireOwnedAddress(id, requireUserId());
    }

    /**
     * 根据id修改地址
     *
     * @param addressBook
     */
    public void update(AddressBook addressBook) {
        requireInput(addressBook);
        Long userId = requireUserId();
        requireOwnedAddress(addressBook.getId(), userId);
        addressBook.setUserId(userId);
        // 默认地址只允许通过专用事务切换，不接受普通编辑请求修改。
        addressBook.setIsDefault(null);
        if (addressBook.getConsignee() == null && addressBook.getSex() == null
                && addressBook.getPhone() == null
                && addressBook.getProvinceCode() == null
                && addressBook.getProvinceName() == null
                && addressBook.getCityCode() == null
                && addressBook.getCityName() == null
                && addressBook.getDistrictCode() == null
                && addressBook.getDistrictName() == null
                && addressBook.getDetail() == null
                && addressBook.getLabel() == null) {
            return;
        }
        if (addressBookMapper.update(addressBook) == 0) {
            // 兼容数据库返回“实际变更行数”时的重复编辑，删除竞争仍返回不可用。
            requireOwnedAddress(addressBook.getId(), userId);
        }
    }

    /**
     * 设置默认地址
     *
     * @param addressBook
     */
    @Transactional
    public void setDefault(AddressBook addressBook) {
        requireInput(addressBook);
        Long userId = requireUserId();
        requireOwnedAddress(addressBook.getId(), userId);
        //1、将当前用户的所有地址修改为非默认地址 update address_book set is_default = ? where user_id = ?
        AddressBook reset = AddressBook.builder().userId(userId).isDefault(0).build();
        addressBookMapper.updateIsDefaultByUserId(reset);

        //2、将当前地址改为默认地址 update address_book set is_default = ? where id = ?
        AddressBook target = AddressBook.builder().id(addressBook.getId())
                .userId(userId).isDefault(1).build();
        if (addressBookMapper.update(target) != 1) {
            // 目标被并发删除时回滚前面的清除操作。
            throw unavailable();
        }
    }

    /**
     * 根据id删除地址
     *
     * @param id
     */
    public void deleteById(Long id) {
        Long userId = requireUserId();
        requireValidId(id);
        if (addressBookMapper.deleteOwnedById(id, userId) != 1) {
            throw unavailable();
        }
    }

    private Long requireUserId() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null || userId <= 0) {
            throw new AddressBookBusinessException("请先登录");
        }
        return userId;
    }

    private AddressBook requireOwnedAddress(Long id, Long userId) {
        requireValidId(id);
        AddressBook address = addressBookMapper.getOwnedById(id, userId);
        if (address == null) {
            throw unavailable();
        }
        return address;
    }

    private void requireValidId(Long id) {
        if (id == null || id <= 0) {
            throw unavailable();
        }
    }

    private void requireInput(AddressBook addressBook) {
        if (addressBook == null) {
            throw unavailable();
        }
    }

    private AddressBookBusinessException unavailable() {
        return new AddressBookBusinessException("地址不存在或不可访问");
    }

}
