package com.ke.bella.batch.db.repo;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.jooq.DSLContext;
import org.jooq.conf.MappedSchema;
import org.jooq.conf.MappedTable;
import org.jooq.conf.RenderMapping;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.tools.StringUtils;

import java.util.regex.Pattern;

import static com.ke.bella.batch.Tables.QUEUE;

public class DSLContextHolder {
    private static final Cache<String, DSLContext> configurations = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .build();

    public static synchronized DSLContext get(String key, final DSLContext db) {
        if (StringUtils.isEmpty(key)) {
            return db;
        }

        DSLContext ret = configurations.getIfPresent(key);
        if (ret == null) {
            ret = DSL.using(db.configuration().derive(newSettings(key)));
            configurations.put(key, ret);
        }

        return ret;
    }

    public static Settings newSettings(String key) {
        return new Settings().withRenderMapping(new RenderMapping()
                .withSchemata( // 为对象设置表的映射
                        new MappedSchema()
                                .withInputExpression(Pattern.compile(".*"))
                                .withTables(new MappedTable()
                                        .withInput(QUEUE.getName())
                                        .withOutput(targetTableName(QUEUE.getName(), key)))));
    }

    public static String targetTableName(String orignalName, String key) {
        if (StringUtils.isEmpty(key)) {
            return orignalName;
        }
        return String.format("%s_%s", orignalName, key); // todo 修改为多段
    }
}
