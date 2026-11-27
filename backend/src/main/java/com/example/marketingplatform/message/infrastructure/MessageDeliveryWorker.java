package com.example.marketingplatform.message.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MessageDeliveryWorker {
    private static final Logger log= LoggerFactory.getLogger(MessageDeliveryWorker.class);
    private final DeliveryWorkerTransactions transactions;
    private final AtomicBoolean running=new AtomicBoolean(false);
    public MessageDeliveryWorker(DeliveryWorkerTransactions transactions){this.transactions=transactions;}
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        try {
            transactions.recoverInterruptedJobs();
            log.info("Message delivery startup recovery completed.");
        } catch (RuntimeException ex) {
            log.error("Message delivery startup recovery failed. The application must not continue serving traffic " +
                    "with potentially stranded PROCESSING jobs. Verify PostgreSQL availability and permissions, " +
                    "then restart the single backend instance.", ex);
            throw new IllegalStateException("Message delivery startup recovery failed; refusing to continue startup.", ex);
        }
    }
    @Scheduled(fixedDelay=500)
    public void tick(){
        if(!running.compareAndSet(false,true)) return;
        try {
            UUID job=transactions.claimNextJob();
            if(job!=null) {
                try { transactions.processBatch(job); }
                catch(Exception ex) { log.warn("Campaign message delivery batch failed; retry state will be recorded.",ex); transactions.recordFailure(job); }
            }
        } catch(Exception ex){log.error("Message delivery worker iteration failed.",ex);}
        finally {running.set(false);}
    }
}
