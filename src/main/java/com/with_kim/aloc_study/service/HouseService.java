package com.with_kim.aloc_study.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.with_kim.aloc_study.dto.HouseSearchCondition;
import com.with_kim.aloc_study.dto.request.HouseCreateRequest;
import com.with_kim.aloc_study.dto.request.HouseUpdateRequest;
import com.with_kim.aloc_study.dto.response.HouseResponse;
import com.with_kim.aloc_study.dto.response.HouseSchoolDistanceResponse;
import com.with_kim.aloc_study.entity.Building;
import com.with_kim.aloc_study.entity.House;
import com.with_kim.aloc_study.entity.Users;
import com.with_kim.aloc_study.exception.InvalidRequestException;
import com.with_kim.aloc_study.exception.ResourceNotFoundException;
import com.with_kim.aloc_study.repository.AnalysisResultRepository;
import com.with_kim.aloc_study.repository.BuildingRepository;
import com.with_kim.aloc_study.repository.HouseQueryRepository;
import com.with_kim.aloc_study.repository.HouseRepository;
import com.with_kim.aloc_study.repository.ReviewRepository;
import com.with_kim.aloc_study.repository.SubmissionRepository;
import com.with_kim.aloc_study.repository.UserRepository;
import com.with_kim.aloc_study.repository.VerifiedAddressRepository;
import com.with_kim.aloc_study.repository.WishListRepository;
import com.with_kim.aloc_study.repository.projection.HouseSchoolDistanceProjection;
import com.with_kim.aloc_study.util.HouseViewCountUpdater;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HouseService {

    private static final String DONGDAEMUNGU_NAME = "동대문구";
    private static final String DONGDAEMUNGU_CODE = "11230";
    private static final Map<String, String> CODES = Map.of(
            "회기동", "1123010100",
            "전농동", "1123010400",
            "휘경동", "1123010900"
    );

    private final HouseRepository houseRepository;
    private final BuildingRepository buildingRepository;
    private final MetadataService metadataService;
    private final HouseQueryRepository houseQueryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final HouseViewCountUpdater viewCountUpdater;
    private final WishListRepository wishListRepository;
    private final ReviewRepository reviewRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final SubmissionRepository submissionRepository;
    private final VerifiedAddressRepository verifiedAddressRepository;

    public Page<HouseResponse> getAllHouses(Pageable pageable) {
        return houseRepository.findAllActiveWithBuilding(pageable)
                .map(HouseResponse::from);
    }

    @Transactional(readOnly = true)
    public HouseResponse getHouse(Long houseId, Long requesterId) {
        House house = houseRepository.findByIdWithBuilding(houseId)
                .orElseThrow(() -> new ResourceNotFoundException("매물을 찾을 수 없습니다. id=" + houseId));

        boolean isActive = house.getListingStatus() == House.ListingStatus.ACTIVE;
        boolean isOwner = requesterId != null
                && house.getUsers() != null
                && house.getUsers().getId() == requesterId;

        if (!isActive && !isOwner) {
            throw new ResourceNotFoundException("매물을 찾을 수 없습니다. id=" + houseId);
        }

        metadataService.updateMetadataIfNeeded(house);
        viewCountUpdater.increase(houseId);
        return HouseResponse.from(house);
    }

    //모든 학교 건물과의 거리 반환
    public List<HouseSchoolDistanceResponse> getSchoolDistances(Long houseId) {
        houseRepository.findById(houseId)
                .orElseThrow(() -> new ResourceNotFoundException("매물을 찾을 수 없습니다. id=" + houseId));

        List<HouseSchoolDistanceProjection> projections = houseRepository.findAllSchoolDistancesByHouseId(houseId);

        if (projections.isEmpty()) {
            throw new ResourceNotFoundException("등록된 학교 건물이 없습니다.");
        }

        return projections.stream()
                .map(p -> new HouseSchoolDistanceResponse(
                        p.getHouseId(),
                        p.getSchoolBuildingId(),
                        p.getSchoolBuildingName(),
                        Math.round(p.getDistanceMeters() * 100) / 100.0
                ))
                .toList();
    }

    //조건 검색
    public List<HouseResponse> searchHouses(HouseSearchCondition condition) {
        validateSearchCondition(condition);
        return houseQueryRepository.searchHouses(condition).stream()
                .map(HouseResponse::from)
                .toList();
    }

    private void validateSearchCondition(HouseSearchCondition condition) {
        boolean anyBoundingBox = condition.swLat() != null || condition.swLng() != null
                || condition.neLat() != null || condition.neLng() != null;
        boolean allBoundingBox = condition.swLat() != null && condition.swLng() != null
                && condition.neLat() != null && condition.neLng() != null;
        if (anyBoundingBox && !allBoundingBox) {
            throw new InvalidRequestException("사각형 범위 검색은 swLat, swLng, neLat, neLng가 모두 필요합니다.");
        }

        boolean hasCenterPos = condition.centerLat() != null || condition.centerLng() != null;
        boolean validCenterPos = condition.centerLat() != null && condition.centerLng() != null;
        if(hasCenterPos && !validCenterPos) {
            throw new InvalidRequestException("중심 좌표가 올바르지 않습니다");
        }
        if(validCenterPos && condition.radiusMeters() == null){
            throw new InvalidRequestException("반경을 지정해야 합니다");
        }
        if (condition.radiusMeters() != null && condition.radiusMeters() <= 0) {
            throw new InvalidRequestException("반경은 0보다 커야 합니다.");
        }

        if (allBoundingBox && condition.radiusMeters() != null) {
            throw new InvalidRequestException("사각형 범위와 원형 범위는 동시에 사용할 수 없습니다.");
        }

        if (condition.schoolBuildingId() != null && condition.maxDistanceFromSchool() == null) {
            throw new InvalidRequestException("학교 건물 조건 검색은 maxDistanceFromSchool을 지정해야 합니다.");
        }
        if (condition.maxDistanceFromSchool() != null && condition.schoolBuildingId() == null) {
            throw new InvalidRequestException("maxDistanceFromSchool을 지정하려면 schoolBuildingId가 필요합니다.");
        }
        if (condition.maxDistanceFromSchool() != null && condition.maxDistanceFromSchool() <= 0) {
            throw new InvalidRequestException("학교와의 거리는 0보다 커야 합니다.");
        }

        if (condition.minPrice() != null && condition.maxPrice() != null
                && condition.minPrice() > condition.maxPrice()) {
            throw new InvalidRequestException("보증금 최소값이 최대값보다 클 수 없습니다.");
        }

        if (condition.minDeposit() != null && condition.maxDeposit() != null
                && condition.minDeposit() > condition.maxDeposit()) {
            throw new InvalidRequestException("보증금 최소값이 최대값보다 클 수 없습니다.");
        }

        if (condition.minMonthlyRent() != null && condition.maxMonthlyRent() != null
                && condition.minMonthlyRent() > condition.maxMonthlyRent()) {
            throw new InvalidRequestException("월세 최소값이 최대값보다 클 수 없습니다.");
        }
    }

    // 매물 비교
    @Transactional(readOnly = true)
    public List<HouseResponse> compareHouses(List<Long> houseIds) {
        if(houseIds == null || houseIds.isEmpty()) {
            throw new InvalidRequestException("비교할 매물 ID를 입력하세요.");
        }

        if(houseIds.size() > 3) {
            throw new InvalidRequestException("매물 비교는 최대 3개까지 가능합니다.");
        }

        List<House> houses = houseRepository.findAllActiveByIdInWithBuilding(houseIds);

        if(houses.size() != houseIds.size()) {
            throw new ResourceNotFoundException("존재하지 않는 매물이 포함되어 있습니다.");
        }

        return houses.stream()
                .map(HouseResponse::from)
                .toList();
    }

    // 매물 등록
    @Transactional
    public HouseResponse createHouse(Long userId, HouseCreateRequest request) {
        Users user = findUser(userId);
        validateAgent(user);

        Building building = resolveBuilding(request);

        House house = House.create(
                building,
                user,
                request.price(),
                request.deposit(),
                request.monthlyRent(),
                request.area(),
                request.roomNumber(),
                request.toilet(),
                request.managementFee(),
                House.ContractType.valueOf(request.contractType()),
                request.floor(),
                House.Direction.valueOf(request.direction()),
                request.description(),
                toMetadataJson(request),
                request.imageUrls()
        );


        House savedHouse = houseRepository.save(house);

        return HouseResponse.from(savedHouse);
    }

    private Building resolveBuilding(HouseCreateRequest request) {
        if(request.buildingId() != null && request.buildingId() != 0) {
            return buildingRepository.findById(request.buildingId())
                    .orElseThrow(() -> new ResourceNotFoundException("건물을 찾을 수 없습니다. id=" + request.buildingId()));
        }

        validateNewBuildingRequest(request);
        String emdName = parseSupportedEmdName(request.address());

        return buildingRepository.findByAddress(request.address())
                .orElseGet(() -> buildingRepository.save(Building.of(
                        request.address(),
                        null,
                        request.latitude(),
                        request.longitude(),
                        null,
                        DONGDAEMUNGU_CODE,
                        DONGDAEMUNGU_NAME,
                        CODES.get(emdName),
                        emdName,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )));
    }

    private void validateNewBuildingRequest(HouseCreateRequest request) {
        if(request.address() == null || request.address().isBlank()) {
            throw new InvalidRequestException("buildingId가 0이면 건물 주소는 필수입니다.");
        }

        if(request.latitude() == null || request.longitude() == null) {
            throw new InvalidRequestException("buildingId가 0이면 건물 위도와 경도는 필수입니다.");
        }
    }

    private String parseSupportedEmdName(String address) {
        if(!address.contains(DONGDAEMUNGU_NAME)) {
            throw new InvalidRequestException("현재는 동대문구 매물만 등록할 수 있습니다.");
        }

        return CODES.keySet()
                .stream()
                .filter(address::contains)
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("현재는 전농동, 휘경동, 회기동 매물만 등록할 수 있습니다."));
    }

    // 내가 등록한 매물 수정
    @Transactional
    public HouseResponse updateMyHouse(Long userId, Long houseId, HouseUpdateRequest request) {
        Users user = findUser(userId);
        validateAgent(user);

        House house = houseRepository.findByIdWithBuilding(houseId)
                .orElseThrow(() -> new ResourceNotFoundException("매물을 찾을 수 없습니다. id=" + houseId));
        validateHouseOwner(house, userId);

        Building building = null;
        if(request.buildingId() != null) {
            building = buildingRepository.findById(request.buildingId())
                    .orElseThrow(() -> new ResourceNotFoundException("건물을 찾을 수 없습니다. id=" + request.buildingId()));
        }

        house.update(
                building,
                request.price(),
                request.deposit(),
                request.monthlyRent(),
                request.area(),
                request.roomNumber(),
                request.toilet(),
                request.managementFee(),
                parseContractType(request.contractType()),
                request.floor(),
                parseDirection(request.direction()),
                request.description(),
                toMetadataJson(request.metadata()),
                request.imageUrls()
        );

        return HouseResponse.from(house);
    }

    // 내가 등록한 매물 삭제
    @Transactional
    public void deleteMyHouse(Long userId, Long houseId) {
        Users user = findUser(userId);
        validateAgent(user);

        House house = houseRepository.findByIdWithBuilding(houseId)
                .orElseThrow(() -> new ResourceNotFoundException("매물을 찾을 수 없습니다. id=" + houseId));
        validateHouseOwner(house, userId);

        wishListRepository.deleteByHouse_Id(houseId);
        reviewRepository.deleteByHouse_Id(houseId);
        analysisResultRepository.deleteBySubmission_House_Id(houseId);
        submissionRepository.deleteByHouse_Id(houseId);
        verifiedAddressRepository.deleteByHouse_Id(houseId);
        houseRepository.delete(house);
    }

    //내가 등록한 매물 노출 상태 변경
    @Transactional
    public void updateMyHouseListingStatus(Long userId, Long houseId, String listingStatus) {
        Users user = findUser(userId);
        validateAgent(user);

        House house = houseRepository.findByIdWithBuilding(houseId)
                .orElseThrow(() -> new ResourceNotFoundException("매물을 찾을 수 없습니다. id=" + houseId));
        validateHouseOwner(house, userId);

        house.updateListingStatus(parseListingStatus(listingStatus));
    }

    private String toMetadataJson(HouseCreateRequest request) {
        return toMetadataJson(request.metadata());
    }

    private String toMetadataJson(Object metadata) {
        if (metadata == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("metadata 형식이 올바르지 않습니다.");
        }
    }

    private Users findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다. id=" + userId));
    }

    private void validateAgent(Users user) {
        if(user.getRole() != Users.Role.AGENT && user.getRole() != Users.Role.ADMIN) {
            throw new AccessDeniedException("매물 등록 권한이 없습니다.");
        }
    }

    private void validateHouseOwner(House house, Long userId) {
        if (house.getUsers() == null || house.getUsers().getId() != userId) {
            throw new AccessDeniedException("본인이 등록한 매물만 수정 또는 삭제할 수 있습니다.");
        }
    }

    private House.ContractType parseContractType(String contractType) {
        if (contractType == null) {
            return null;
        }

        try {
            return House.ContractType.valueOf(contractType);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("contractType 값이 올바르지 않습니다.");
        }
    }

    private House.Direction parseDirection(String direction) {
        if (direction == null) {
            return null;
        }

        try {
            return House.Direction.valueOf(direction);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("direction 값이 올바르지 않습니다.");
        }
    }

    private House.ListingStatus parseListingStatus(String listingStatus) {
        try {
            return House.ListingStatus.valueOf(listingStatus);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidRequestException(
                    "listingStatus는 ACTIVE, HIDDEN, COMPLETED 중 하나여야 합니다."
            );
        }
    }

    //House 등록 후 Metadata 채우기
    public void updateHouseMetadataAfterCreate(Long houseId) {
        House house = houseRepository.findByIdWithBuilding(houseId)
                .orElseThrow(() -> new ResourceNotFoundException("매물을 찾을 수 없습니다. id=" + houseId));
        metadataService.updateMetadataIfNeeded(house);
    }

    // 내가 등록한 매물 조회
    @Transactional(readOnly = true)
    public Page<HouseResponse> getMyHouses(Long userId, Pageable pageable) {
        return houseRepository.findByUsers_IdWithBuilding(userId, pageable)
                .map(HouseResponse::from);
    }

}
