package com.guicedee.cerial;

import com.guicedee.cerial.enumerations.ComPortStatus;
import com.guicedee.client.IGuiceContext;
import com.guicedee.guicedinjection.interfaces.IJobService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.time.LocalDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.guicedee.cerial.enumerations.ComPortStatus.*;

@Getter
@Setter
@Log
public class CerialIdleMonitor implements Runnable
{
    private final CerialPortConnection connection;
    private int initialDelay;
    private int period;

    private int seconds;

    private ComPortStatus previousStatus;

    ScheduledExecutorService scheduledExecutorService;

    public CerialIdleMonitor(CerialPortConnection connection)
    {
        this.connection = connection;
        previousStatus = connection.getComPortStatus();
        initialDelay = 2;
        period = 1;
        //10 minutes
        seconds = (int) TimeUnit.SECONDS.toSeconds(60 * 10);
    }

    public CerialIdleMonitor(CerialPortConnection connection, int initialDelay, int period, int seconds)
    {
        this(connection);
        this.initialDelay = initialDelay;
        this.period = period;
        seconds = (int) TimeUnit.SECONDS.toSeconds(seconds);
    }

    public void begin()
    {
        IJobService jobService = IGuiceContext.get(IJobService.class);
        String idleMonitorName = getIdleMonitorName();
        scheduledExecutorService =
                jobService.addPollingJob(idleMonitorName, this, initialDelay, period, TimeUnit.SECONDS);
        Runtime.getRuntime()
               .addShutdownHook(new Thread(() -> scheduledExecutorService = jobService.removePollingJob(idleMonitorName)));
    }

    private String getIdleMonitorName()
    {
        return "Idle Monitor " + getConnection().getComPortName();
    }

    public void end()
    {
        IGuiceContext.get(IJobService.class)
                     .removePollingJob(getIdleMonitorName());
    }

    @Override
    public void run()
    {
        if(connection.getComPortStatus() == Idle)
        {
            return;
        }
        if (connection.getLastMessageTime() == null)
        {
            if(!onlineServerStatus.contains(connection.getComPortStatus()))
            {
                connection.setComPortStatus(Offline);
            }
        }
        else if (connection.getLastMessageTime()
                      .isBefore(LocalDateTime.now()
                                             .minusSeconds(seconds)) &&
                (connection.getComPortStatus() != Silent && ComPortStatus.onlineServerStatus.contains(connection.getComPortStatus())))
        {
            connection.setComPortStatus(ComPortStatus.Idle);
        }
    }

}
