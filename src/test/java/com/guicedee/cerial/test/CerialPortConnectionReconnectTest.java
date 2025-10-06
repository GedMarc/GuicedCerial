package com.guicedee.cerial.test;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.guicedee.cerial.CerialPortConnection;
import com.guicedee.cerial.SerialPortException;
import com.guicedee.cerial.enumerations.BaudRate;
import com.guicedee.cerial.enumerations.ComPortStatus;
import com.guicedee.cerial.implementations.DataSerialPortBytesListener;
import com.guicedee.cerial.implementations.DataSerialPortMessageListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static com.fazecast.jSerialComm.SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CerialPortConnectionReconnectTest {

    @Mock
    SerialPort mockPort;

    @AfterEach
    void cleanupLogs() {
        // no-op; placeholder if any file log cleanup is required later
    }

    @Test
    @DisplayName("onConnectError: disconnects and schedules a reconnect attempt (no frenzy)")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testOnErrorDisconnectsAndSchedulesReconnect() throws Exception {
        // Given a connection with a mocked underlying port
        com.guicedee.cerial.test.support.TestableCerialPortConnection connection = new com.guicedee.cerial.test.support.TestableCerialPortConnection(99, BaudRate.$9600, 1);
        when(mockPort.isOpen()).thenReturn(false);
        when(mockPort.openPort()).thenReturn(true);

        // Inject mocked port
        connection.setConnectionPort(mockPort);

        // When: A reconnect is scheduled
        connection.triggerScheduleReconnect("unit-test");

        // And: Exactly one reconnect attempt is made within ~1s (avoid frenzy on multiple schedules)
        // Fire a second schedule immediately; reconnect scheduler should still keep only one timer
        connection.triggerScheduleReconnect("unit-test-duplicate");

        // Expect the first reconnect attempt to call openPort once within 2 seconds
        verify(mockPort, timeout(2000).times(1)).openPort();

        // Ensure it did not call openPort more than once in that first window
        verify(mockPort, atMost(1)).openPort();
    }

    @Test
    @DisplayName("Listeners: PORT_DISCONNECTED is treated as error and triggers onConnectError(Offline)")
    void testListenersTriggerErrorOnDisconnect() {
        // Given
        @SuppressWarnings("unchecked")
        CerialPortConnection<?> connection = mock(CerialPortConnection.class);
        SerialPort sp = mock(SerialPort.class);
        DataSerialPortMessageListener msgListener = new DataSerialPortMessageListener(new char[]{'\n'}, sp, connection);
        DataSerialPortBytesListener bytesListener = new DataSerialPortBytesListener(new char[]{'\n'}, sp, connection);

        SerialPortEvent event = mock(SerialPortEvent.class);
        when(event.getEventType()).thenReturn(LISTENING_EVENT_PORT_DISCONNECTED);

        // When
        msgListener.serialEvent(event);
        bytesListener.serialEvent(event);

        // Then
        verify(connection, atLeastOnce()).onConnectError(any(SerialPortException.class), eq(ComPortStatus.Offline));
    }
}
