package com.ke.bella.batch.db.repo;

import com.ke.bella.batch.tables.records.InstanceRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

import static com.ke.bella.batch.tables.Instance.INSTANCE;
import static org.springframework.transaction.annotation.Isolation.SERIALIZABLE;

@Component
public class InstanceRepo {
    @Resource
    private DSLContext db;

    @Transactional(isolation = SERIALIZABLE)
    public Long register(String ip, int port) {
        InstanceRecord rec = db.selectFrom(INSTANCE)
                .where(INSTANCE.IP.eq(ip).and(INSTANCE.PORT.eq(port))).fetchOne();
        if(rec == null) {
            rec = findIdle();
            rec.set(INSTANCE.IP, ip);
            rec.set(INSTANCE.PORT, port);
        }
        rec.setStatus(1);
        rec.set(INSTANCE.MTIME, LocalDateTime.now());
        rec.store();
        return rec.getId();
    }

    public void unregister(String ip, int port) {
        InstanceRecord rec = db.selectFrom(INSTANCE)
                .where(INSTANCE.IP.eq(ip).and(INSTANCE.PORT.eq(port))).fetchOne();
        if(rec != null) {
            rec.setStatus(0);
            rec.set(INSTANCE.MTIME, LocalDateTime.now());
            rec.store();
        }
    }

    private InstanceRecord findIdle() {
        InstanceRecord rec = db.selectFrom(INSTANCE)
                .where(INSTANCE.STATUS.eq(0)).limit(1).fetchAny();
        if(rec == null) {
            rec = INSTANCE.newRecord();
            rec.set(INSTANCE.CTIME, LocalDateTime.now());
            rec.attach(db.configuration());
        }
        return rec;
    }

}
