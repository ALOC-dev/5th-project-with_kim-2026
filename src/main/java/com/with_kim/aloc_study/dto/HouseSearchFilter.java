package com.with_kim.aloc_study.dto;

import com.with_kim.aloc_study.entity.House;

public record HouseSearchFilter(
        House.ContractType contractType,
        Long priceMin,          // 매매가 전용
        Long priceMax,
        Long depositMin,        // 보증금
        Long depositMax,
        Long monthlyRentMin,    // 월세
        Long monthlyRentMax,
        Integer roomNumber,
        Boolean excludeBanjiha,
        Integer floorMin,
        Double areaMin,
        Double areaMax,
        House.Direction direction,
        String sggName,
        String emdName,
        Integer campusMaxMinutes,
        Integer campusMaxMeters,
        String semanticQuery
) {
    public static HouseSearchFilter fallback(String originalQuery){
        return new HouseSearchFilter(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                originalQuery);
    }
}