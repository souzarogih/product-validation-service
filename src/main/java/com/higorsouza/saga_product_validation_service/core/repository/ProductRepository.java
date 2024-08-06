package com.higorsouza.saga_product_validation_service.core.repository;

import com.higorsouza.saga_product_validation_service.core.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

    Boolean existsByCode(String code);
}
