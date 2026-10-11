package com.uth.news.service;

import com.uth.news.entity.News;
import com.uth.news.repository.*;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import static org.mockito.Mockito.*;

class NewsSearchTest {
    @Test
    @SuppressWarnings("unchecked")
    void keywordMatchesTitleOrContentAndCategoryIsAnExactFilter() {
        NewsRepository repository = mock(NewsRepository.class);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        NewsService service = new NewsService(repository, mock(UserRepository.class));
        service.search(" A%_\\B ", " Tech ", "createdAt", "desc", 0, 10);
        ArgumentCaptor<Specification<News>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture(), any(Pageable.class));

        Root<News> root = mock(Root.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<String> title = mock(Path.class), content = mock(Path.class), category = mock(Path.class);
        Expression<String> lowerTitle = mock(Expression.class), lowerContent = mock(Expression.class);
        Predicate titleMatch = mock(Predicate.class), contentMatch = mock(Predicate.class);
        Predicate keywordMatch = mock(Predicate.class), categoryMatch = mock(Predicate.class);
        when(root.<String>get("title")).thenReturn(title);
        when(root.<String>get("content")).thenReturn(content);
        when(root.<String>get("category")).thenReturn(category);
        when(cb.lower(title)).thenReturn(lowerTitle);
        when(cb.lower(content)).thenReturn(lowerContent);
        String pattern = "%a\\%\\_\\\\b%";
        when(cb.like(lowerTitle, pattern, '\\')).thenReturn(titleMatch);
        when(cb.like(lowerContent, pattern, '\\')).thenReturn(contentMatch);
        when(cb.or(titleMatch, contentMatch)).thenReturn(keywordMatch);
        when(cb.equal(category, "Tech")).thenReturn(categoryMatch);
        captor.getValue().toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(cb).like(lowerTitle, pattern, '\\');
        verify(cb).like(lowerContent, pattern, '\\');
        verify(cb).or(titleMatch, contentMatch);
        verify(cb).equal(category, "Tech");
        verify(cb).and(new Predicate[]{keywordMatch, categoryMatch});
    }

    @Test
    @SuppressWarnings("unchecked")
    void blankFiltersDoNotRestrictResults() {
        NewsRepository repository = mock(NewsRepository.class);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        NewsService service = new NewsService(repository, mock(UserRepository.class));
        for (String filter : new String[]{null, "  "}) {
            service.search(filter, filter, "title", "asc", 0, 10);
        }
        ArgumentCaptor<Specification<News>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository, times(2)).findAll(captor.capture(), any(Pageable.class));
        for (Specification<News> specification : captor.getAllValues()) {
            Root<News> root = mock(Root.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class);
            specification.toPredicate(root, mock(CriteriaQuery.class), cb);
            verify(cb).and(new Predicate[0]);
            verifyNoInteractions(root);
        }
    }
}
