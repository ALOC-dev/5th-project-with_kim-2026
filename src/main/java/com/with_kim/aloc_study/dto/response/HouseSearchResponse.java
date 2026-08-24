package com.with_kim.aloc_study.dto.response;

import com.with_kim.aloc_study.dto.NearbyInfo;
import com.with_kim.aloc_study.entity.Building;
import com.with_kim.aloc_study.entity.House;

public record HouseSearchResponse(
        Long id, String contractType, Long price,Long deposit, Long monthlyRent,Long managementFee,
        Integer roomNumber, Integer floor, Double area, String direction,
        String address,Double latitude, Double longitude, Integer constructionYear,Integer campusDistanceMeters,
        Integer campusWalkMinutes,String nearestSubwayName, Integer subwayDistanceMeters, String description
) {
    public static HouseSearchResponse from(House h, NearbyInfo nearby){
        Building b=h.getBuilding();
        return new HouseSearchResponse(
                h.getId(),
                h.getContractType()!=null ? h.getContractType().name():null,
                h.getPrice(),
                h.getDeposit(),
                h.getMonthlyRent(),
                h.getManagementFee(),
                h.getRoomNumber(),
                h.getFloor(),
                h.getArea(),
                h.getDirection()!=null? h.getDirection().name():null,
                b.getAddress(),
                b.getLatitude(),
                b.getLongitude(),
                b.getConstructionYear(),
                nearby != null ? nearby.nearestCampusMeters() : null,
                nearby != null ? nearby.nearestCampusMinutes() : null,
                nearby !=null ? nearby.nearestSubwayName() : null,
                nearby !=null ? nearby.nearestSubwayMeters():null,
                h.getDescription()
        );
    }
}
