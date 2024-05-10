package com.guicedee.cerial.enumerations;

import lombok.Getter;

import java.util.EnumSet;

@Getter
public enum ComPortStatus
{
	GeneralException("exclamation-circle", "bg-danger", "text-danger"),
	Missing("engine-warning", "bg-danger", "text-danger"),
	InUse("wifi-slash", "bg-danger", "text-danger"),
	Offline("map-marker-alt-slash", "bg-secondary", "text-secondary"),
	OperationInProgress("spinner", "bg-purple", "text-purple"),
	FileTransfer("exchange-alt", "bg-purple", "text-purple"),
	Simulation("galaxy", "bg-purple", "text-purple"),
	Opening("outlet", "bg-info", "text-info"),
	Silent("lightbulb-exclamation", "bg-warning", "text-warning"),
	Idle("lightbulb", "bg-warning", "text-warning"),
	Logging("wind-turbine", "bg-info", "text-info"),
	Running("lightbulb-on", "bg-success", "text-success");
	
	private String icon;
	
	private String backgroundClass;
	
	private String foregroundClass;
	
	public static final EnumSet<ComPortStatus> pauseOperations = EnumSet.of(GeneralException,Missing,InUse,Offline,OperationInProgress,FileTransfer,Opening) ;
	public static final EnumSet<ComPortStatus> portActive = EnumSet.of(Silent,Idle,Logging,Running,Simulation);
	public static final EnumSet<ComPortStatus> portOffline = EnumSet.of(GeneralException,Missing,InUse,Offline);

	public static final EnumSet<ComPortStatus> onlineServerStatus = EnumSet.of(Simulation, Idle, Opening, Logging, OperationInProgress, Running, Silent, FileTransfer);

	
	ComPortStatus(String icon, String backgroundClass, String foregroundClass)
	{
		this.icon = icon;
		this.backgroundClass = backgroundClass;
		this.foregroundClass = foregroundClass;
	}

    public ComPortStatus setIcon(String icon)
	{
		this.icon = icon;
		return this;
	}

    public ComPortStatus setBackgroundClass(String backgroundClass)
	{
		this.backgroundClass = backgroundClass;
		return this;
	}

    public ComPortStatus setForegroundClass(String foregroundClass)
	{
		this.foregroundClass = foregroundClass;
		return this;
	}
}
