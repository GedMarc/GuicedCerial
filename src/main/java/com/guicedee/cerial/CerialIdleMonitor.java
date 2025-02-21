package com.guicedee.cerial;

import com.guicedee.cerial.enumerations.ComPortStatus;
import com.guicedee.client.IGuiceContext;
import io.vertx.core.Vertx;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.guicedee.cerial.enumerations.ComPortStatus.Silent;
import static com.guicedee.cerial.enumerations.ComPortStatus.exceptionOperations;

@Getter
@Setter
@Log
public class CerialIdleMonitor
{
    private final CerialPortConnection connection;
    private int initialDelay;
    private int period;

    private int seconds;

    private ComPortStatus previousStatus;

    private long timerId;

    public CerialIdleMonitor(CerialPortConnection connection)
    {
        this.connection = connection;
        previousStatus = connection.getComPortStatus();
        initialDelay = 2;
        period = 10;
        //10 minutes
        seconds = (int) TimeUnit.SECONDS.toSeconds(120);
    }

    public CerialIdleMonitor(CerialPortConnection connection, int initialDelay, int period, int seconds)
    {
        this(connection);
        this.initialDelay = initialDelay;
        this.period = period;
        this.seconds = (int) TimeUnit.SECONDS.toSeconds(seconds);
    }

    public void begin()
    {
        var vertx = IGuiceContext.get(Vertx.class);
        timerId = vertx.setPeriodic(TimeUnit.SECONDS.toMillis(period), (handler) -> {
            if (!exceptionOperations.contains(connection.getComPortStatus()) &&
                    (connection.getComPortStatus() != Silent &&
                            ComPortStatus.onlineServerStatus.contains(connection.getComPortStatus())) &&
                    (connection.getLastMessageTime() == null || connection.getLastMessageTime()
                            .isBefore(LocalDateTime.now()
                                    .minusSeconds(seconds))

                    ))
            {
                connection.setComPortStatus(ComPortStatus.Idle);
            }
        });
    }

    private String getIdleMonitorName()
    {
        return "Idle Monitor " + getConnection().getComPortName();
    }

    public void end()
    {
        var vertx = IGuiceContext.get(Vertx.class);
        vertx.cancelTimer(timerId);
    }

}
