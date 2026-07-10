package io.github.devoracode.upsert.test;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
@MapperScan("io.github.devoracode.upsert.test")
public class TestApplication {

    /*
     * Counting MetaObjectHandler used by the auto-fill tests. It fills
     * createTime on insert and updateTime on both insert and update, and
     * records how many times each fill method is invoked so the tests can
     * assert on the fill behavior.
     */
    @Bean
    @Primary
    public CountingMetaObjectHandler countingMetaObjectHandler() {
        return new CountingMetaObjectHandler();
    }

    public static class CountingMetaObjectHandler implements MetaObjectHandler {

        private final AtomicInteger insertFillCount = new AtomicInteger(0);
        private final AtomicInteger updateFillCount = new AtomicInteger(0);

        @Override
        public void insertFill(MetaObject metaObject) {
            insertFillCount.incrementAndGet();
            LocalDateTime now = LocalDateTime.now();
            strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
            strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        }

        @Override
        public void updateFill(MetaObject metaObject) {
            updateFillCount.incrementAndGet();
            strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        }

        public int getInsertFillCount() {
            return insertFillCount.get();
        }

        public int getUpdateFillCount() {
            return updateFillCount.get();
        }

        public void reset() {
            insertFillCount.set(0);
            updateFillCount.set(0);
        }
    }
}
