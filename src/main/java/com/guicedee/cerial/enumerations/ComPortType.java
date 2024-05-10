package com.guicedee.cerial.enumerations;

import java.util.EnumSet;

public enum ComPortType
{
    Scanner,
    Device,
    Server,
    PrinterPPLA,
    PrinterPPLB,
    PrinterPPLZ,
    ScannerSim,
    DeviceSim;
    
    public static final EnumSet<ComPortType> deviceServerSim = EnumSet.of(ComPortType.Server,ComPortType.Device,DeviceSim);
    public static final EnumSet<ComPortType> deviceServer = EnumSet.of(ComPortType.Server,ComPortType.Device);
    public static final EnumSet<ComPortType> deviceSim = EnumSet.of(DeviceSim);
    
    public static final EnumSet<ComPortType> scanners = EnumSet.of(Scanner);
    public static final EnumSet<ComPortType> scannerSim = EnumSet.of(ScannerSim);
    public static final EnumSet<ComPortType> scannersAndSim = EnumSet.of(Scanner,ScannerSim);
    
    public static final EnumSet<ComPortType> printers = EnumSet.of(PrinterPPLA,PrinterPPLB,PrinterPPLZ);
}
