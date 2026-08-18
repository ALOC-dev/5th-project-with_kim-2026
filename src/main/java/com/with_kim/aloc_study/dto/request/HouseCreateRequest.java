package com.with_kim.aloc_study.dto.request;

import com.with_kim.aloc_study.dto.MetadataDto;

import java.util.List;

public record HouseCreateRequest(
        Long buildingId,
        String address,
        Double latitude,
        Double longitude,
        Long price,
        Long deposit,
        Long monthlyRent,
        Double area,
        Integer roomNumber,
        Integer toilet,
        Long managementFee,
        String contractType,
        Integer floor,
        String direction,
        String description,
        MetadataDto metadata,
        List<String> imageUrls
) {
}
