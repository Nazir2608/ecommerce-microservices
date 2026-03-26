package com.nazir.ecommerce.productservice.repository;

import com.nazir.ecommerce.productservice.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    Page<Product> findByCategoryAndStatus(String category, Product.ProductStatus status, Pageable pageable);

    Page<Product> findBySellerIdAndStatus(String sellerId, Product.ProductStatus status, Pageable pageable);

    Page<Product> findByStatus(Product.ProductStatus status, Pageable pageable);

    @Query("{ $or: [ " +
            "{ 'name':        { $regex: ?0, $options: 'i' } }, " +
            "{ 'description': { $regex: ?0, $options: 'i' } }, " +
            "{ 'brand':       { $regex: ?0, $options: 'i' } }, " +
            "{ 'tags':        { $regex: ?0, $options: 'i' } }  " +
            "], 'status': 'ACTIVE' }")
    Page<Product> searchActive(String query, Pageable pageable);

    @Query("{ 'category': ?0, 'status': 'ACTIVE', 'stockQuantity': { $gt: 0 } }")
    Page<Product> findInStockByCategory(String category, Pageable pageable);
}
