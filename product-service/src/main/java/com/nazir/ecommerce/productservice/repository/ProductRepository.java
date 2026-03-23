package com.nazir.ecommerce.productservice.repository;

import com.nazir.ecommerce.productservice.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MongoDB repository for the Product aggregate.
 *
 * LEARNING POINT — MongoRepository vs JpaRepository:
 *   Both work identically at the Spring Data abstraction level.
 *   Method naming conventions (findBy, existsBy) work the same.
 *   MongoDB doesn't support JOIN — relationships must be handled in the service layer.
 *
 * LEARNING POINT — @Query with MongoDB:
 *   Uses MongoDB query syntax inside JSON: { "field": { "$operator": value } }
 *   Unlike JPQL, no SQL — MongoDB query language is document-oriented.
 */
@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findBySku(String sku);
    boolean existsBySku(String sku);

    Page<Product> findByCategoryAndStatus(
            String category, Product.ProductStatus status, Pageable pageable);

    Page<Product> findBySellerIdAndStatus(
            String sellerId, Product.ProductStatus status, Pageable pageable);

    Page<Product> findByStatus(Product.ProductStatus status, Pageable pageable);

    /**
     * Case-insensitive partial search on name, description, brand.
     * LEARNING POINT: $regex in MongoDB is case-insensitive with 'i' option.
     * For production use full-text search with $text index (see @TextIndexed).
     */
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
