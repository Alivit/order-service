package com.minispring.orderservice.mapper;

import com.minispring.grpc.service.UserDto;
import com.minispring.orderservice.dto.response.UserProfileView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    @Mapping(target = "id", source = "user")
    @Mapping(target = "birthDate", source = "user")
    UserProfileView toView(UserDto user);

    default UUID mapUuid(UserDto user) {
        return (user == null || user.getId().isBlank()) ? null : UUID.fromString(user.getId());
    }

    default LocalDate mapBirthDate(UserDto user) {
        return (user == null || user.getBirthDate().isBlank()) ? null : LocalDate.parse(user.getBirthDate());
    }

    default Instant map(com.google.protobuf.Timestamp timestamp) {
        return (timestamp == null) ? null : Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
