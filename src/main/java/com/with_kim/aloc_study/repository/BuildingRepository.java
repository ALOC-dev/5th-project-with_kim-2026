package com.with_kim.aloc_study.repository;

import com.with_kim.aloc_study.entity.Building;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BuildingRepository extends JpaRepository<Building, Long> {

    Optional<Building> findByAddress(String address);
}
