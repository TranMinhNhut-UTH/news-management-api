package com.uth.news.service;

import com.uth.news.constant.AppConstants;
import com.uth.news.dto.request.NewsRequest;
import com.uth.news.dto.response.*;
import com.uth.news.entity.News;
import com.uth.news.exception.*;
import com.uth.news.repository.*;
import java.util.ArrayList;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NewsService {
    private final NewsRepository newsRepository;
    private final UserRepository userRepository;

    public NewsService(NewsRepository newsRepository, UserRepository userRepository) {
        this.newsRepository = newsRepository;
        this.userRepository = userRepository;
    }

    public PageResponse<NewsResponse> list(int page, int size) {
        return PageResponse.from(newsRepository.findAll(pageable(page, size,
                AppConstants.DEFAULT_SORT_BY, AppConstants.DEFAULT_SORT_DIRECTION)).map(NewsResponse::from));
    }

    public NewsResponse get(Long id) { return NewsResponse.from(find(id)); }

    public PageResponse<NewsResponse> search(String keyword, String category, String sortBy,
                                           String direction, int page, int size) {
        Pageable pageable = pageable(page, size, sortBy, direction);
        Specification<News> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (keyword != null && !keyword.isBlank()) {
                // Treat SQL LIKE metacharacters as literal keyword characters.
                String pattern = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("content")), pattern, '\\')));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category.trim()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(newsRepository.findAll(specification, pageable).map(NewsResponse::from));
    }

    @Transactional
    public NewsResponse create(NewsRequest request, String username) {
        var author = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        return NewsResponse.from(newsRepository.saveAndFlush(new News(request.title(), request.content(),
                request.category(), request.imageUrl(), author)));
    }

    @Transactional
    public NewsResponse update(Long id, NewsRequest request, String username) {
        News news = find(id);
        checkOwner(news, username);
        news.setTitle(request.title());
        news.setContent(request.content());
        news.setCategory(request.category());
        news.setImageUrl(request.imageUrl());
        return NewsResponse.from(newsRepository.saveAndFlush(news));
    }

    @Transactional
    public void delete(Long id, String username) {
        News news = find(id);
        checkOwner(news, username);
        newsRepository.delete(news);
    }

    private News find(Long id) {
        return newsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("News not found"));
    }

    private void checkOwner(News news, String username) {
        if (!news.getAuthor().getUsername().equals(username)) {
            throw new ForbiddenException("Only the author can modify this news");
        }
    }

    private Pageable pageable(int page, int size, String sortBy, String direction) {
        if (page < 0 || size < 1 || size > AppConstants.MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be >= 0 and size must be between 1 and 100");
        }
        if (!AppConstants.ALLOWED_SORT_FIELDS.contains(sortBy)
                || !AppConstants.ALLOWED_SORT_DIRECTIONS.contains(direction)) {
            throw new BadRequestException("sortBy must be title or createdAt; direction must be asc or desc");
        }
        // Unique tie-breaker keeps pagination stable for equal titles/timestamps.
        Sort.Direction order = Sort.Direction.fromString(direction);
        return PageRequest.of(page, size, Sort.by(order, sortBy).and(Sort.by(order, "id")));
    }
}
