package com.binpacker.lib.solver.parallelsolvers;

import java.util.ArrayList;
import java.util.List;

import org.jocl.*;
import static org.jocl.CL.*;

import com.binpacker.lib.common.Bin;
import com.binpacker.lib.common.Box;
import com.binpacker.lib.ocl.KernelUtils;
import com.binpacker.lib.solver.common.SolverProperties;
import com.binpacker.lib.solver.common.ocl.OCLCommon;
import com.binpacker.lib.ocl.OpenCLDevice;

public class GPUSolver implements ParallelSolverInterface {

	private OCLCommon ocl = new OCLCommon();
	private cl_kernel kernel;
	private String kernelSource;
	private Bin binTemplate;

	private final String kernelFileName;
	private final String kernelFunctionName;
	private final String displayName;
	private final ReferenceSolver referenceSolver;

	public GPUSolver(String kernelFileName, String kernelFunctionName, String displayName,
			ReferenceSolver referenceSolver) {
		this.kernelFileName = kernelFileName;
		this.kernelFunctionName = kernelFunctionName;
		this.displayName = displayName;
		this.referenceSolver = referenceSolver;
	}

	public String getDisplayName() {
		return displayName;
	}

	public ReferenceSolver getReferenceSolver() {
		return referenceSolver;
	}

	public boolean isTemplate() {
		return kernelFileName.endsWith(".template");
	}

	public boolean isCompiled() {
		return kernel != null;
	}

	public void compileKernel(int maxBins, int maxSpaces) {
		if (kernel != null)
			return; // Already compiled

		String source = kernelSource
				.replace("{{MAX_BINS}}", String.valueOf(maxBins))
				.replace("{{MAX_SPACES_PER_BIN}}", String.valueOf(maxSpaces));

		// Initialize OpenCL
		ocl.init(source, devicePreference);
		kernel = clCreateKernel(ocl.clProgram, kernelFunctionName, null);
	}

	@Override
	public void init(SolverProperties properties) {
		this.binTemplate = properties.bin;
		this.devicePreference = properties.openCLDevice; // Store for later
		this.kernelSource = KernelUtils.loadKernelSource(kernelFileName);

		if (!isTemplate()) {
			// Initialize OpenCL immediately if not a template
			ocl.init(kernelSource, properties.openCLDevice);
			kernel = clCreateKernel(ocl.clProgram, kernelFunctionName, null);
		}
	}

	// Store device preference
	private com.binpacker.lib.ocl.OpenCLDevice devicePreference;

	@Override
	public List<Double> solve(List<Box> boxes, List<List<Integer>> orders) {
		int numBoxes = boxes.size();
		int numOrders = orders.size();

		if (numBoxes == 0 || numOrders == 0) {
			return new ArrayList<>();
		}

		// 1. Prepare data
		float[] boxData = new float[numBoxes * 4];
		for (int i = 0; i < numBoxes; i++) {
			Box b = boxes.get(i);
			boxData[i * 4 + 0] = b.size.x;
			boxData[i * 4 + 1] = b.size.y;
			boxData[i * 4 + 2] = b.size.z;
			boxData[i * 4 + 3] = b.weight;
		}

		int[] orderData = new int[numOrders * numBoxes];
		for (int i = 0; i < numOrders; i++) {
			List<Integer> order = orders.get(i);
			for (int j = 0; j < numBoxes; j++) {
				orderData[i * numBoxes + j] = order.get(j);
			}
		}

		// 2. Allocate buffers

		cl_mem boxesMem = clCreateBuffer(ocl.clContext,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_float * boxData.length, Pointer.to(boxData), null);

		cl_mem ordersMem = clCreateBuffer(ocl.clContext,
				CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
				Sizeof.cl_int * orderData.length, Pointer.to(orderData), null);

		cl_mem scoresMem = clCreateBuffer(ocl.clContext,
				CL_MEM_WRITE_ONLY,
				Sizeof.cl_float * numOrders, null, null);

		// 3. Set kernel args
		int a = 0;
		clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(boxesMem));
		clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(ordersMem));
		clSetKernelArg(kernel, a++, Sizeof.cl_mem, Pointer.to(scoresMem));
		clSetKernelArg(kernel, a++, Sizeof.cl_int, Pointer.to(new int[] { numBoxes }));
		clSetKernelArg(kernel, a++, Sizeof.cl_float, Pointer.to(new float[] { binTemplate.w }));
		clSetKernelArg(kernel, a++, Sizeof.cl_float, Pointer.to(new float[] { binTemplate.h }));
		clSetKernelArg(kernel, a++, Sizeof.cl_float, Pointer.to(new float[] { binTemplate.d }));
		clSetKernelArg(kernel, a++, Sizeof.cl_float, Pointer.to(new float[] { binTemplate.maxWeight }));

		// 4. Run kernel
		long[] globalWorkSize = new long[] { numOrders };
		Integer ok = clEnqueueNDRangeKernel(ocl.clQueue, kernel, 1, null, globalWorkSize,
				null, 0, null, null);
		if (ok != CL_SUCCESS) {
			System.err.println("Failed to run kernel");
			System.exit(1);
		}

		// 5. Read results
		float[] scores = new float[numOrders];
		clEnqueueReadBuffer(ocl.clQueue, scoresMem, CL_TRUE, 0,
				Sizeof.cl_float * numOrders, Pointer.to(scores), 0, null, null);

		// 6. Convert results
		List<Double> resultList = new ArrayList<>(numOrders);
		for (float score : scores) {
			resultList.add((double) score);
		}

		// 7. Cleanup memory
		clReleaseMemObject(boxesMem);
		clReleaseMemObject(ordersMem);
		clReleaseMemObject(scoresMem);

		return resultList;
	}

	@Override
	public void release() {
		if (kernel != null) {
			clReleaseKernel(kernel);
		}
		if (ocl != null) {
			ocl.release();
		}
	}
}
