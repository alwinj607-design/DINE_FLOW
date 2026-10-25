package com.dineflow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MenuRepository extends JpaRepository<MenuItem,Long> {
 List<MenuItem> findByPublishedTrueOrderByIdAsc();
}
