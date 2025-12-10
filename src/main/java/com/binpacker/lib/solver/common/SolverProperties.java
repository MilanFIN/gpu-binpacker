package com.binpacker.lib.solver.common;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.ocl.OpenCLDevice;

public class SolverProperties {
	public Bin bin;
	public boolean growingBin;
	public String growAxis;

	public OpenCLDevice openCLDevice;

	public SolverProperties(Bin bin, boolean growingBin, String growAxis) {
		this(bin, growingBin, growAxis, null);
	}

	public SolverProperties(Bin bin, boolean growingBin, String growAxis, OpenCLDevice openCLDevice) {
		this.bin = bin;
		this.growingBin = growingBin;
		this.growAxis = growAxis;
		this.openCLDevice = openCLDevice;
	}

}
