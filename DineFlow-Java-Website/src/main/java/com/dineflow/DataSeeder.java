package com.dineflow;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class DataSeeder {
 @Bean CommandLineRunner seed(MenuRepository repo) { return args -> {
  if(repo.count()==0) {
   repo.save(new MenuItem("Masala Dosa","Breakfast",120,"🥞",true));
   repo.save(new MenuItem("Idli Sambar","Breakfast",80,"🍚",true));
   repo.save(new MenuItem("Paneer Butter Masala","Main Course",260,"🥘",true));
   repo.save(new MenuItem("Veg Biryani","Main Course",220,"🥘",true));
   repo.save(new MenuItem("Butter Naan","Main Course",50,"🫓",true));
   repo.save(new MenuItem("Samosa (2 pcs)","Snacks",40,"🥟",true));
   repo.save(new MenuItem("Veg Burger","Snacks",110,"🍔",true));
   repo.save(new MenuItem("Gulab Jamun","Dessert",70,"🍡",false));
   repo.save(new MenuItem("Mango Lassi","Drinks",90,"🥭",true));
   repo.save(new MenuItem("Masala Chai","Drinks",30,"☕",true));
  }
 }; }
}
