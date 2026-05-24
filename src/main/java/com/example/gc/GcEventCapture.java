package com.example.gc;

import com.sun.management.GarbageCollectionNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * GC通知リスナー。
 * System.gc() を呼び出し、GC完了通知が届くまで待機する。
 * CountDownLatch により、最初の System.gc() 起因の GC イベントを捕捉する。
 */
public class GcEventCapture implements NotificationListener, AutoCloseable {

    // System.gc() によるGCの cause 文字列
    private static final String SYSTEM_GC_CAUSE = "System.gc()";

    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicReference<GarbageCollectionNotificationInfo> captured = new AtomicReference<>();
    private final List<NotificationEmitter> registeredEmitters = new ArrayList<>();

    public GcEventCapture() {
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener(this, null, null);
                registeredEmitters.add(emitter);
            }
        }
    }

    @Override
    public void handleNotification(Notification notif, Object handback) {
        if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notif.getType())) {
            return;
        }
        GarbageCollectionNotificationInfo info =
            GarbageCollectionNotificationInfo.from((CompositeData) notif.getUserData());

        // System.gc() 起因のイベントのみ捕捉
        if (SYSTEM_GC_CAUSE.equals(info.getGcCause())) {
            captured.set(info);
            latch.countDown();
        }
    }

    /**
     * System.gc() を呼び出し、GC完了通知が届くまで最大 timeoutMs ミリ秒待機する。
     *
     * @param timeoutMs 最大待機時間 (ミリ秒)
     * @return GC通知情報。タイムアウトした場合は null
     */
    public GarbageCollectionNotificationInfo triggerAndWait(long timeoutMs) throws InterruptedException {
        System.gc();
        latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        return captured.get();
    }

    @Override
    public void close() {
        for (NotificationEmitter emitter : registeredEmitters) {
            try {
                emitter.removeNotificationListener(this);
            } catch (Exception ignored) {
            }
        }
    }
}
