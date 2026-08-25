package io.github.devoracode.upsert.test;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import io.github.devoracode.upsert.core.fill.UpsertFillProcessor;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
@MapperScan("io.github.devoracode.upsert.test")
public class TestApplication {

    /*
     * 用于填充测试的计数 MetaObjectHandler。它在插入时填充 createTime，
     * 在插入和更新时都填充 updateTime，并记录每个填充方法的调用次数
     * 及 insertFill 的调用来源（预绑定处理器 / MP 原生参数处理器），
     * 以便测试断言填充行为。
     */
    @Bean
    @Primary
    public CountingMetaObjectHandler countingMetaObjectHandler() {
        return new CountingMetaObjectHandler();
    }

    public static class CountingMetaObjectHandler implements MetaObjectHandler {

        private final AtomicInteger insertFillCount = new AtomicInteger(0);
        private final AtomicInteger updateFillCount = new AtomicInteger(0);

        /*
         * 每次 insertFill 调用的来源记录：
         * "pre-bind" —— 本库 UpsertFillProcessor（SQL 绑定前）
         * "native"   —— MP 原生 MybatisParameterHandler（SQL 绑定后）
         */
        private final List<String> insertFillSources =
                Collections.synchronizedList(new ArrayList<>());

        @Override
        public void insertFill(MetaObject metaObject) {
            insertFillCount.incrementAndGet();
            insertFillSources.add(resolveFillSource());
            LocalDateTime now = LocalDateTime.now();
            strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
            strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        }

        @Override
        public void updateFill(MetaObject metaObject) {
            updateFillCount.incrementAndGet();
            strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        }

        /*
         * 通过当前线程堆栈判断本次填充调用的触发方。
         * 两条调用链路的调用方帧不同，据此区分来源。
         */
        private static String resolveFillSource() {
            for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                String className = frame.getClassName();
                if (UpsertFillProcessor.class.getName().equals(className)) {
                    return "pre-bind";
                }
                if ("com.baomidou.mybatisplus.core.MybatisParameterHandler".equals(className)) {
                    return "native";
                }
            }
            return "unknown";
        }

        public int getInsertFillCount() {
            return insertFillCount.get();
        }

        public int getUpdateFillCount() {
            return updateFillCount.get();
        }

        public List<String> getInsertFillSources() {
            synchronized (insertFillSources) {
                return new ArrayList<>(insertFillSources);
            }
        }

        public void reset() {
            insertFillCount.set(0);
            updateFillCount.set(0);
            insertFillSources.clear();
        }
    }
}
