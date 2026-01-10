package com.binpacker.lib.ocl;

public class OpenCLDevice {
	public final int platformIndex;
	public final int deviceIndex;
	public final String name;

	public OpenCLDevice(int platformIndex, int deviceIndex, String name) {
		this.platformIndex = platformIndex;
		this.deviceIndex = deviceIndex;
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}

}
