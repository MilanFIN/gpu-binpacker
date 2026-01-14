package com.binpacker.lib.solver.common;

import java.util.List;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.ocl.OpenCLDevice;

public class SolverProperties {
	public Bin bin;
	public boolean growingBin;
	public String growAxis;
	public List<Integer> rotationAxes;

	public OpenCLDevice openCLDevice;
	public float weight;

	public SolverProperties(Bin bin, boolean growingBin, String growAxis, List<Integer> rotationAxes) {
		this(bin, growingBin, growAxis, rotationAxes, null, 0.0f);
	}

	public SolverProperties(Bin bin, boolean growingBin, String growAxis, List<Integer> rotationAxes,
			OpenCLDevice openCLDevice) {
		this(bin, growingBin, growAxis, rotationAxes, openCLDevice, 0.0f);
	}

	public SolverProperties(Bin bin, boolean growingBin, String growAxis, List<Integer> rotationAxes,
			OpenCLDevice openCLDevice, float weight) {
		this.bin = bin;
		this.growingBin = growingBin;
		this.growAxis = growAxis;
		this.rotationAxes = rotationAxes;
		this.openCLDevice = openCLDevice;
		this.weight = weight;
	}

}
