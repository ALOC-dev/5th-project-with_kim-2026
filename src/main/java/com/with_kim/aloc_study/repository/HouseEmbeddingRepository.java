package com.with_kim.aloc_study.repository;

import com.with_kim.aloc_study.dto.HouseSearchFilter;
import com.with_kim.aloc_study.entity.House;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class HouseEmbeddingRepository {
    private final EntityManager em;

    //매물 1개 임베딩 저장
    public void updateEmbedding(Long houseId,String vectorLiteral){
        em.createNativeQuery("""
                UPDATE houses
                SET feature_embedding = CAST(:v AS vector)
                WHERE id=:id
                """)
                .setParameter("v",vectorLiteral)
                .setParameter("id",houseId)
                .executeUpdate();
    }

    //쿼리 백터와 코사인 거리가 가까운 매물 topk개
    @SuppressWarnings("unchecked")
    public List<House> searchByVector(String vectorLiteral, HouseSearchFilter f,int topK){
        StringBuilder sql=new StringBuilder("""
                SELECT h.*
                FROM houses h
                JOIN buildings b ON h.building_id=b.id
                WHERE h.feature_embedding IS NOT NULL
                """);

        Map<String,Object> params=new HashMap<>();

        if(f.contractType()!=null){
            sql.append(" AND h.contract_type = :ct");
            params.put("ct",f.contractType().name());
        }
        if(f.priceMin()!=null){
            sql.append(" AND h.price>= :pmin");
            params.put("pmin",f.priceMin());
        }
        if (f.priceMax() != null) {
            sql.append(" AND h.price <= :pmax");
            params.put("pmax", f.priceMax());
        }
        if (f.depositMin() != null) {
            sql.append(" AND h.deposit >= :dmin");
            params.put("dmin", f.depositMin());
        }
        if (f.depositMax() != null) {
            sql.append(" AND h.deposit <= :dmax");
            params.put("dmax", f.depositMax());
        }
        if (f.monthlyRentMin() != null) {
            sql.append(" AND h.monthly_rent >= :mrmin");
            params.put("mrmin", f.monthlyRentMin());
        }
        if (f.monthlyRentMax() != null) {
            sql.append(" AND h.monthly_rent <= :mrmax");
            params.put("mrmax", f.monthlyRentMax());
        }
        if (f.roomNumber() != null) {
            sql.append(" AND h.room_number = :rn");
            params.put("rn", f.roomNumber());
        }
        if (Boolean.TRUE.equals(f.excludeBanjiha())) {
            sql.append(" AND h.floor > 0");
        }
        if (f.floorMin() != null) {
            sql.append(" AND h.floor >= :fmin");
            params.put("fmin", f.floorMin());
        }
        if (f.areaMin() != null) {
            sql.append(" AND h.area >= :amin");
            params.put("amin", f.areaMin());
        }
        if (f.areaMax() != null) {
            sql.append(" AND h.area <= :amax");
            params.put("amax", f.areaMax());
        }
        if (f.direction() != null) {
            sql.append(" AND h.direction = :dir");
            params.put("dir", f.direction().name());
        }
        if (f.sggName() != null) {
            sql.append(" AND b.sgg_name = :sgg");
            params.put("sgg", f.sggName());
        }
        if (f.emdName() != null) {
            sql.append(" AND b.emd_name = :emd");
            params.put("emd", f.emdName());
        }

        Double campusDist = null;
        if (f.campusMaxMeters() != null) {
            campusDist = f.campusMaxMeters().doubleValue();
        } else if (f.campusMaxMinutes() != null) {
            campusDist = f.campusMaxMinutes() * 70.0;
        }
        if(campusDist!=null){
            sql.append("""
             AND EXISTS (
                SELECT 1 FROM school_buildings s
                WHERE s.location IS NOT NULL
                  AND b.location IS NOT NULL
                  AND ST_DWithin(
                        b.location::geography,
                        s.location::geography,
                        :campusDist
                      )
            )
            """);
            params.put("campusDist", campusDist);
        }

        sql.append(" ORDER BY h.feature_embedding <=> CAST(:v AS vector) LIMIT :k");

        Query q=em.createNativeQuery(sql.toString(),House.class)
                .setParameter("v",vectorLiteral)
                .setParameter("k", topK);
        params.forEach(q::setParameter);

        return q.getResultList();
    }
}
