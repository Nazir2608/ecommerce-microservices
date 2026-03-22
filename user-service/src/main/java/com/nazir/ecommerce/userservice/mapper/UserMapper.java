package com.nazir.ecommerce.userservice.mapper;

import com.nazir.ecommerce.userservice.dto.request.RegisterRequest;
import com.nazir.ecommerce.userservice.dto.request.UpdateProfileRequest;
import com.nazir.ecommerce.userservice.dto.response.UserResponse;
import com.nazir.ecommerce.userservice.model.User;
import org.mapstruct.*;

/**
 * MapStruct mapper — generates implementation at compile time.
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — MapStruct                                              │
 * │                                                                          │
 * │  MapStruct reads this interface at compile time and generates a          │
 * │  UserMapperImpl class with the actual mapping logic.                     │
 * │                                                                          │
 * │  Benefits over manual mapping:                                           │
 * │    • No reflection at runtime → fast as hand-written code               │
 * │    • Compile-time error if field types don't match                       │
 * │    • @Mapping annotations handle field name differences                  │
 * │    • @BeanMapping(nullValuePropertyMappingStrategy = IGNORE)             │
 * │      → partial update: only update non-null fields                       │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  LEARNING POINT — componentModel = "spring"                              │
 * │                                                                          │
 * │  Makes the generated class a @Component so it can be @Autowired.        │
 * │  Without this, you'd call Mappers.getMapper(UserMapper.class) manually. │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserMapper {

    /**
     * RegisterRequest → User entity (for saving to DB).
     *
     * Fields ignored:
     *   id, status, roles, emailVerified, createdAt, updatedAt, lastLoginAt,
     *   failedLoginAttempts, lockedUntil — these are set by service layer or Hibernate.
     *
     * NOTE: password is copied as plain text here.
     *       The service layer MUST hash it before saving.
     */
    @Mapping(target = "id",                   ignore = true)
    @Mapping(target = "status",               ignore = true)
    @Mapping(target = "roles",                ignore = true)
    @Mapping(target = "emailVerified",        ignore = true)
    @Mapping(target = "profileImageUrl",      ignore = true)
    @Mapping(target = "createdAt",            ignore = true)
    @Mapping(target = "updatedAt",            ignore = true)
    @Mapping(target = "lastLoginAt",          ignore = true)
    @Mapping(target = "failedLoginAttempts",  ignore = true)
    @Mapping(target = "lockedUntil",          ignore = true)
    User toEntity(RegisterRequest request);

    /**
     * User entity → UserResponse DTO (for API responses).
     * password is not a field in UserResponse, so MapStruct ignores it automatically.
     */
    UserResponse toResponse(User user);

    /**
     * Partial update: apply non-null fields from UpdateProfileRequest onto an existing User.
     *
     * @MappingTarget — the entity is passed in and mutated in place.
     * NullValuePropertyMappingStrategy.IGNORE (set at mapper level) means
     * null fields in the source are skipped — only provided fields are updated.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id",                   ignore = true)
    @Mapping(target = "email",                ignore = true)
    @Mapping(target = "username",             ignore = true)
    @Mapping(target = "password",             ignore = true)
    @Mapping(target = "status",               ignore = true)
    @Mapping(target = "roles",                ignore = true)
    @Mapping(target = "emailVerified",        ignore = true)
    @Mapping(target = "createdAt",            ignore = true)
    @Mapping(target = "updatedAt",            ignore = true)
    @Mapping(target = "lastLoginAt",          ignore = true)
    @Mapping(target = "failedLoginAttempts",  ignore = true)
    @Mapping(target = "lockedUntil",          ignore = true)
    void updateEntity(UpdateProfileRequest request, @MappingTarget User user);
}
