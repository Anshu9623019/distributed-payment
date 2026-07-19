package com.distributed_payment.auth.mapper;

import com.distributed_payment.auth.dto.RegisterRequest;
import com.distributed_payment.customer.entity.Customer;
import com.distributed_payment.security.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", constant = "CUSTOMER")
    @Mapping(target = "enabled", constant = "true")
    @Mapping(target = "password", ignore = true) // encoded separately in the service
    User toUserEntity(RegisterRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true) // set manually once the User is persisted
    Customer toCustomerEntity(RegisterRequest request);
}
