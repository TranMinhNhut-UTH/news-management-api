package com.uth.news.repository;

import com.uth.news.entity.News;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsRepository extends JpaRepository<News, Long>, JpaSpecificationExecutor<News> {
    @Query("""
            select count(n) > 0 from News n
            where n.imageUrl like :plain or n.imageUrl like :query or n.imageUrl like :fragment
            """)
    boolean isImageReferenced(@Param("plain") String plain, @Param("query") String query,
                              @Param("fragment") String fragment);
}
