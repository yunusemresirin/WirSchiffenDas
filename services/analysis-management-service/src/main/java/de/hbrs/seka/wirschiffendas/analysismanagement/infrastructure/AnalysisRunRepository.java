package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AnalysisRun;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AnalysisRun r where r.analysisId = :analysisId")
    Optional<AnalysisRun> findForUpdate(@Param("analysisId") String analysisId);

    @Query("select r.analysisId from AnalysisRun r where r.overallResult is null "
            + "and (r.lastProgressAt is null or r.lastProgressAt < :cutoff)")
    List<String> findInactiveIds(@Param("cutoff") Instant cutoff);
}