package com.dineflow;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
@Table(name="restaurant_orders")
public class RestaurantOrder {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public String customerName;
 public String orderType;
 public String tableNumber;
 public String status="PLACED";
 public double subtotal,discount,tax,total;
 public String coupon;
 @Column(length=100000) public String itemsJson;
 public LocalDateTime createdAt=LocalDateTime.now();
 public RestaurantOrder() {}
}
