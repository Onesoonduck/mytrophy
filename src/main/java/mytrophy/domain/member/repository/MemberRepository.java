package mytrophy.domain.member.repository;

import mytrophy.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Integer> {
    Boolean existsByUsername(String username);

    Member findByUsername(String username);
}
