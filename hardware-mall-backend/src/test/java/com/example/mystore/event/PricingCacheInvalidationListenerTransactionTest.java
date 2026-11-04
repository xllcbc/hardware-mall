package com.example.mystore.event;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PricingCacheInvalidationListenerTransactionTest {

    @Test
    void eventRunsSynchronouslyAfterCommitAndNeverDuringTransaction() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        TrackingTransactionManager transactionManager = new TrackingTransactionManager();
        doAnswer(invocation -> {
            assertThat(transactionManager.commitCount).isEqualTo(1);
            return true;
        }).when(redisUtil).evictWithLock(eq(RedisConstants.PREFIX_PRICING_LEVEL + 2L), anyLong());

        try (AnnotationConfigApplicationContext context = context(redisUtil, transactionManager)) {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            ApplicationEventPublisher publisher = context;

            transaction.executeWithoutResult(status -> {
                publisher.publishEvent(new PricingCacheInvalidationEvent(
                        PricingCacheInvalidationEvent.Type.MATRIX_CHANGED, 2L));
                verifyNoInteractions(redisUtil);
            });

            verify(redisUtil).evictWithLock(eq(RedisConstants.PREFIX_PRICING_LEVEL + 2L), anyLong());
        }
    }

    @Test
    void eventDoesNotRunWhenTransactionRollsBack() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        TrackingTransactionManager transactionManager = new TrackingTransactionManager();

        try (AnnotationConfigApplicationContext context = context(redisUtil, transactionManager)) {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            ApplicationEventPublisher publisher = context;

            transaction.executeWithoutResult(status -> {
                publisher.publishEvent(new PricingCacheInvalidationEvent(
                        PricingCacheInvalidationEvent.Type.LEVEL_AVAILABILITY_CHANGED, 2L));
                status.setRollbackOnly();
            });

            assertThat(transactionManager.rollbackCount).isEqualTo(1);
            verifyNoInteractions(redisUtil);
        }
    }

    private AnnotationConfigApplicationContext context(RedisUtil redisUtil,
                                                       TrackingTransactionManager transactionManager) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(PricingCacheInvalidationListener.class,
                () -> new PricingCacheInvalidationListener(redisUtil));
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
