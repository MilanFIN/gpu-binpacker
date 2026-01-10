package com.binpacker.lib.solver.common.ocl;

import org.jocl.*;
import static org.jocl.CL.*;

public class GPUBinState {
	public cl_mem inputBuffer;
	public cl_mem outputBuffer;
	public int capacity = 1000; // Initial capacity
	public int count = 0;

	private OCLCommon ocl;
	private int inputFloatsPerSpace;
	private int outputFloatsPerSpace;

	public GPUBinState(OCLCommon ocl, int inputFloatsPerSpace, int outputFloatsPerSpace) {
		this.ocl = ocl;
		this.inputFloatsPerSpace = inputFloatsPerSpace;
		this.outputFloatsPerSpace = outputFloatsPerSpace;
		allocateBuffers(capacity);
	}

	public void allocateBuffers(int newCapacity) {
		if (inputBuffer != null)
			clReleaseMemObject(inputBuffer);
		if (outputBuffer != null)
			clReleaseMemObject(outputBuffer);

		this.capacity = newCapacity;

		inputBuffer = ocl.createBuffer(CL_MEM_READ_ONLY, (long) Sizeof.cl_float * capacity * inputFloatsPerSpace);
		outputBuffer = ocl.createBuffer(CL_MEM_READ_WRITE, (long) Sizeof.cl_float * capacity * outputFloatsPerSpace);
	}

	public void release() {
		if (inputBuffer != null)
			clReleaseMemObject(inputBuffer);
		if (outputBuffer != null)
			clReleaseMemObject(outputBuffer);
	}
}
