package com.guicedee.cerial;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.primitives.Bytes;
import com.google.inject.Inject;
import com.guicedee.cerial.enumerations.*;
import com.guicedee.client.CallScoper;
import com.guicedee.client.IGuiceContext;
import com.guicedee.guicedinjection.interfaces.IGuicePreDestroy;
import com.guicedee.guicedservlets.websockets.options.CallScopeProperties;
import com.guicedee.guicedservlets.websockets.options.CallScopeSource;
import com.guicedee.services.jsonrepresentation.IJsonRepresentation;
import gnu.io.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import org.apache.commons.lang3.function.TriConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.TooManyListenersException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;
import static com.guicedee.cerial.enumerations.ComPortStatus.*;
import static lombok.AccessLevel.PRIVATE;

@SuppressWarnings({"UnusedReturnValue",
        "unchecked",
        "unused"})
@Getter
@ToString(of = {"comPort",
        "comPortStatus"})
@JsonIgnoreProperties(ignoreUnknown = true, value = {"inspection"})
@JsonAutoDetect(fieldVisibility = ANY, getterVisibility = NONE, setterVisibility = NONE)
@NoArgsConstructor
public class CerialPortConnection<J extends CerialPortConnection<J>> implements IJsonRepresentation<J>,
                                                                                IGuicePreDestroy<J>
{
    @JsonIgnore
    private final Object[] $lock = new Object[0];

    @JsonIgnore
    private NRSerialPort<?> serialPort;

    @JsonIgnore
    private final AtomicBoolean outputBufferEmpty = new AtomicBoolean(false);
    @JsonIgnore
    private final AtomicBoolean clearToSend = new AtomicBoolean(false);

    private Integer comPort;

    private BaudRate baudRate = BaudRate.$9600;
    private ComPortStatus comPortStatus = ComPortStatus.Offline;
    private ComPortType comPortType = ComPortType.Device;
    private DataBits dataBits = DataBits.$8;
    private FlowControl flowControl = FlowControl.None;
    private Parity parity = Parity.None;
    private StopBits stopBits = StopBits.$1;

    private Integer bufferSize = 4096;

    private Integer idleTimerSeconds = 5;

    @Getter(PRIVATE)
    @JsonIgnore
    private OutputStream writer = null;

    @JsonIgnore
    @Getter(PRIVATE)
    private BiConsumer<CerialPortConnection<?>, ComPortStatus> comPortStatusUpdate;
    @JsonIgnore
    @Getter(PRIVATE)
    private BiConsumer<byte[], CerialPortConnection<?>> comPortRead;
    @JsonIgnore
    @Getter(PRIVATE)
    private TriConsumer<Throwable, CerialPortConnection<?>, ComPortStatus> comPortError;
    @JsonIgnore
    private CerialIdleMonitor monitor;

    private LocalDateTime lastMessageTime;

    @Inject
    @JsonIgnore
    @Getter(PRIVATE)
    CallScoper callScoper;

    @JsonIgnore
    private Logger logger;

    public CerialPortConnection(int comPort, BaudRate baudRate, int seconds)
    {
        this.comPort = comPort;
        this.baudRate = baudRate;
        setDataBits(DataBits.$8);
        setParity(Parity.None);
        setStopBits(StopBits.$1);
        setFlowControl(FlowControl.None);
        setComPortStatus(ComPortStatus.Offline);
        setComPortType(ComPortType.Device);

        this.serialPort = new NRSerialPort<>(getComPortName(), baudRate.toInt());
        this.idleTimerSeconds = seconds;
        this.setMonitor(new CerialIdleMonitor(this, 2, 1, seconds));
    }

    public CerialPortConnection(int comPort, BaudRate baudRate)
    {
        this(comPort, baudRate, 60 * 10);
    }

    /**
     * @return Returns a serial port, or constructs a new one
     */
    public NRSerialPort<?> getSerialPort()
    {
        if (this.serialPort == null)
        {
            this.serialPort = new NRSerialPort<>(getComPortName(), baudRate.toInt());
        }
        return serialPort;
    }

    public CerialIdleMonitor getMonitor()
    {
        if (monitor == null)
        {
            this.setMonitor(new CerialIdleMonitor(this, 2, 1, idleTimerSeconds));
        }
        return monitor;
    }

    public J connect()
    {
        beforeConnect();
        try
        {
            serialPort.connect();
            configure(serialPort.getSerialPortInstance());
            afterConnect();
            registerShutdownHook();
            getLogger().info("Com Port Connected - {}", getComPortName());
        }
        catch (Throwable e)
        {
            getLogger().fatal("Error connecting to port", e);
            onConnectError(e, ComPortStatus.GeneralException);
        }
        return (J) this;
    }

    public J disconnect()
    {
        if (serialPort.isConnected())
        {
            serialPort.disconnect();
            setComPortStatus(Offline);
        }
        return (J) this;
    }

    public J beforeConnect()
    {
        if (callScoper == null)
        {
            IGuiceContext.instance()
                         .inject()
                         .injectMembers(this);
        }
        setComPortStatus(Opening);
        return (J) this;
    }

    public J afterConnect()
    {
        setComPortStatus(Silent);
        configureNotifications();
        //     configureForRTS();
        getMonitor().begin();
        return (J) this;
    }

    public J onConnectError(Throwable e, ComPortStatus status)
    {
        if (comPortError != null)
        {
            comPortError.accept(e, this, status);
        }
        setComPortStatus(status);
        disconnect();
        return (J) this;
    }

    public J onComPortStatusUpdate(BiConsumer<CerialPortConnection<?>, ComPortStatus> comPortStatusUpdate)
    {
        this.comPortStatusUpdate = comPortStatusUpdate;
        return (J) this;
    }

    public J setComPortStatus(ComPortStatus comPortStatus, boolean... update)
    {
        if (this.comPortStatus != comPortStatus && (
                (update != null && update.length == 0) ||
                        (update != null && update[0]))
        )
        {
            if (this.comPortStatusUpdate != null)
            {
                this.comPortStatusUpdate.accept(this, comPortStatus);
            }
        }
        this.comPortStatus = comPortStatus;
        return (J) this;
    }

    protected J registerShutdownHook()
    {
        IGuiceContext.getAllLoadedServices()
                     .getOrDefault(IGuicePreDestroy.class, new HashSet())
                     .add(this);
        return (J) this;
    }

    protected J afterShutdown()
    {
        return (J) this;
    }

    protected J beforeShutdown()
    {
        getMonitor().end();
        return (J) this;
    }

    protected J configure(RXTXPort instance) throws UnsupportedCommOperationException
    {
        setEndOfMessageChar((byte) '\n');

        if (flowControl != null)
        {
            instance.setFlowControlMode(flowControl.toInt());
        }
        if (parity != null)
        {
            instance.setSerialPortParams(baudRate.toInt(),
                                         dataBits.toInt(),
                                         stopBits.toInt(),
                                         parity.toInt());
        }
        if (bufferSize != null)
        {
            instance.setInputBufferSize(bufferSize);
            instance.setOutputBufferSize(bufferSize);
        }
        return (J) this;
    }

    private J me()
    {
        return (J) this;
    }

    /**
     * Must be connected first
     *
     * @return
     */
    public J configureForRTS()
    {
        getSerialPort().getSerialPortInstance()
                       .setRTS(true);
        getSerialPort().getSerialPortInstance()
                       .notifyOnCTS(true);
        getSerialPort().getSerialPortInstance()
                       .notifyOnOutputEmpty(true);
        getSerialPort().getSerialPortInstance()
                       .setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN |
                                                   SerialPort.FLOWCONTROL_RTSCTS_OUT);
        getSerialPort().getSerialPortInstance()
                       .setDTR(true);

        return (J) this;
    }

    /**
     * Must be connected first
     *
     * @return
     */
    public J configureForXOnXOff()
    {
    /*    getSerialPort().getSerialPortInstance()
                       .notifyOnCTS(true);
        getSerialPort().getSerialPortInstance()
                       .notifyOnOutputEmpty(true);*/
        getSerialPort().getSerialPortInstance()
                       .setFlowControlMode(SerialPort.FLOWCONTROL_XONXOFF_IN |
                                                   SerialPort.FLOWCONTROL_XONXOFF_OUT);
        return (J) this;
    }

    public J configureNotifications()
    {
        getSerialPort().getSerialPortInstance()
                       .notifyOnBreakInterrupt(true);
        getSerialPort().getSerialPortInstance()
                       .notifyOnOverrunError(true);
        getSerialPort().getSerialPortInstance()
                       .notifyOnParityError(true);
        getSerialPort().getSerialPortInstance()
                       .notifyOnFramingError(true);
        getSerialPort().getSerialPortInstance()
                       .notifyOnRingIndicator(true);
        getSerialPort().getSerialPortInstance()
                       .notifyOnDataAvailable(true);
        getSerialPort().notifyOnDataAvailable(true);
        final DataInputStream inputStream = new DataInputStream(getSerialPort().getInputStream());
        writer = new DataOutputStream(getSerialPort().getOutputStream());
        try
        {
            getSerialPort().addEventListener(event -> {
                if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE && event.getNewValue() && !event.getOldValue())
                {
                    if (getComPortStatus() == Silent)
                    {
                        setComPortStatus(Running);
                    }
                    if (comPortRead != null)
                    {
                        try
                        {
                            byte[] bytes = new byte[0];
                            if (inputStream.available() > 0)
                            {
                                synchronized ($lock)
                                {
                                    while (!Bytes.contains(bytes, serialPort.getSerialPortInstance()
                                                                            .getEndOfInputChar()))
                                    {
                                        if (bytes.length == 0)
                                        {
                                            bytes = new byte[inputStream.available()];
                                            inputStream.readFully(bytes);
                                        }
                                        else
                                        {
                                            byte[] extendedBytes = new byte[inputStream.available()];
                                            inputStream.readFully(extendedBytes);
                                            bytes = Bytes.concat(bytes, extendedBytes);
                                        }
                                        if (bytes.length > 0 &&
                                                !Strings.isNullOrEmpty(new String(bytes)) &&
                                                Bytes.contains(bytes, serialPort.getSerialPortInstance()
                                                                                .getEndOfInputChar()))
                                        {
                                            checkAndProcessBytes(bytes);

                                        }
                                        Thread.sleep(50);
                                    }
                                }
                            }
                        }
                        catch (Throwable e)
                        {
                            onConnectError(new SerialPortException("Exception in event listener thread [" + getComPort() + "]", e), GeneralException);
                            throw new SerialPortException("Throwable took place during byte reading", e);
                        }
                    }
                }
                else if (event.getEventType() == SerialPortEvent.CTS)
                {
                    //ready to start sending
                    clearToSend.set(event.getNewValue());
                    //    setComPortStatus(OperationInProgress);
                }
                else if (event.getEventType() == SerialPortEvent.OUTPUT_BUFFER_EMPTY)
                {
                    outputBufferEmpty.set(event.getNewValue());
                    //      setComPortStatus(Silent);
                }
                else if (event.getEventType() == SerialPortEvent.HARDWARE_ERROR)
                {
                    getLogger().error("COM" + comPort + " Hardware Error- {}", event.getNewValue());
                    onConnectError(new SerialPortException("Hardware Error on Port [" + getComPort() + "]"), GeneralException);
                }
                else if (event.getEventType() == SerialPortEvent.FE)
                {
                    getLogger().error("COM" + comPort + " Framing Error- {}", event.getNewValue());
                    onConnectError(new SerialPortException("Framing Error on Port [" + getComPort() + "]"), GeneralException);
                }
                else if (event.getEventType() == SerialPortEvent.PE)
                {
                    getLogger().error("COM" + comPort + " Parity Error- {}", event.getNewValue());
                    onConnectError(new SerialPortException("Parity Error on Port [" + getComPort() + "]"), GeneralException);
                }
                else if (event.getEventType() == SerialPortEvent.OE)
                {
                    getLogger().error("COM" + comPort + " Overrun Error- {}", event.getNewValue());
                    onConnectError(new SerialPortException("Overrun Error on Port [" + getComPort() + "]"), GeneralException);
                }
                else if (event.getEventType() == SerialPortEvent.BI)
                {
                    getLogger().error("COM" + comPort + " Break-Interrupt Error- {}", event.getNewValue());
                    onConnectError(new SerialPortException("Break-Interrupt Error on Port [" + getComPort() + "]"), GeneralException);
                }
                else
                {
                    getLogger().error("COM" + comPort + " Unknown Error- {}", event.getNewValue());
                    onConnectError(new SerialPortException("Event failure type not catered for [" + getComPort() + "]/[" + event.getEventType() + "]"), GeneralException);
                }
            });
        }
        catch (TooManyListenersException e)
        {
            getLogger().warn("A listener is already attached to the com port {}", getComPortName(), e);
            onConnectError(new SerialPortException("Listeners already registered on Port [" + getComPort() + "]"), Missing);
        }
        return (J) this;
    }

    private Logger getLogger()
    {
        if (logger == null)
        {
            return logger = LogManager.getLogger("COM" + comPort);
        }
        return logger;
    }

    private byte[] checkAndProcessBytes(byte[] bytes) throws UnsupportedCommOperationException
    {
        int messageEnd = Bytes.indexOf(bytes, serialPort.getSerialPortInstance()
                                                        .getEndOfInputChar());
        if (messageEnd < 1)
        {
        }
        else
        {
            lastMessageTime = LocalDateTime.now();
            callScoper.enter();
            boolean workedOn = false;
            try
            {
                CallScopeProperties properties = IGuiceContext.get(CallScopeProperties.class);
                properties.setSource(CallScopeSource.SerialPort);
                properties.getProperties()
                          .put("ComPort", comPort);
                properties.getProperties()
                          .put("CerialPortConnection", this);
                setComPortStatus(Running);
                workedOn = true;
            }
            finally
            {
                if (messageEnd == bytes.length)
                {
                    bytes = new byte[0];
                }
                else
                {
                    if (bytes.length > 0)
                    {
                        getLogger().info(new String(bytes));
                        comPortRead.accept(bytes, me());
                    }
                }
                if (workedOn)
                {
                    callScoper.exit();
                }
            }
        }
        return bytes;
    }

    public J setEndOfMessageChar(byte b) throws UnsupportedCommOperationException
    {
        this.serialPort.getSerialPortInstance()
                       .setEndOfInputChar(b);
        return (J) this;
    }


    @JsonProperty
    protected String getComPortName()
    {
        if (OSValidator.isWindows())
        {
            return "COM" + getComPort();
        }
        else
        {
            return "/dev/ttyUSB" + getComPort() + " " + getBaudRate().toInt();
        }
    }

    @SneakyThrows
    public void write(String message, boolean... checkForEndOfCharacter)
    {
        synchronized ($lock)
        {
            if (getSerialPort() != null && getSerialPort().isConnected())
            {
                if ((checkForEndOfCharacter != null && checkForEndOfCharacter.length == 0) ||
                        (checkForEndOfCharacter != null && checkForEndOfCharacter[0]))
                {
                    try
                    {
                        if (!message.endsWith("" + getSerialPort().getSerialPortInstance()
                                                                  .getEndOfInputChar()))
                        {
                            message += (char) getSerialPort().getSerialPortInstance()
                                                             .getEndOfInputChar();
                        }
                    }
                    catch (Exception e)
                    {
                        throw new SerialPortException("Unable to fix message and add end of character - " + message, e);
                    }
                }

                if (!message.endsWith("" + (char) serialPort.getSerialPortInstance()
                                                            .getEndOfInputChar()))
                {
                    getLogger().warn("Message being sent on stream without an end character - {}", message);
                }
                if (serialPort.isConnected() && !message.trim()
                                                        .isEmpty())
                {
                    if (writer != null)
                    {
                        try
                        {
                            getLogger().info(message.trim());
                            writer.write(message.getBytes());
                        }
                        catch (Exception e)
                        {
                            throw new SerialPortException("Could not write to serial port " + getComPortName() + " - " + e.getMessage(), e);
                        }
                    }
                }
            }
            else
            {
                getLogger().warn("Com Port not ready for write - COM{} - {}", comPort, message);
            }
        }
    }

    @Override
    public void onDestroy()
    {
        if (serialPort != null && serialPort.isConnected())
        {
            serialPort.disconnect();
        }
    }

    @SuppressWarnings("unused")
    private static class OSValidator
    {
        private static final String OS = System.getProperty("os.name")
                                               .toLowerCase();

        public static boolean isWindows()
        {
            return OS.contains("win");
        }

        public static boolean isMac()
        {
            return OS.contains("mac");
        }

        public static boolean isUnix()
        {
            return (OS.contains("nix") || OS.contains("nux") || OS.contains("aix"));
        }

        public static boolean isSolaris()
        {
            return OS.contains("sunos");
        }
    }

    public J setComPort(Integer comPort)
    {
        this.comPort = comPort;
        return (J) this;
    }

    public J setBaudRate(BaudRate baudRate)
    {
        this.baudRate = baudRate;
        return (J) this;
    }

    public J setComPortStatus(ComPortStatus comPortStatus)
    {
        if (this.comPortStatus != comPortStatus && this.comPortStatusUpdate != null)
        {
            this.comPortStatusUpdate.accept(this, comPortStatus);
        }
        this.comPortStatus = comPortStatus;
        return (J) this;
    }

    public J setComPortType(ComPortType comPortType)
    {
        this.comPortType = comPortType;
        return (J) this;
    }

    public J setDataBits(DataBits dataBits)
    {
        this.dataBits = dataBits;
        return (J) this;
    }

    public J setFlowControl(FlowControl flowControl)
    {
        this.flowControl = flowControl;
        return (J) this;
    }

    public J setParity(Parity parity)
    {
        this.parity = parity;
        return (J) this;
    }

    public J setStopBits(StopBits stopBits)
    {
        this.stopBits = stopBits;
        return (J) this;
    }

    public J setBufferSize(Integer bufferSize)
    {
        this.bufferSize = bufferSize;
        return (J) this;
    }

    public J setWriter(OutputStream writer)
    {
        this.writer = writer;
        return (J) this;
    }

    public J setComPortStatusUpdate(BiConsumer<CerialPortConnection<?>, ComPortStatus> comPortStatusUpdate)
    {
        this.comPortStatusUpdate = comPortStatusUpdate;
        return (J) this;
    }

    public J setComPortRead(BiConsumer<byte[], CerialPortConnection<?>> comPortRead)
    {
        this.comPortRead = comPortRead;
        return (J) this;
    }

    public J setComPortError(TriConsumer<Throwable, CerialPortConnection<?>, ComPortStatus> comPortError)
    {
        this.comPortError = comPortError;
        return (J) this;
    }

    public J setMonitor(CerialIdleMonitor monitor)
    {
        this.monitor = monitor;
        return (J) this;
    }

    public J setLastMessageTime(LocalDateTime lastMessageTime)
    {
        this.lastMessageTime = lastMessageTime;
        return (J) this;
    }
}
