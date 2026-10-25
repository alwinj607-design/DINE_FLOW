package com.dineflow;
import jakarta.persistence.*;
@Entity
public class MenuItem {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 public String name;
 public String category;
 public double price;
 public String emoji;
 @Column(length=600000) public String imageData;
 public boolean published=true;
 public MenuItem() {}
 public MenuItem(String n,String c,double p,String e,boolean pub){name=n;category=c;price=p;emoji=e;published=pub;}
}
