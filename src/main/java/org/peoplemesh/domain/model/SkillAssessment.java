package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "skill_assessment", schema = "skills")
@IdClass(SkillAssessment.Key.class)
public class SkillAssessment extends PanacheEntityBase {

    @Id
    @Column(name = "node_id", nullable = false)
    public UUID nodeId;

    @Id
    @Column(name = "skill_id", nullable = false)
    public UUID skillId;

    @Column(nullable = false)
    public short level;

    @Column(nullable = false)
    public boolean interest;

    @Column(nullable = false, length = 20)
    public String source = "SELF";

    @Column(name = "assessed_at", nullable = false)
    public Instant assessedAt;

    @PrePersist
    void onCreate() {
        if (assessedAt == null) assessedAt = Instant.now();
    }

    public static List<SkillAssessment> findByNode(UUID nodeId) {
        return list("nodeId", nodeId);
    }

    public static List<SkillAssessment> findBySkillAndMinLevel(UUID skillId, int minLevel) {
        return list("skillId = ?1 and level >= ?2", skillId, (short) minLevel);
    }

    public static void deleteByNode(UUID nodeId) {
        delete("nodeId", nodeId);
    }

    public static class Key implements Serializable {
        public UUID nodeId;
        public UUID skillId;

        public Key() {}

        public Key(UUID nodeId, UUID skillId) {
            this.nodeId = nodeId;
            this.skillId = skillId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(nodeId, k.nodeId) && Objects.equals(skillId, k.skillId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodeId, skillId);
        }
    }
}
