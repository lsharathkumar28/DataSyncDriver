package com.outreach.datasyncdriver.repository;

import com.outreach.datasyncdriver.entity.SchemaMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SchemaMappingRepository extends JpaRepository<SchemaMapping, Long> {

    List<SchemaMapping> findByMappingGroupName(String mappingGroupName);

    List<SchemaMapping> findBySourceSystem(String sourceSystem);

    List<SchemaMapping> findByTargetSystem(String targetSystem);

    List<SchemaMapping> findBySourceSystemAndTargetSystem(String sourceSystem, String targetSystem);

    void deleteByMappingGroupName(String mappingGroupName);
}

