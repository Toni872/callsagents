package com.callsagents.backend.campaigns.repository;

import com.callsagents.backend.campaigns.entity.Campaign;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wiring test for {@link CampaignSpecifications#voiceConfigured(Boolean)}.
 * The normalized service layer maps blank values to null, so "voice config
 * present" == any of the 5 columns NOT NULL (and the inverse for false).
 */
@ExtendWith(MockitoExtension.class)
class CampaignSpecificationsTest {

    @Mock private Root<Campaign> root;
    @Mock private CriteriaQuery<?> query;
    @Mock private CriteriaBuilder cb;
    @Mock private Predicate predicate;

    @Test
    @DisplayName("voiceConfigured(true): OR over 5 NOT NULL column predicates")
    void voiceConfigured_true_orOverFiveNotNull() throws Exception {
        when(cb.isNotNull(any())).thenReturn(predicate);
        when(cb.or(any(), any(), any(), any(), any())).thenReturn(predicate);

        Specification<Campaign> spec = CampaignSpecifications.voiceConfigured(true);
        spec.toPredicate(root, query, cb);

        verify(root, times(5)).get(anyString());
        verify(cb).or(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("voiceConfigured(false): AND over 5 IS NULL column predicates")
    void voiceConfigured_false_andOverFiveIsNull() throws Exception {
        when(cb.isNull(any())).thenReturn(predicate);
        when(cb.and(any(), any(), any(), any(), any())).thenReturn(predicate);

        Specification<Campaign> spec = CampaignSpecifications.voiceConfigured(false);
        spec.toPredicate(root, query, cb);

        verify(root, times(5)).get(anyString());
        verify(cb).and(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("voiceConfigured(null): no constraint (conjunction)")
    void voiceConfigured_null_noConstraint() throws Exception {
        Specification<Campaign> spec = CampaignSpecifications.voiceConfigured(null);
        spec.toPredicate(root, query, cb);

        verify(cb).conjunction();
    }
}
