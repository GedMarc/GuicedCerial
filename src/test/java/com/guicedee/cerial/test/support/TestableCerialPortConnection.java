package com.guicedee.cerial.test.support;

import com.guicedee.cerial.CerialPortConnection;
import com.guicedee.cerial.enumerations.BaudRate;

public class TestableCerialPortConnection extends CerialPortConnection<TestableCerialPortConnection> {
    public TestableCerialPortConnection(int comPort, BaudRate baudRate, int seconds) {
        super(comPort, baudRate, seconds);
    }

    public TestableCerialPortConnection(int comPort, BaudRate baudRate) {
        super(comPort, baudRate);
    }

    public void triggerScheduleReconnect(String reason) {
        // expose protected scheduleReconnect for tests
        this.scheduleReconnect(reason);
    }
}
