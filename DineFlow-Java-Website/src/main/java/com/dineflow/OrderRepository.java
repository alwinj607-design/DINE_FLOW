package com.dineflow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OrderRepository extends JpaRepository<RestaurantOrder,Long> {
 List<RestaurantOrder> findAllByOrderByCreatedAtDesc();
}
