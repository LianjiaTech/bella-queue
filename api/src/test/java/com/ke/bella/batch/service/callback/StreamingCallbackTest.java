package com.ke.bella.batch.service.callback;

import com.ke.bella.batch.service.QueueService;
import com.ke.bella.batch.utils.SseUtils;
import com.ke.bella.queue.TaskEvent;
import com.theokanning.openai.queue.Put;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class StreamingCallbackTest {

    @Mock
    private QueueService queueService;

    @Mock
    private SseEmitter sseEmitter;

    @Mock
    private TaskEvent.Progress.Payload progressEvent;

    @Mock
    private TaskEvent.Completion.Payload completionEvent;

    private StreamingCallback streamingCallback;
    private static final String TASK_ID = "TASK-123-0-S-240304120000-0001-000001";
    private static final String QUEUE = "test-queue";
    private static final int TIMEOUT = 300;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private Put mockPut(int timeout) {
        Put put = mock(Put.class);
        when(put.getQueue()).thenReturn(QUEUE);
        when(put.getTimeout()).thenReturn(timeout);
        return put;
    }

    @Before
    public void setUp() {
        try (MockedStatic<SseUtils> sseUtilsMock = mockStatic(SseUtils.class)) {
            sseUtilsMock.when(() -> SseUtils.createSse(anyLong(), anyString())).thenReturn(sseEmitter);
            streamingCallback = new StreamingCallback(TASK_ID, mockPut(TIMEOUT), queueService, meterRegistry);
        }
    }

    @Test
    public void testOnProgressEvent_WithNormalString() {
        String eventData = "normal content";
        when(progressEvent.getEventData()).thenReturn(eventData);
        when(progressEvent.getEventId()).thenReturn("event-1");
        when(progressEvent.getEventName()).thenReturn("progress");

        try (MockedStatic<SseUtils> sseUtilsMock = mockStatic(SseUtils.class)) {
            streamingCallback.onProgressEvent(progressEvent);

            sseUtilsMock.verify(() -> SseUtils.send(eq(sseEmitter), any(SseEmitter.SseEventBuilder.class)), times(1));
            verify(progressEvent).getEventData();
            verify(progressEvent).getEventId();
            verify(progressEvent).getEventName();
        }

        Timer ttft = meterRegistry.find("queue.task.ttft").tag("queue", QUEUE).timer();
        assertNotNull(ttft);
        assertEquals(1, ttft.count());
    }

    @Test
    public void testOnProgressEvent_WithDataPrefix() {
        String eventData = "data:actual content";
        when(progressEvent.getEventData()).thenReturn(eventData);
        when(progressEvent.getEventId()).thenReturn("event-2");
        when(progressEvent.getEventName()).thenReturn("progress");

        try (MockedStatic<SseUtils> sseUtilsMock = mockStatic(SseUtils.class)) {
            streamingCallback.onProgressEvent(progressEvent);

            sseUtilsMock.verify(() -> SseUtils.send(eq(sseEmitter), any(SseEmitter.SseEventBuilder.class)), times(1));
            verify(progressEvent).getEventData();
        }
    }

    @Test
    public void testOnProgressEvent_WithDataPrefixOnly() {
        String eventData = "data:";
        when(progressEvent.getEventData()).thenReturn(eventData);
        when(progressEvent.getEventId()).thenReturn("event-3");
        when(progressEvent.getEventName()).thenReturn("progress");

        try (MockedStatic<SseUtils> sseUtilsMock = mockStatic(SseUtils.class)) {
            streamingCallback.onProgressEvent(progressEvent);

            sseUtilsMock.verify(() -> SseUtils.send(eq(sseEmitter), any(SseEmitter.SseEventBuilder.class)), times(1));
        }
    }

    @Test
    public void testOnProgressEvent_WithDataPrefixAndWhitespace() {
        String eventData = "data:   content with spaces   ";
        when(progressEvent.getEventData()).thenReturn(eventData);
        when(progressEvent.getEventId()).thenReturn("event-4");
        when(progressEvent.getEventName()).thenReturn("progress");

        try (MockedStatic<SseUtils> sseUtilsMock = mockStatic(SseUtils.class)) {
            streamingCallback.onProgressEvent(progressEvent);

            sseUtilsMock.verify(() -> SseUtils.send(eq(sseEmitter), any(SseEmitter.SseEventBuilder.class)), times(1));
        }
    }

    @Test
    public void testOnProgressEvent_WithNullEventData() {
        when(progressEvent.getEventData()).thenReturn(null);
        when(progressEvent.getEventId()).thenReturn("event-5");
        when(progressEvent.getEventName()).thenReturn("progress");

        try (MockedStatic<SseUtils> sseUtilsMock = mockStatic(SseUtils.class)) {
            streamingCallback.onProgressEvent(progressEvent);

            sseUtilsMock.verify(() -> SseUtils.send(eq(sseEmitter), any(SseEmitter.SseEventBuilder.class)), times(1));
        }
    }

    @Test
    public void testOnProgressEvent_WithNonStringData() {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("key", "value");
        when(progressEvent.getEventData()).thenReturn(eventData);
        when(progressEvent.getEventId()).thenReturn("event-6");
        when(progressEvent.getEventName()).thenReturn("progress");

        try (MockedStatic<SseUtils> sseUtilsMock = mockStatic(SseUtils.class)) {
            streamingCallback.onProgressEvent(progressEvent);

            sseUtilsMock.verify(() -> SseUtils.send(eq(sseEmitter), any(SseEmitter.SseEventBuilder.class)), times(1));
        }
    }

    @Test
    public void testOnProgressEvent_WhenAlreadyCompleted() {
        when(progressEvent.getEventId()).thenReturn("event-7");

        streamingCallback.onCompletionEvent(completionEvent);

        try (MockedStatic<SseUtils> sseUtilsMock = mockStatic(SseUtils.class)) {
            streamingCallback.onProgressEvent(progressEvent);

            sseUtilsMock.verify(() -> SseUtils.send(any(), any()), never());
            verify(progressEvent).getEventId();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testOnProgressEvent_WithNullEvent() {
        streamingCallback.onProgressEvent(null);
    }

    @Test
    public void testOnCompletionEvent_Success() {
        streamingCallback.onCompletionEvent(completionEvent);

        verify(sseEmitter).complete();

        Timer ttlt = meterRegistry.find("queue.task.ttlt").tag("queue", QUEUE).timer();
        assertNotNull(ttlt);
        assertEquals(1, ttlt.count());
    }

    @Test
    public void testOnCompletionEvent_CalledTwice_ShouldOnlyCompleteOnce() {
        streamingCallback.onCompletionEvent(completionEvent);
        streamingCallback.onCompletionEvent(completionEvent);

        verify(sseEmitter, times(1)).complete();
    }

    @Test
    public void testOnCompletionEvent_WhenEmitterThrowsException() {
        doThrow(new RuntimeException("Emitter error")).when(sseEmitter).complete();

        streamingCallback.onCompletionEvent(completionEvent);

        verify(sseEmitter).complete();
    }

    @Test
    public void testOnTimeout() {
        streamingCallback.onTimeout(TASK_ID);

        verify(sseEmitter).completeWithError(any(RuntimeException.class));
        verify(queueService).cancel(TASK_ID);

        Timer ttlt = meterRegistry.find("queue.task.ttlt").tag("queue", QUEUE).timer();
        assertNotNull(ttlt);
        assertEquals(1, ttlt.count());
    }

    @Test
    public void testGetTimeout() {
        long timeout = streamingCallback.getTimeout();

        assertEquals(TIMEOUT, timeout);
    }

    @Test
    public void testConstructor_WithZeroTimeout_ShouldUseDefault() {
        try (MockedStatic<SseUtils> sseUtilsMock = mockStatic(SseUtils.class)) {
            sseUtilsMock.when(() -> SseUtils.createSse(anyLong(), anyString())).thenReturn(sseEmitter);
            StreamingCallback callback = new StreamingCallback(TASK_ID, mockPut(0), queueService, meterRegistry);

            assertEquals(600, callback.getTimeout());
        }
    }

    @Test
    public void testConstructor_WithNegativeTimeout_ShouldUseDefault() {
        try (MockedStatic<SseUtils> sseUtilsMock = mockStatic(SseUtils.class)) {
            sseUtilsMock.when(() -> SseUtils.createSse(anyLong(), anyString())).thenReturn(sseEmitter);
            StreamingCallback callback = new StreamingCallback(TASK_ID, mockPut(-10), queueService, meterRegistry);

            assertEquals(600, callback.getTimeout());
        }
    }

    @Test
    public void testConstructor_WithExcessiveTimeout_ShouldCapAtMax() {
        try (MockedStatic<SseUtils> sseUtilsMock = mockStatic(SseUtils.class)) {
            sseUtilsMock.when(() -> SseUtils.createSse(anyLong(), anyString())).thenReturn(sseEmitter);
            StreamingCallback callback = new StreamingCallback(TASK_ID, mockPut(700), queueService, meterRegistry);

            assertEquals(600, callback.getTimeout());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEmitterOnError_WhenNotCompleted_ShouldCancelTask() {
        reset(sseEmitter, queueService);
        ArgumentCaptor<java.util.function.Consumer<Throwable>> errorHandlerCaptor =
                ArgumentCaptor.forClass(java.util.function.Consumer.class);

        try (MockedStatic<SseUtils> sseUtilsMock = mockStatic(SseUtils.class)) {
            sseUtilsMock.when(() -> SseUtils.createSse(anyLong(), anyString())).thenReturn(sseEmitter);

            new StreamingCallback(TASK_ID, mockPut(TIMEOUT), queueService, meterRegistry);

            verify(sseEmitter).onError(errorHandlerCaptor.capture());

            errorHandlerCaptor.getValue().accept(new RuntimeException("Connection error"));

            verify(queueService).cancel(TASK_ID);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEmitterOnError_WhenCompleted_ShouldNotCancelTask() {
        reset(sseEmitter, queueService);
        ArgumentCaptor<java.util.function.Consumer<Throwable>> errorHandlerCaptor =
                ArgumentCaptor.forClass(java.util.function.Consumer.class);

        try (MockedStatic<SseUtils> sseUtilsMock = mockStatic(SseUtils.class)) {
            sseUtilsMock.when(() -> SseUtils.createSse(anyLong(), anyString())).thenReturn(sseEmitter);

            StreamingCallback callback = new StreamingCallback(TASK_ID, mockPut(TIMEOUT), queueService, meterRegistry);

            verify(sseEmitter).onError(errorHandlerCaptor.capture());

            callback.onCompletionEvent(completionEvent);

            reset(queueService);

            errorHandlerCaptor.getValue().accept(new RuntimeException("Connection error"));

            verify(queueService, never()).cancel(TASK_ID);
        }
    }
}