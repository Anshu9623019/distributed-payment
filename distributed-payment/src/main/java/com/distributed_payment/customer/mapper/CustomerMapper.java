package com.distributed_payment.customer.mapper;

import com.distributed_payment.customer.dto.CustomerResponse;
import com.distributed_payment.customer.entity.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    @Mapping(target = "id", source = "customer.id")
    @Mapping(target = "fullName", source = "customer.user.fullName")
    @Mapping(target = "email", source = "customer.user.email")
    @Mapping(target = "enabled", source = "customer.user.enabled")
    CustomerResponse toResponse(Customer customer);
}
