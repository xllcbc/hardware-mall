package com.example.mystore.event;

import com.example.mystore.service.PricingRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PricingCacheInvalidationListenerTransactionTest {

    @Test
    void eventRunsSynchronouslyAfterCommitAndNeverDuringTransaction() {
        PricingRuleService pricingRuleService = mock(PricingRuleService.class);
        TrackingTransactionManager transactionManager = new TrackingTransactionManager();
        doAnswer(invocation -> {
            assertThat(transactionManager.commitCount).isEqualTo(1);
            return null;
        }).when(pricingRuleService).evictLevelCache(2L);

        try (AnnotationConfigApplicationContext context = context(pricingRuleService, transactionManager)) {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            ApplicationEventPublisher publisher = context;

            transaction.executeWithoutResult(status -> {
                publisher.publishEvent(new PricingCacheInvalidationEvent(
                        PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 2L));
                verifyNoInteractions(pricingRuleService);
            });

            verify(pricingRuleService).evictLevelCache(2L);
        }
    }

    @Test
    void eventDoesNotRunWhenTransactionRollsBack() {
        PricingRuleService pricingRuleService = mock(PricingRuleService.class);
        TrackingTransactionManager transactionManager = new TrackingTransactionManager();

        try (AnnotationConfigApplicationContext context = context(pricingRuleService, transactionManager)) {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            ApplicationEventPublisher publisher = context;

            transaction.executeWithoutResult(status -> {
                publisher.publishEvent(new PricingCacheInvalidationEvent(
                        PricingCacheInvalidationEvent.Type.LEVEL_AVAILABILITY_CHANGED, 2L));
                status.setRollbackOnly();
            });

            assertThat(transactionManager.rollbackCount).isEqualTo(1);
            verifyNoInteractions(pricingRuleService);
        }
    }

    private AnnotationConfigApplicationContext context(PricingRuleService pricingRuleService,
                                                       TrackingTransactionManager transactionManager) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(PricingCacheInvalidationListener.class,
                () -> new PricingCacheInvalidationListener(pricingRuleService));
        context.registerBean(TransactionalEventListenerFactory.class, TransactionalEventListenerFactory::new);
        context.registerBean(TrackingTransactionManager.class, () -> transactionManager);
        context.refresh();
        return context;
    }

    private static final class TrackingTransactionManager extends AbstractPlatformTransactionManager {

        private int commitCount;
        private int rollbackCount;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbackCount++;
        }
    }
}
