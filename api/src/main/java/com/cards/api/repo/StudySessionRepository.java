package com.cards.api.repo;

import com.cards.api.entity.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface StudySessionRepository extends JpaRepository<StudySession, Long>, JpaSpecificationExecutor<StudySession> {

    long countByUserId(Long userId);
}