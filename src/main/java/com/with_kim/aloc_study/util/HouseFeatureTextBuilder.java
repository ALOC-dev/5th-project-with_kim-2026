package com.with_kim.aloc_study.util;

import com.with_kim.aloc_study.dto.NearbyInfo;
import com.with_kim.aloc_study.entity.Building;
import com.with_kim.aloc_study.entity.House;
import org.springframework.boot.autoconfigure.info.ProjectInfoProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class HouseFeatureTextBuilder {
    private static final int NEW_BUILDING_YEARS = 5;//신축기준
    private static final int OLD_BUILDING_YEARS = 15;//구축기준
    private static final int LOW_FLOOR_MAX = 3;//저층
    private static final double SMALL_AREA_MAX = 40.0; //㎡ 소형
    private static final double MEDIUM_AREA_MAX = 70.0;//40~70㎡ 중형

    public String build(House h, NearbyInfo nearby) {
        Building b = h.getBuilding();
        List<String> parts = new ArrayList<>();

        //계약유형, 가격
        if (h.getContractType() != null) {
            switch (h.getContractType()) {
                case MONTHLY -> {
                    if (h.getMonthlyRent() != null) {
                        long m = manwon(h.getMonthlyRent());
                        if (m <= 50) parts.add("저렴한 월세 가성비");
                        else if (m >= 90) parts.add("비싼 고급");
                    }
                    if (h.getDeposit() != null && manwon(h.getDeposit()) <= 500) {
                        parts.add("보증금 부담 적은");
                    }
                }
                case JEONSE -> {
                    if (h.getDeposit() != null) {
                        long m = manwon(h.getDeposit());
                        if (m <= 30000) parts.add("저렴한 전세 가성비");
                        else if (m >= 80000) parts.add("비싼 고급");
                    }
                }
                case SALE -> {
                    if (h.getPrice() != null) {
                        long m = manwon(h.getPrice());
                        if (m <= 50000) parts.add("저렴한 가성비");
                        else if (m >= 120000) parts.add("비싼 고급 프리미엄");
                    }
                }
            }
        }

//        //관리비, 월 총 부담
//        if(h.getManagementFee()!=null){
//            parts.add("관리비 %d만원".formatted(manwon(h.getManagementFee())));
//            if(h.getContractType()==House.ContractType.MONTHLY && h.getPrice()!=null){
//                parts.add("월 총 부담 %d만원".formatted(manwon(h.getPrice()+h.getManagementFee())));
//            }
//        }

        //면적, 평수 추상 감각
        if(h.getArea()!=null){
//            parts.add("전용면적 %.0f㎡ %d평".formatted(h.getArea(),Math.round(h.getArea()/3.3058)));
            if(h.getArea()<=SMALL_AREA_MAX){
                parts.add("소형");
            }
            else if(h.getArea()<=MEDIUM_AREA_MAX){
                parts.add("중형");
            }
            else{
                parts.add("대형");
            }
        }

        //방 수
        if(h.getRoomNumber()!=null){
//            parts.add("방 %d개".formatted(h.getRoomNumber()));
            switch (h.getRoomNumber()) {
                case 1 -> parts.add("원룸");
                case 2 -> parts.add("투룸");
                case 3 -> parts.add("쓰리룸");
                default -> parts.add("방 많은 넓은 집");
            }
        }

//        //욕실
//        if (h.getToilet() != null){
//            parts.add("욕실 %d개".formatted(h.getToilet()));
//        }

        //층, 반지하 여부, 저층/고층
        if (h.getFloor() != null){
            if(h.getFloor()<=0){
                parts.add("반지하");
            }
            else{
//                parts.add("%d층 지상층".formatted(h.getFloor()));
                parts.add(h.getFloor() <= LOW_FLOOR_MAX ? "저층" : "고층");
            }
        }

        //방향
        if(h.getDirection()!=null){
            parts.add(dir(h.getDirection())+"향");
            if (h.getDirection() == House.Direction.SOUTH) {
                parts.add("채광 좋은 햇빛 잘 드는");
            }
        }

        //신축/구축
        if(b.getConstructionYear()!=null){
            int age = LocalDate.now().getYear() - b.getConstructionYear();
            if (age <= NEW_BUILDING_YEARS){
                parts.add("신축 깨끗한 새 건물");
            }
            else if(age>=OLD_BUILDING_YEARS){
                parts.add("구축");
            }
        }

        //용도
        if (b.getBuildingUsage() != null){
            parts.add(b.getBuildingUsage());
        }
        //행정동
        if (b.getEmdName() != null){
            parts.add(b.getEmdName());
        }
        //자치구
        if (b.getSggName() != null){
            parts.add(b.getSggName());
            if(b.getSggName().endsWith("구")){
                parts.add(b.getSggName().substring(0,b.getSggName().length()-1));
            }
        }

        //캠퍼스 인접
        if (nearby.nearestCampusName() != null) {
//            parts.add("%s 도보 %d분".formatted(nearby.nearestCampusName(), nearby.nearestCampusMinutes()));
            parts.add("학교 캠퍼스 인접");
        }

        //치안, cctv
        boolean hasCctv=nearby.cctvCount()!=null && nearby.cctvCount() > 0;
        boolean hasPolice = nearby.policeCount() != null && nearby.policeCount() > 0;
        if (hasCctv && hasPolice) {
            parts.add("CCTV와 경찰서 있는 치안 좋은 안전한");
        } else if (hasCctv) {
            parts.add("CCTV 있는 치안 좋은 안전한");
        } else if (hasPolice) {
            parts.add("경찰서 인근 치안 좋은");
        }

        //역세권
        if(nearby.nearestSubwayMeters()!=null){
            if (nearby.nearestSubwayName() != null) {
                parts.add(nearby.nearestSubwayName() + " 인근");
            }
            if(nearby.nearestSubwayMeters()<=500){
                parts.add("역세권 지하철 가까운");
            }
            else{
                parts.add("지하철 이용권");
            }
        }

        return String.join(", ", parts);
    }

    private long manwon(Long won){
        return won==null ? 0: won/10_000;
    }

    private String dir(House.Direction d){
        return switch (d){
            case NORTH -> "북";
            case EAST -> "동";
            case SOUTH -> "남";
            case WEST -> "서";
        };
    }
}

